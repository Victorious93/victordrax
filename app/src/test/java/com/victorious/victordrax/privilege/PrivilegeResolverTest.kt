package com.victorious.victordrax.privilege

import org.junit.Assert.*
import org.junit.Test

class PrivilegeResolverTest {
    @Test fun autoPrefersRoot() {
        assertEquals(PrivilegeBackend.ROOT, PrivilegeResolver.resolve(PrivilegeMode.AUTO, suPresent = true, shizukuGranted = true))
        assertEquals(PrivilegeBackend.ROOT, PrivilegeResolver.resolve(PrivilegeMode.AUTO, suPresent = true, shizukuGranted = false))
    }

    @Test fun autoFallsBackToShizuku() {
        assertEquals(PrivilegeBackend.SHIZUKU, PrivilegeResolver.resolve(PrivilegeMode.AUTO, suPresent = false, shizukuGranted = true))
    }

    @Test fun autoNoneWhenNothing() {
        assertEquals(PrivilegeBackend.NONE, PrivilegeResolver.resolve(PrivilegeMode.AUTO, suPresent = false, shizukuGranted = false))
    }

    @Test fun forcedRootIgnoresShizuku() {
        assertEquals(PrivilegeBackend.NONE, PrivilegeResolver.resolve(PrivilegeMode.ROOT, suPresent = false, shizukuGranted = true))
        assertEquals(PrivilegeBackend.ROOT, PrivilegeResolver.resolve(PrivilegeMode.ROOT, suPresent = true, shizukuGranted = false))
    }

    @Test fun forcedShizukuIgnoresRoot() {
        assertEquals(PrivilegeBackend.NONE, PrivilegeResolver.resolve(PrivilegeMode.SHIZUKU, suPresent = true, shizukuGranted = false))
        assertEquals(PrivilegeBackend.SHIZUKU, PrivilegeResolver.resolve(PrivilegeMode.SHIZUKU, suPresent = false, shizukuGranted = true))
    }

    @Test fun noneAlwaysNone() {
        assertEquals(PrivilegeBackend.NONE, PrivilegeResolver.resolve(PrivilegeMode.NONE, suPresent = true, shizukuGranted = true))
    }

    @Test fun canChrootNeedsUidZeroForShizuku() {
        assertTrue(PrivilegeResolver.canChroot(PrivilegeBackend.ROOT, null))
        assertTrue(PrivilegeResolver.canChroot(PrivilegeBackend.SHIZUKU, 0))
        assertFalse(PrivilegeResolver.canChroot(PrivilegeBackend.SHIZUKU, 2000))
        assertFalse(PrivilegeResolver.canChroot(PrivilegeBackend.SHIZUKU, null))
        assertFalse(PrivilegeResolver.canChroot(PrivilegeBackend.NONE, 0))
    }

    @Test fun labelsPresent() {
        PrivilegeMode.entries.forEach {
            assertTrue(it.label.isNotBlank())
            assertTrue(it.description.isNotBlank())
        }
    }
}
