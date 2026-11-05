package com.victorious.victordrax.shizuku

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Robolectric has no Shizuku binder. We only assert safe defaults and labels —
 * real grant + UserService exec need a device with Shizuku installed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ShizukuGateTest {
    @Test fun notInstalledOnRobolectric() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val st = ShizukuGate.status(ctx)
        assertTrue(
            "expected NOT_INSTALLED or NOT_RUNNING on Robolectric, got $st",
            st == ShizukuStatus.NOT_INSTALLED || st == ShizukuStatus.NOT_RUNNING,
        )
        assertFalse(ShizukuGate.isGranted())
        assertFalse(ShizukuGate.isInstalled(ctx))
    }

    @Test fun labelsCoverAllStatuses() {
        ShizukuStatus.entries.forEach {
            assertTrue(ShizukuGate.label(it).isNotBlank())
        }
    }

    @Test fun packageConstant() {
        assertEquals("moe.shizuku.privileged.api", ShizukuGate.PACKAGE)
        assertTrue(ShizukuGate.DOWNLOAD_URL.startsWith("https://"))
    }
}
