package com.victorious.andrax

import com.victorious.andrax.data.Checksum
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ChecksumTest {
    private val abcHash = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

    @Test fun knownVector() = assertEquals(abcHash, Checksum.sha256("abc".byteInputStream()))
    @Test fun emptyInput() = assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", Checksum.sha256(ByteArray(0).inputStream()))
    @Test fun fileMatchesStream() {
        val f = File.createTempFile("va-test", ".bin").apply { writeText("abc"); deleteOnExit() }
        assertEquals(abcHash, Checksum.sha256(f))
    }
    @Test fun matchIsCaseInsensitiveAndRejectsBlank() {
        assertTrue(Checksum.matches(abcHash, abcHash.uppercase()))
        assertFalse(Checksum.matches(abcHash, ""))
        assertFalse(Checksum.matches(abcHash, abcHash.replace('b', 'c')))
    }
    @Test fun parsesSha256sumLine() {
        assertEquals(abcHash, Checksum.parseExpected("${abcHash.uppercase()}  rootfs.tar.xz\n"))
        assertNull(Checksum.parseExpected("not-a-hash"))
        assertNull(Checksum.parseExpected(abcHash.dropLast(1)))
    }
}
