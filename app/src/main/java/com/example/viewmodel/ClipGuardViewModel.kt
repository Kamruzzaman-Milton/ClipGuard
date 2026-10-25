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
import com.example.model.BatchConfirmation
import com.example.model.BatchProgressState
import com.example.model.ClipGuardUiState
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
                    refreshAppOpsOnly()
                }
            }
        }
        loadAllApps()
    }

    fun onResumeCheck() {
        val prev = _uiState.value.shizukuStatus
        val current = shizukuManager.refreshStatus()
        if (prev != current && current is ShizukuStatus.Ready) {
            refreshAppOpsOnly()
        }
    }

    fun loadAllApps() {
        loadPackagesJob?.cancel()
        loadPackagesJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoadingApps = true) }
            shizukuManager.refreshStatus()

            val loadedApps = withContext(Dispatchers.IO) {
                val pm = getApplication<Application>().packageManager
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

                        val isAllowed = shizukuManager.isClipboardAllowed(appInfo.uid, pkg)

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
                            isClipboardAllowed = isAllowed,
                            icon = iconBitmap
                        )
                    }
                    .sortedWith(
                        compareBy<AppItem, String>(String.CASE_INSENSITIVE_ORDER) { it.appName }
                            .thenBy { it.packageName }
                    )
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

    private fun refreshAppOpsOnly() {
        val currentList = _uiState.value.allApps
        if (currentList.isEmpty()) {
            loadAllApps()
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val updated = currentList.map { app ->
                val allowed = shizukuManager.isClipboardAllowed(app.uid, app.packageName)
                if (allowed != app.isClipboardAllowed) {
                    app.copy(isClipboardAllowed = allowed)
                } else {
                    app
                }
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
            ShizukuStatus.Ready -> refreshAppOpsOnly()
        }
    }

    /**
     * Optimistically toggles READ_CLIPBOARD for a single app and rolls back with a toast on failure.
     */
    fun toggleAppClipboard(app: AppItem, newAllowed: Boolean) {
        val previousAllowed = app.isClipboardAllowed
        if (previousAllowed == newAllowed) return

        // Optimistic UI update
        _uiState.update { state ->
            state.copy(
                allApps = state.allApps.map { item ->
                    if (item.packageName == app.packageName && item.uid == app.uid) {
                        item.copy(isClipboardAllowed = newAllowed, isToggling = true)
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
                _uiState.update { state ->
                    state.copy(
                        allApps = state.allApps.map { item ->
                            if (item.packageName == app.packageName && item.uid == app.uid) {
                                item.copy(isClipboardAllowed = newAllowed, isToggling = false)
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
                                item.copy(isClipboardAllowed = previousAllowed, isToggling = false)
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
            val succeededPackages = HashMap<String, Boolean>(targets.size)

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
                        succeededPackages[target.packageName] = allow
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
                            app.copy(isClipboardAllowed = newState, isToggling = false)
                        } else {
                            app
                        }
                    }
                )
            }

            val actionWord = if (allow) "Allowed" else "Blocked"
            val summary = if (failureCount == 0) {
                "$actionWord clipboard for $successCount apps"
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
