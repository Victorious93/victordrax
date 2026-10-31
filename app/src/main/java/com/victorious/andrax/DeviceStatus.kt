package com.victorious.andrax

import android.os.Build

object DeviceStatus {
    fun arch(): String = Build.SUPPORTED_ABIS.joinToString()
}
