package com.example.shizuku

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.IBinder
import com.example.model.ShizukuStatus
import java.lang.reflect.Method
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

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
     * Checks whether READ_CLIPBOARD is currently allowed (MODE_ALLOWED) for the given package.
     * Uses Shizuku-wrapped IAppOpsService.checkOperation(op, uid, packageName) when Ready,
     * with a safe fallback to local AppOpsManager so the app list never crashes.
     */
    fun isClipboardAllowed(uid: Int, packageName: String): Boolean {
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
                    return mode == AppOpsManager.MODE_ALLOWED
                }
            } catch (_: Throwable) {
                invalidateServiceCache()
                refreshStatus()
            }
        }

        return checkLocalFallback(uid, packageName)
    }

    private fun checkLocalFallback(uid: Int, packageName: String): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return true
            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                appOps.unsafeCheckOpNoThrow(OP_READ_CLIPBOARD_STR, uid, packageName)
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(OP_READ_CLIPBOARD_STR, uid, packageName)
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (_: Throwable) {
            true
        }
    }

    /**
     * Sets READ_CLIPBOARD mode via Shizuku-wrapped IAppOpsService.setMode(op, uid, packageName, mode).
     * Modes: MODE_ALLOWED = allow, MODE_IGNORED = block.
     */
    fun setClipboardAllowed(uid: Int, packageName: String, allow: Boolean): Result<Unit> {
        val currentStatus = refreshStatus()
        if (!currentStatus.isReady) {
            return Result.failure(
                IllegalStateException("Shizuku is not ready (${currentStatus::class.simpleName})")
            )
        }

        val targetMode = if (allow) AppOpsManager.MODE_ALLOWED else AppOpsManager.MODE_IGNORED

        return try {
            val service = getOrCreateAppOpsService()
            val setMode = cachedSetModeMethod
                ?: service.javaClass.getMethod(
                    "setMode",
                    Int::class.javaPrimitiveType,
                    Int::class.javaPrimitiveType,
                    String::class.java,
                    Int::class.javaPrimitiveType
                )
            setMode.invoke(service, readClipboardOp, uid, packageName, targetMode)

            // Verify the operation took effect via checkOperation
            val checkMethod = cachedCheckOperationMethod
            if (checkMethod != null) {
                val updatedMode =
                    checkMethod.invoke(service, readClipboardOp, uid, packageName) as? Int
                if (updatedMode != null && updatedMode != targetMode) {
                    // On some MIUI/HyperOS builds, UID mode overrides package mode
                    cachedSetUidModeMethod?.invoke(service, readClipboardOp, uid, targetMode)
                }
            }
            Result.success(Unit)
        } catch (t: Throwable) {
            invalidateServiceCache()
            refreshStatus()
            val rootCause = t.cause ?: t
            Result.failure(rootCause)
        }
    }
}
