package com.victorious.victordrax.shizuku

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/** Observable Shizuku readiness for UI and privilege routing. */
enum class ShizukuStatus {
    NOT_INSTALLED,
    NOT_RUNNING,
    PRE_V11,
    DENIED,
    GRANTED,
}

/**
 * Thin wrapper around the Shizuku binder / permission APIs.
 * Safe to call when Shizuku is absent — all public methods catch [IllegalStateException].
 */
object ShizukuGate {
    const val PACKAGE = "moe.shizuku.privileged.api"
    const val DOWNLOAD_URL = "https://shizuku.rikka.app/download/"
    const val REQUEST_CODE = 0x5644 // "VD"

    private val listeners = CopyOnWriteArrayList<(ShizukuStatus) -> Unit>()
    private val binderAlive = AtomicBoolean(false)
    private val main = Handler(Looper.getMainLooper())

    private val onBinderReceived = Shizuku.OnBinderReceivedListener {
        binderAlive.set(true)
        notifyListeners()
    }
    private val onBinderDead = Shizuku.OnBinderDeadListener {
        binderAlive.set(false)
        notifyListeners()
    }
    private val onPermResult = Shizuku.OnRequestPermissionResultListener { _, _ -> notifyListeners() }

    @Volatile private var hooked = false

    /** Idempotent: attach binder / permission listeners once. */
    fun ensureHooked() {
        if (hooked) return
        synchronized(this) {
            if (hooked) return
            Shizuku.addBinderReceivedListenerSticky(onBinderReceived)
            Shizuku.addBinderDeadListener(onBinderDead)
            Shizuku.addRequestPermissionResultListener(onPermResult)
            binderAlive.set(Shizuku.pingBinder())
            hooked = true
        }
    }

    fun addStatusListener(l: (ShizukuStatus) -> Unit) {
        ensureHooked()
        listeners.add(l)
        l(status())
    }

    fun removeStatusListener(l: (ShizukuStatus) -> Unit) {
        listeners.remove(l)
    }

    private fun notifyListeners() {
        val s = status()
        main.post { listeners.forEach { it(s) } }
    }

    fun isInstalled(ctx: Context): Boolean =
        runCatching { ctx.packageManager.getPackageInfo(PACKAGE, 0); true }.getOrDefault(false)

    fun pingBinder(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun status(ctx: Context? = null): ShizukuStatus {
        ensureHooked()
        if (ctx != null && !isInstalled(ctx) && !pingBinder()) return ShizukuStatus.NOT_INSTALLED
        if (!pingBinder()) {
            return if (ctx != null && !isInstalled(ctx)) ShizukuStatus.NOT_INSTALLED
            else ShizukuStatus.NOT_RUNNING
        }
        return try {
            if (Shizuku.isPreV11()) ShizukuStatus.PRE_V11
            else if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) ShizukuStatus.GRANTED
            else ShizukuStatus.DENIED
        } catch (_: Throwable) {
            ShizukuStatus.NOT_RUNNING
        }
    }

    fun isGranted(): Boolean = status() == ShizukuStatus.GRANTED

    /** uid of the Shizuku server process (0 = root/Sui, 2000 = ADB shell). -1 if unknown. */
    fun serverUid(): Int = runCatching {
        if (!pingBinder()) -1 else Shizuku.getUid()
    }.getOrDefault(-1)

    fun requestPermission(code: Int = REQUEST_CODE) {
        ensureHooked()
        runCatching {
            if (Shizuku.isPreV11()) return
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                notifyListeners(); return
            }
            Shizuku.requestPermission(code)
        }
    }

    fun openDownload(ctx: Context) {
        runCatching {
            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(DOWNLOAD_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun openManager(ctx: Context) {
        runCatching {
            ctx.packageManager.getLaunchIntentForPackage(PACKAGE)
                ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                ?.let { ctx.startActivity(it) }
                ?: openDownload(ctx)
        }
    }

    fun label(s: ShizukuStatus): String = when (s) {
        ShizukuStatus.NOT_INSTALLED -> "Not installed"
        ShizukuStatus.NOT_RUNNING -> "Installed, not running"
        ShizukuStatus.PRE_V11 -> "Unsupported (pre-v11)"
        ShizukuStatus.DENIED -> "Running, permission denied"
        ShizukuStatus.GRANTED -> "Granted"
    }

    /**
     * Suspend until the next permission result (or already granted / binder dead).
     * Used by the Settings "Request permission" button.
     */
    suspend fun awaitPermissionResult(): Boolean = suspendCancellableCoroutine { cont ->
        ensureHooked()
        if (isGranted()) { cont.resume(true); return@suspendCancellableCoroutine }
        if (!pingBinder()) { cont.resume(false); return@suspendCancellableCoroutine }
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                Shizuku.removeRequestPermissionResultListener(this)
                cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        cont.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
        Shizuku.addRequestPermissionResultListener(listener)
        requestPermission()
    }
}
