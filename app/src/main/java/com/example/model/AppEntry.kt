package com.example.model

import androidx.compose.ui.graphics.ImageBitmap

enum class AppFilter(val labelResName: String) {
    ALL("All"),
    USER("User"),
    SYSTEM("System"),
    BLOCKED("Blocked")
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
    val isClipboardAllowed: Boolean,
    val icon: ImageBitmap? = null,
    val isToggling: Boolean = false
)

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
    val batchConfirmation: BatchConfirmation? = null,
    val batchProgress: BatchProgressState? = null
) {
    val filteredApps: List<AppItem>
        get() {
            val query = searchQuery.trim().lowercase()
            return allApps.asSequence()
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
                .toList()
        }

    val totalCount: Int get() = allApps.size
    val userCount: Int get() = allApps.count { !it.isSystemApp }
    val systemCount: Int get() = allApps.count { it.isSystemApp }
    val blockedCount: Int get() = allApps.count { !it.isClipboardAllowed }
}
