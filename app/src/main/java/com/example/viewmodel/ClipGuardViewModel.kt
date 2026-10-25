package com.example.viewmodel

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.model.AppFilter
import com.example.model.AppItem
import com.example.model.AppSortOption
import com.example.model.BatchConfirmation
import com.example.model.BatchProgressState
import com.example.model.ClipGuardUiState
import com.example.model.ClipboardPermissionState
import com.example.model.ShizukuStatus
import com.example.shizuku.ShizukuAppOpsManager
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ClipGuardViewModel(application: Application) : AndroidViewModel(application) {

    val shizukuManager = ShizukuAppOpsManager(application.applicationContext)

    private val _uiState = MutableStateFlow(
        ClipGuardUiState(shizukuStatus = shizukuManager.status.value)
    )
    val uiState: StateFlow<ClipGuardUiState> = _uiState.asStateFlow()

    private val _toastEvents = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val toastEvents: SharedFlow<String> = _toastEvents.asSharedFlow()

    private val iconCache = ConcurrentHashMap<String, ImageBitmap>()
    private var loadPackagesJob: Job? = null

    init {
        viewModelScope.launch {
            shizukuManager.status.collect { newStatus ->
                val oldStatus = _uiState.value.shizukuStatus
                _uiState.update { it.copy(shizukuStatus = newStatus) }
                if (oldStatus != newStatus && newStatus is ShizukuStatus.Ready) {
                    refreshAppOpsAndUsage()
                }
            }
        }
        loadAllApps()
    }

    fun onResumeCheck() {
        val prev = _uiState.value.shizukuStatus
        val current = shizukuManager.refreshStatus()
        if (prev != current && current is ShizukuStatus.Ready) {
            refreshAppOpsAndUsage()
        }
    }

    fun loadAllApps() {
        loadPackagesJob?.cancel()
        loadPackagesJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            shizukuManager.refreshStatus()

            val loadedApps = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
                val usageMap = shizukuManager.getAppUsageStatsMap()

                val rawApps = try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        pm.getInstalledApplications(
                            PackageManager.ApplicationInfoFlags.of(0L)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        pm.getInstalledApplications(0)
                    }
                } catch (_: Throwable) {
                    emptyList()
                }

                rawApps
                    .asSequence()
                    .filter { it.packageName.isNotBlank() }
                    .map { appInfo ->
                        val pkg = appInfo.packageName
                        val label = runCatching {
                            pm.getApplicationLabel(appInfo).toString().ifBlank { pkg }
                        }.getOrDefault(pkg)

                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 &&
                            (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0

                        val permState = shizukuManager.getClipboardState(appInfo.uid, pkg)

                        val pkgInfo = runCatching {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
                            } else {
                                @Suppress("DEPRECATION")
                                pm.getPackageInfo(pkg, 0)
                            }
                        }.getOrNull()

                        val firstInstall = pkgInfo?.firstInstallTime ?: 0L
                        val lastUpdate = pkgInfo?.lastUpdateTime ?: 0L
                        val usage = usageMap[pkg]
                        val totalUsageTime = usage?.totalForegroundTimeMs ?: 0L
                        val lastUsed = usage?.lastTimeUsedMs ?: 0L

                        val iconBitmap = iconCache[pkg] ?: runCatching {
                            val drawable = pm.getApplicationIcon(appInfo)
                            drawable.toBitmap(width = 96, height = 96).asImageBitmap().also {
                                iconCache[pkg] = it
                            }
                        }.getOrNull()

                        AppItem(
                            packageName = pkg,
                            appName = label,
                            uid = appInfo.uid,
                            isSystemApp = isSystem,
                            permissionState = permState,
                            icon = iconBitmap,
                            firstInstallTime = firstInstall,
                            lastUpdateTime = lastUpdate,
                            totalForegroundTimeMs = totalUsageTime,
                            lastTimeUsedMs = lastUsed
                        )
                    }
                    .toList()
            }

            _uiState.update {
                it.copy(
                    isLoadingApps = false,
                    allApps = loadedApps
                )
            }
        }
    }

    private fun refreshAppOpsAndUsage() {
        val currentList = _uiState.value.allApps
        if (currentList.isEmpty()) {
            loadAllApps()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val usageMap = shizukuManager.getAppUsageStatsMap()
            val updated = currentList.map { app ->
                val permState = shizukuManager.getClipboardState(app.uid, app.packageName)
                val usage = usageMap[app.packageName]
                app.copy(
                    permissionState = permState,
                    totalForegroundTimeMs = usage?.totalForegroundTimeMs ?: app.totalForegroundTimeMs,
                    lastTimeUsedMs = usage?.lastTimeUsedMs ?: app.lastTimeUsedMs
                )
            }
            _uiState.update { it.copy(allApps = updated) }
        }
    }

    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    fun selectFilter(filter: AppFilter) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    fun selectSort(sort: AppSortOption) {
        _uiState.update { it.copy(selectedSort = sort) }
        if (sort == AppSortOption.MOST_USED || sort == AppSortOption.RECENTLY_USED) {
            viewModelScope.launch {
                withContext(Dispatchers.IO) {
                    if (shizukuManager.ensureUsageStatsPermissionGranted()) {
                        refreshAppOpsAndUsage()
                    }
                }
            }
        }
    }

    fun handleShizukuBannerAction() {
        when (shizukuManager.refreshStatus()) {
            ShizukuStatus.NotInstalled -> shizukuManager.openShizukuDownloadPage()
            ShizukuStatus.NotRunning -> {
                if (!shizukuManager.openShizukuApp()) {
                    _toastEvents.tryEmit("Please open the Shizuku app and start the service")
                }
            }
            ShizukuStatus.PermissionNotGranted,
            ShizukuStatus.PermissionDenied -> {
                shizukuManager.requestShizukuPermission()
            }
            ShizukuStatus.Ready -> refreshAppOpsAndUsage()
        }
    }

    /**
     * Toggles READ_CLIPBOARD for a single app:
     * - ON: transitions from "Decide for me" / "Deny" to "While using the app" (MODE_FOREGROUND)
     * - OFF: transitions to "Deny" (MODE_IGNORED)
     */
    fun toggleAppClipboard(app: AppItem, newAllowed: Boolean) {
        val previousState = app.permissionState
        if (previousState.isAllowed == newAllowed) return

        val targetState = if (newAllowed) {
            ClipboardPermissionState.FOREGROUND
        } else {
            ClipboardPermissionState.DENIED
        }

        // Optimistic UI update
        _uiState.update { state ->
            state.copy(
                allApps = state.allApps.map { item ->
                    if (item.packageName == app.packageName && item.uid == app.uid) {
                        item.copy(permissionState = targetState, isToggling = true)
                    } else {
                        item
                    }
                }
            )
        }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                shizukuManager.setClipboardAllowed(
                    uid = app.uid,
                    packageName = app.packageName,
                    allow = newAllowed
                )
            }

            if (result.isSuccess) {
                // Re-fetch actual state to confirm
                val actualState = withContext(Dispatchers.IO) {
                    shizukuManager.getClipboardState(app.uid, app.packageName)
                }
                _uiState.update { state ->
                    state.copy(
                        allApps = state.allApps.map { item ->
                            if (item.packageName == app.packageName && item.uid == app.uid) {
                                item.copy(
                                    permissionState = if (actualState.isAllowed == newAllowed) actualState else targetState,
                                    isToggling = false
                                )
                            } else {
                                item
                            }
                        }
                    )
                }
            } else {
                // Rollback optimistic update
                _uiState.update { state ->
                    state.copy(
                        allApps = state.allApps.map { item ->
                            if (item.packageName == app.packageName && item.uid == app.uid) {
                                item.copy(permissionState = previousState, isToggling = false)
                            } else {
                                item
                            }
                        }
                    )
                }
                val reason = result.exceptionOrNull()?.message ?: "Shizuku command failed"
                _toastEvents.tryEmit("Failed to update ${app.appName}: $reason")
            }
        }
    }

    fun requestBatchOperation(allow: Boolean) {
        val currentStatus = shizukuManager.refreshStatus()
        if (!currentStatus.isReady) {
            _toastEvents.tryEmit("Shizuku must be running and authorized for batch actions")
            return
        }
        val targets = _uiState.value.filteredApps
        if (targets.isEmpty()) {
            _toastEvents.tryEmit("No apps in the current filtered list")
            return
        }
        _uiState.update {
            it.copy(
                batchConfirmation = BatchConfirmation(
                    allow = allow,
                    targetApps = targets
                )
            )
        }
    }

    fun dismissBatchConfirmation() {
        _uiState.update { it.copy(batchConfirmation = null) }
    }

    fun executeConfirmedBatchOperation() {
        val confirmation = _uiState.value.batchConfirmation ?: return
        val allow = confirmation.allow
        val targets = confirmation.targetApps

        _uiState.update {
            it.copy(
                batchConfirmation = null,
                batchProgress = BatchProgressState(
                    allow = allow,
                    processedCount = 0,
                    totalCount = targets.size,
                    currentPackage = targets.firstOrNull()?.appName.orEmpty()
                )
            )
        }

        viewModelScope.launch {
            var successCount = 0
            var failureCount = 0
            val targetState = if (allow) ClipboardPermissionState.FOREGROUND else ClipboardPermissionState.DENIED
            val succeededPackages = HashMap<String, ClipboardPermissionState>(targets.size)

            withContext(Dispatchers.IO) {
                targets.forEachIndexed { index, target ->
                    _uiState.update { state ->
                        state.copy(
                            batchProgress = BatchProgressState(
                                allow = allow,
                                processedCount = index,
                                totalCount = targets.size,
                                currentPackage = target.appName
                            )
                        )
                    }

                    val res = shizukuManager.setClipboardAllowed(
                        uid = target.uid,
                        packageName = target.packageName,
                        allow = allow
                    )
                    if (res.isSuccess) {
                        successCount++
                        succeededPackages[target.packageName] = targetState
                    } else {
                        failureCount++
                    }
                }
            }

            _uiState.update { state ->
                state.copy(
                    batchProgress = null,
                    allApps = state.allApps.map { app ->
                        val newState = succeededPackages[app.packageName]
                        if (newState != null) {
                            app.copy(permissionState = newState, isToggling = false)
                        } else {
                            app
                        }
                    }
                )
            }

            val actionWord = if (allow) "Enabled 'While using app' for" else "Blocked"
            val summary = if (failureCount == 0) {
                "$actionWord $successCount apps"
            } else {
                "$actionWord $successCount apps ($failureCount failed)"
            }
            _toastEvents.tryEmit(summary)
        }
    }

    override fun onCleared() {
        super.onCleared()
        shizukuManager.destroy()
    }
}
