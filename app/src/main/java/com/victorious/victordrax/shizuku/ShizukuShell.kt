package com.victorious.victordrax.shizuku

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import com.victorious.victordrax.data.ShellResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * Runs privileged shell commands through a Shizuku [ShellUserService].
 * Binding is lazy and cached; call [unbind] when the app is done.
 */
object ShizukuShell {
    private val mutex = Mutex()
    private val service = AtomicReference<IShellService?>(null)
    private val conn = AtomicReference<ServiceConnection?>(null)

    // Bump when the UserService AIDL / impl changes so Shizuku recreates it.
    private const val USER_SERVICE_VERSION = 1

    private val args = Shizuku.UserServiceArgs(
        ComponentName("com.victorious.victordrax", ShellUserService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("shell")
        .debuggable(false)
        .version(USER_SERVICE_VERSION)

    fun isReady(): Boolean =
        ShizukuGate.pingBinder() &&
            runCatching { Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }
                .getOrDefault(false)

    suspend fun exec(command: String, bindTimeoutMs: Long = 8_000L): ShellResult =
        withContext(Dispatchers.IO) {
            if (!isReady()) return@withContext ShellResult(-1, "Shizuku not granted or not running")
            val svc = mutex.withLock { ensureBound(bindTimeoutMs) }
                ?: return@withContext ShellResult(-1, "Failed to bind Shizuku UserService")
            runCatching {
                val raw = svc.exec(command) ?: return@runCatching ShellResult(-1, "null response")
                parse(raw)
            }.getOrElse { ShellResult(-1, it.message ?: "Shizuku exec failed") }
        }

    private fun parse(raw: String): ShellResult {
        val nl = raw.indexOf('\n')
        if (nl < 0) {
            val code = raw.trim().toIntOrNull() ?: -1
            return ShellResult(code, "")
        }
        val code = raw.substring(0, nl).trim().toIntOrNull() ?: -1
        return ShellResult(code, raw.substring(nl + 1))
    }

    private fun ensureBound(timeoutMs: Long): IShellService? {
        service.get()?.let { return it }
        val latch = CountDownLatch(1)
        val holder = AtomicReference<IShellService?>(null)
        val c = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                if (binder != null && binder.pingBinder()) {
                    val s = IShellService.Stub.asInterface(binder)
                    holder.set(s)
                    service.set(s)
                }
                latch.countDown()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                service.set(null)
            }
        }
        conn.set(c)
        return try {
            Shizuku.bindUserService(args, c)
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                runCatching { Shizuku.unbindUserService(args, c, true) }
                null
            } else holder.get()
        } catch (t: Throwable) {
            runCatching { Shizuku.unbindUserService(args, c, true) }
            null
        }
    }

    fun unbind() {
        val c = conn.getAndSet(null) ?: return
        service.set(null)
        runCatching { Shizuku.unbindUserService(args, c, true) }
    }
}
