package com.victorious.victordrax.privilege

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.victorious.victordrax.data.SettingsRepo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PrivilegeSettingsTest {
    @Test fun defaultIsAuto() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepo(ctx)
        assertEquals(PrivilegeMode.AUTO, repo.current().privilegeMode)
    }

    @Test fun persistsPrivilegeMode() = runBlocking {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val repo = SettingsRepo(ctx)
        repo.setPrivilegeMode(PrivilegeMode.SHIZUKU)
        assertEquals(PrivilegeMode.SHIZUKU, repo.current().privilegeMode)
        repo.setPrivilegeMode(PrivilegeMode.ROOT)
        assertEquals(PrivilegeMode.ROOT, repo.current().privilegeMode)
        repo.setPrivilegeMode(PrivilegeMode.NONE)
        assertEquals(PrivilegeMode.NONE, repo.current().privilegeMode)
        repo.setPrivilegeMode(PrivilegeMode.AUTO)
        assertEquals(PrivilegeMode.AUTO, repo.current().privilegeMode)
    }
}
