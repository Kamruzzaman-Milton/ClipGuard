package com.example.model

import androidx.compose.ui.graphics.ImageBitmap

enum class AppFilter(val labelResName: String) {
    ALL("All"),
    USER("User"),
    SYSTEM("System"),
    BLOCKED("Blocked")
}

enum class ClipboardPermissionState(val modeCode: Int, val label: String) {
    FOREGROUND(4, "While using the app"),
    ALLOWED(0, "Always allow"),
    DEFAULT(3, "Decide for me"),
    DENIED(1, "Deny");

    val isAllowed: Boolean
        get() = this == FOREGROUND || this == ALLOWED
}

enum class AppSortOption(val displayName: String, val subtitle: String) {
    NAME("Name (A–Z)", "Alphabetical order"),
    RECENTLY_UPDATED("Recently Updated", "Latest app updates first"),
    RECENTLY_INSTALLED("Recently Installed", "Latest installed apps first"),
    MOST_USED("Most Used", "Screen time in last 30 days"),
    RECENTLY_USED("Recently Used", "Most recently opened apps")
}

sealed interface ShizukuStatus {
    data object NotInstalled : ShizukuStatus
    data object NotRunning : ShizukuStatus
    data object PermissionNotGranted : ShizukuStatus
    data object PermissionDenied : ShizukuStatus
    data object Ready : ShizukuStatus

    val isReady: Boolean
        get() = this is Ready
}

data class AppItem(
    val packageName: String,
    val appName: String,
    val uid: Int,
    val isSystemApp: Boolean,
    val permissionState: ClipboardPermissionState = ClipboardPermissionState.DEFAULT,
    val icon: ImageBitmap? = null,
    val isToggling: Boolean = false,
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L,
    val totalForegroundTimeMs: Long = 0L,
    val lastTimeUsedMs: Long = 0L
) {
    val isClipboardAllowed: Boolean
        get() = permissionState.isAllowed
}

data class BatchConfirmation(
    val allow: Boolean,
    val targetApps: List<AppItem>
)

data class BatchProgressState(
    val allow: Boolean,
    val processedCount: Int,
    val totalCount: Int,
    val currentPackage: String
)

data class ClipGuardUiState(
    val shizukuStatus: ShizukuStatus = ShizukuStatus.NotRunning,
    val isLoadingApps: Boolean = true,
    val allApps: List<AppItem> = emptyList(),
    val searchQuery: String = "",
    val selectedFilter: AppFilter = AppFilter.ALL,
    val selectedSort: AppSortOption = AppSortOption.NAME,
    val batchConfirmation: BatchConfirmation? = null,
    val batchProgress: BatchProgressState? = null
) {
    val filteredApps: List<AppItem>
        get() {
            val query = searchQuery.trim().lowercase()
            val filtered = allApps.asSequence()
                .filter { app ->
                    when (selectedFilter) {
                        AppFilter.ALL -> true
                        AppFilter.USER -> !app.isSystemApp
                        AppFilter.SYSTEM -> app.isSystemApp
                        AppFilter.BLOCKED -> !app.isClipboardAllowed
                    }
                }
                .filter { app ->
                    if (query.isEmpty()) {
                        true
                    } else {
                        app.appName.lowercase().contains(query) ||
                            app.packageName.lowercase().contains(query)
                    }
                }

            val comparator = when (selectedSort) {
                AppSortOption.NAME -> compareBy<AppItem, String>(String.CASE_INSENSITIVE_ORDER) { it.appName }
                    .thenBy { it.packageName }
                AppSortOption.RECENTLY_UPDATED -> compareByDescending<AppItem> { it.lastUpdateTime }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName }
                AppSortOption.RECENTLY_INSTALLED -> compareByDescending<AppItem> { it.firstInstallTime }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName }
                AppSortOption.MOST_USED -> compareByDescending<AppItem> { it.totalForegroundTimeMs }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName }
                AppSortOption.RECENTLY_USED -> compareByDescending<AppItem> { it.lastTimeUsedMs }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName }
            }

            return filtered.sortedWith(comparator).toList()
        }

    val totalCount: Int get() = allApps.size
    val userCount: Int get() = allApps.count { !it.isSystemApp }
    val systemCount: Int get() = allApps.count { it.isSystemApp }
    val blockedCount: Int get() = allApps.count { !it.isClipboardAllowed }
}
