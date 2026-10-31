package com.victorious.andrax.dash

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import com.victorious.andrax.DeviceStatus
import com.victorious.andrax.data.Root
import java.io.File

data class Stats(
    val cpuCores: Int, val cpuLoadPct: Int?, val cpuMhz: Int?,
    val ramUsed: Long, val ramTotal: Long,
    val storageFree: Long, val storageTotal: Long,
    val batteryPct: Int, val charging: Boolean, val batteryTempC: Float,
    val network: String, val root: Boolean, val arch: String,
)

object StatsReader {
    private fun readInt(path: String) = runCatching { File(path).readText().trim().toInt() }.getOrNull()

    /** Apps can't read /proc/stat on Android 8+, so load is estimated as current/max frequency across cores. */
    fun cpu(): Triple<Int, Int?, Int?> {
        val n = Runtime.getRuntime().availableProcessors()
        val cur = (0 until n).mapNotNull { readInt("/sys/devices/system/cpu/cpu$it/cpufreq/scaling_cur_freq") }
        val max = (0 until n).mapNotNull { readInt("/sys/devices/system/cpu/cpu$it/cpufreq/cpuinfo_max_freq") }
        val mhz = cur.takeIf { it.isNotEmpty() }?.average()?.div(1000)?.toInt()
        val load = if (cur.isNotEmpty() && max.isNotEmpty()) (cur.sum() * 100L / max.sum()).toInt().coerceIn(0, 100) else null
        return Triple(n, load, mhz)
    }

    fun read(ctx: Context): Stats {
        val (cores, load, mhz) = cpu()
        val mi = ActivityManager.MemoryInfo().also { ctx.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        val st = android.os.StatFs(android.os.Environment.getDataDirectory().path)
        val bat = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = bat?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = bat?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = bat?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val temp = (bat?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm.getNetworkCapabilities(cm.activeNetwork)
        val net = when {
            caps == null -> "Offline"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Ethernet"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
            else -> "Connected"
        } + (caps?.linkDownstreamBandwidthKbps?.takeIf { it > 0 }?.let { " · ${it / 1000} Mbps" } ?: "")
        return Stats(cores, load, mhz, mi.totalMem - mi.availMem, mi.totalMem, st.availableBytes, st.totalBytes,
            if (scale > 0 && level >= 0) level * 100 / scale else -1,
            status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL,
            temp, net, Root.suBinaryPresent(), DeviceStatus.arch())
    }

    fun gb(b: Long) = String.format("%.1f GB", b / 1073741824.0)
}
