package com.example.shizuku

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import com.example.model.ClipboardPermissionState
import com.example.model.ShizukuStatus
import java.lang.reflect.Method
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

data class AppUsageInfo(
    val totalForegroundTimeMs: Long = 0L,
    val lastTimeUsedMs: Long = 0L
)

class ShizukuAppOpsManager(private val context: Context) {

    companion object {
        const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 1001
        private const val OP_READ_CLIPBOARD_STR = "android:read_clipboard"
        private const val FALLBACK_OP_READ_CLIPBOARD = 29
    }

    private val _status = MutableStateFlow<ShizukuStatus>(ShizukuStatus.NotRunning)
    val status: StateFlow<ShizukuStatus> = _status.asStateFlow()

    private var explicitPermissionDenied = false

    @Volatile
    private var cachedAppOpsService: Any? = null

    @Volatile
    private var cachedCheckOperationMethod: Method? = null

    @Volatile
    private var cachedSetModeMethod: Method? = null

    @Volatile
    private var cachedSetUidModeMethod: Method? = null

    val readClipboardOp: Int by lazy {
        resolveReadClipboardOp()
    }

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        invalidateServiceCache()
        refreshStatus()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        invalidateServiceCache()
        _status.value = evaluateStatus()
    }

    private val requestPermissionResultListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) {
                explicitPermissionDenied = (grantResult != PackageManager.PERMISSION_GRANTED)
                invalidateServiceCache()
                refreshStatus()
            }
        }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                HiddenApiBypass.addHiddenApiExemptions("")
            }
        }
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
            Shizuku.addBinderDeadListener(binderDeadListener)
            Shizuku.addRequestPermissionResultListener(requestPermissionResultListener)
        }
        refreshStatus()
    }

    fun destroy() {
        runCatching {
            Shizuku.removeBinderReceivedListener(binderReceivedListener)
            Shizuku.removeBinderDeadListener(binderDeadListener)
            Shizuku.removeRequestPermissionResultListener(requestPermissionResultListener)
        }
    }

    fun refreshStatus(): ShizukuStatus {
        val current = evaluateStatus()
        _status.value = current
        if (current is ShizukuStatus.Ready) {
            ensureUsageStatsPermissionGranted()
        }
        return current
    }

    private fun evaluateStatus(): ShizukuStatus {
        if (!isShizukuInstalled()) {
            return ShizukuStatus.NotInstalled
        }
        val binderAlive = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        if (!binderAlive) {
            return ShizukuStatus.NotRunning
        }
        return try {
            if (Shizuku.isPreV11()) {
                ShizukuStatus.PermissionNotGranted
            } else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                explicitPermissionDenied = false
                ShizukuStatus.Ready
            } else if (explicitPermissionDenied || Shizuku.shouldShowRequestPermissionRationale()) {
                ShizukuStatus.PermissionDenied
            } else {
                ShizukuStatus.PermissionNotGranted
            }
        } catch (_: Throwable) {
            ShizukuStatus.NotRunning
        }
    }

    fun isShizukuInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    SHIZUKU_PACKAGE_NAME,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(SHIZUKU_PACKAGE_NAME, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    fun requestShizukuPermission() {
        val current = refreshStatus()
        if (current is ShizukuStatus.NotInstalled || current is ShizukuStatus.NotRunning) {
            return
        }
        runCatching {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        }
    }

    fun openShizukuApp(): Boolean {
        return try {
            val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)
            if (launchIntent != null) {
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                true
            } else {
                false
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun openShizukuDownloadPage() {
        runCatching {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://shizuku.rikka.app/download/")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    private fun resolveReadClipboardOp(): Int {
        return try {
            val method = AppOpsManager::class.java.getMethod("strOpToOp", String::class.java)
            (method.invoke(null, OP_READ_CLIPBOARD_STR) as? Int) ?: FALLBACK_OP_READ_CLIPBOARD
        } catch (_: Throwable) {
            try {
                val method = AppOpsManager::class.java.getDeclaredMethod("strOpToOp", String::class.java)
                method.isAccessible = true
                (method.invoke(null, OP_READ_CLIPBOARD_STR) as? Int) ?: FALLBACK_OP_READ_CLIPBOARD
            } catch (_: Throwable) {
                FALLBACK_OP_READ_CLIPBOARD
            }
        }
    }

    @Synchronized
    private fun invalidateServiceCache() {
        cachedAppOpsService = null
        cachedCheckOperationMethod = null
        cachedSetModeMethod = null
        cachedSetUidModeMethod = null
    }

    @Synchronized
    private fun getOrCreateAppOpsService(): Any {
        cachedAppOpsService?.let { return it }

        val rawBinder: IBinder = SystemServiceHelper.getSystemService(Context.APP_OPS_SERVICE)
            ?: SystemServiceHelper.getSystemService("appops")
            ?: throw IllegalStateException("System service 'appops' binder not found")

        val wrappedBinder = ShizukuBinderWrapper(rawBinder)
        val stubClass = Class.forName("com.android.internal.app.IAppOpsService\$Stub")
        val asInterfaceMethod = stubClass.getMethod("asInterface", IBinder::class.java)
        val service = asInterfaceMethod.invoke(null, wrappedBinder)
            ?: throw IllegalStateException("Failed to wrap IAppOpsService interface")

        val serviceClass = service.javaClass
        cachedCheckOperationMethod = serviceClass.getMethod(
            "checkOperation",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java
        )
        cachedSetModeMethod = serviceClass.getMethod(
            "setMode",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            String::class.java,
            Int::class.javaPrimitiveType
        )
        cachedSetUidModeMethod = runCatching {
            serviceClass.getMethod(
                "setUidMode",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
        }.getOrNull()

        cachedAppOpsService = service
        return service
    }

    /**
     * Resolves the exact ClipboardPermissionState for the given package and UID.
     * Maps to MIUI's 4 stages:
     * - FOREGROUND (4): "While using the app"
     * - ALLOWED (0): "Always allow"
     * - DEFAULT (3): "Decide for me" (blank)
     * - DENIED (1): "Deny"
     */
    fun getClipboardState(uid: Int, packageName: String): ClipboardPermissionState {
        if (_status.value.isReady) {
            try {
                val service = getOrCreateAppOpsService()
                val checkMethod = cachedCheckOperationMethod
                    ?: service.javaClass.getMethod(
                        "checkOperation",
                        Int::class.javaPrimitiveType,
                        Int::class.javaPrimitiveType,
                        String::class.java
                    )
                val mode = checkMethod.invoke(service, readClipboardOp, uid, packageName) as? Int
                if (mode != null) {
                    return when (mode) {
                        0 -> ClipboardPermissionState.ALLOWED
                        4 -> ClipboardPermissionState.FOREGROUND
                        3 -> ClipboardPermissionState.DEFAULT
                        1, 2 -> ClipboardPermissionState.DENIED
                        else -> if (mode == 4) ClipboardPermissionState.FOREGROUND else ClipboardPermissionState.DEFAULT
                    }
                }
            } catch (_: Throwable) {
                invalidateServiceCache()
                refreshStatus()
            }
        }

        return checkLocalFallbackState(uid, packageName)
    }

    private fun checkLocalFallbackState(uid: Int, packageName: String): ClipboardPermissionState {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return ClipboardPermissionState.DEFAULT
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(OP_READ_CLIPBOARD_STR, uid, packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(OP_READ_CLIPBOARD_STR, uid, packageName)
            }
            when (mode) {
                AppOpsManager.MODE_ALLOWED -> ClipboardPermissionState.ALLOWED
                4 -> ClipboardPermissionState.FOREGROUND
                AppOpsManager.MODE_IGNORED, AppOpsManager.MODE_ERRORED -> ClipboardPermissionState.DENIED
                AppOpsManager.MODE_DEFAULT -> ClipboardPermissionState.DEFAULT
                else -> ClipboardPermissionState.DEFAULT
            }
        } catch (_: Throwable) {
            ClipboardPermissionState.DEFAULT
        }
    }

    private fun executeShizukuProcess(cmd: Array<String>): Process? {
        return runCatching {
            val method = Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            )
            method.isAccessible = true
            method.invoke(null, cmd, null, null) as? Process
        }.getOrNull()
    }

    /**
     * Sets READ_CLIPBOARD mode via Shizuku.
     * On MIUI / HyperOS, toggling ON sets permission from "Decide for me" (blank)
     * to "While using the app" (MODE_FOREGROUND = 4, shell argument 'foreground').
     * Toggling OFF sets permission to "Deny" (MODE_IGNORED = 1, shell argument 'ignore').
     */
    fun setClipboardAllowed(uid: Int, packageName: String, allow: Boolean): Result<Unit> {
        val currentStatus = refreshStatus()
        if (!currentStatus.isReady) {
            return Result.failure(
                IllegalStateException("Shizuku is not ready (${currentStatus::class.simpleName})")
            )
        }

        val modeStr = if (allow) "foreground" else "ignore"
        val targetModeInt = if (allow) 4 else AppOpsManager.MODE_IGNORED

        var cmdExecuted = false
        var lastError: Throwable? = null

        // 1. Run canonical Shizuku ADB shell command: cmd appops set <pkg> READ_CLIPBOARD <foreground|ignore>
        try {
            val proc = executeShizukuProcess(
                arrayOf("cmd", "appops", "set", packageName, "READ_CLIPBOARD", modeStr)
            )
            if (proc != null) {
                val exitCode = proc.waitFor()
                if (exitCode == 0) {
                    cmdExecuted = true
                } else {
                    val err = proc.errorStream.bufferedReader().use { it.readText() }.trim()
                    if (err.isNotBlank()) {
                        lastError = Exception(err)
                    }
                }
            }
        } catch (e: Throwable) {
            lastError = e
        }

        // Also update UID mode for consistency across multi-user/dual apps/secondary spaces
        runCatching {
            executeShizukuProcess(
                arrayOf("cmd", "appops", "set", "--uid", uid.toString(), "READ_CLIPBOARD", modeStr)
            )?.waitFor()
        }

        // 2. Also invoke IAppOpsService IPC directly
        runCatching {
            val service = getOrCreateAppOpsService()
            cachedSetModeMethod?.invoke(service, readClipboardOp, uid, packageName, targetModeInt)
            cachedSetUidModeMethod?.invoke(service, readClipboardOp, uid, targetModeInt)
        }

        if (cmdExecuted) {
            return Result.success(Unit)
        }

        if (lastError != null) {
            return Result.failure(lastError)
        }
        return Result.success(Unit)
    }

    /**
     * Checks if PACKAGE_USAGE_STATS is granted to this app.
     */
    fun hasUsageStatsPermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    android.os.Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Automatically grants PACKAGE_USAGE_STATS to ClipGuard via Shizuku ADB commands and Binder IPC.
     */
    fun ensureUsageStatsPermissionGranted(): Boolean {
        if (hasUsageStatsPermission()) return true
        if (!_status.value.isReady) return false

        val pkg = context.packageName

        // Try direct Binder IPC to AppOps service for OP_GET_USAGE_STATS (code 43)
        runCatching {
            val service = getOrCreateAppOpsService()
            cachedSetModeMethod?.invoke(
                service,
                43, // OP_GET_USAGE_STATS
                android.os.Process.myUid(),
                pkg,
                AppOpsManager.MODE_ALLOWED
            )
        }

        // Try direct Binder IPC to PackageManager for grantRuntimePermission
        runCatching {
            val raw = SystemServiceHelper.getSystemService("package") ?: return@runCatching
            val wrapped = ShizukuBinderWrapper(raw)
            val stub = Class.forName("android.content.pm.IPackageManager\$Stub")
            val asInterface = stub.getMethod("asInterface", IBinder::class.java)
            val pmService = asInterface.invoke(null, wrapped) ?: return@runCatching
            val grantMethod = pmService.javaClass.getMethod(
                "grantRuntimePermission",
                String::class.java,
                String::class.java,
                Int::class.javaPrimitiveType
            )
            grantMethod.invoke(pmService, pkg, "android.permission.PACKAGE_USAGE_STATS", 0)
        }

        // Run ADB shell commands via Shizuku process
        runCatching {
            executeShizukuProcess(
                arrayOf("cmd", "appops", "set", pkg, "GET_USAGE_STATS", "allow")
            )?.waitFor()
        }
        runCatching {
            executeShizukuProcess(
                arrayOf("pm", "grant", pkg, "android.permission.PACKAGE_USAGE_STATS")
            )?.waitFor()
        }
        return hasUsageStatsPermission()
    }

    /**
     * Retrieves usage statistics map for all packages over the past 30 days.
     */
    fun getAppUsageStatsMap(): Map<String, AppUsageInfo> {
        if (!hasUsageStatsPermission()) {
            ensureUsageStatsPermissionGranted()
        }
        if (!hasUsageStatsPermission()) {
            return emptyMap()
        }
        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return emptyMap()
            val endTime = System.currentTimeMillis()
            val startTime = endTime - 30L * 24 * 60 * 60 * 1000L // 30 days
            val statsList = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_BEST,
                startTime,
                endTime
            ) ?: emptyList()

            val map = HashMap<String, AppUsageInfo>(statsList.size)
            for (stat in statsList) {
                val existing = map[stat.packageName]
                val totalTime = (existing?.totalForegroundTimeMs ?: 0L) + stat.totalTimeInForeground
                val lastUsed = maxOf(existing?.lastTimeUsedMs ?: 0L, stat.lastTimeUsed)
                map[stat.packageName] = AppUsageInfo(
                    totalForegroundTimeMs = totalTime,
                    lastTimeUsedMs = lastUsed
                )
            }
            map
        } catch (_: Throwable) {
            emptyMap()
        }
    }
}
