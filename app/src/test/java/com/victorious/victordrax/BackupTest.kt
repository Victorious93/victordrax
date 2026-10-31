package com.victorious.victordrax

import com.victorious.victordrax.backup.Backup
import com.victorious.victordrax.backup.BackupBundle
import com.victorious.victordrax.db.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class BackupTest {
    private val bundle = BackupBundle(exportedAt = 1, engagements = listOf(Engagement(id = 3, name = "Lab", client = "C", scope = "10.0.0.0/24", authorizedBy = "J", authorizedDate = 5, authDocUri = "content://d")),
        notes = listOf(Note(id = 1, engagementId = 3, text = "hi", createdAt = 2)),
        findings = listOf(Finding(id = 9, engagementId = 3, title = "XSS", severity = "Medium", cvssVector = "CVSS:3.1/AV:N/AC:L/PR:N/UI:R/S:C/C:L/I:L/A:N", createdAt = 4)),
        playbooks = listOf(Playbook(id = 2, name = "Mine", createdAt = 1)), steps = listOf(PlaybookStep(id = 1, playbookId = 2, ordinal = 0, label = "a", command = "whois {target}")))

    @Test fun roundTrip() {
        val out = ByteArrayOutputStream()
        Backup.writeEncrypted(bundle, "correct horse battery".toCharArray(), out)
        val bytes = out.toByteArray()
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("XSS")) // ciphertext, not plaintext
        assertEquals(bundle, Backup.readEncrypted("correct horse battery".toCharArray(), bytes.inputStream()))
    }
    @Test fun wrongPassword() {
        val out = ByteArrayOutputStream(); Backup.writeEncrypted(bundle, "correct horse battery".toCharArray(), out)
        assertThrows(Backup.WrongPasswordException::class.java) { Backup.readEncrypted("wrong passphrase!".toCharArray(), out.toByteArray().inputStream()) }
    }
    @Test fun tamperedAndForeignFiles() {
        val c = Backup.encrypt("x".toByteArray(), "pw-1234567890".toCharArray())
        c[c.size - 1] = (c[c.size - 1].toInt() xor 1).toByte()
        assertThrows(Backup.WrongPasswordException::class.java) { Backup.decrypt(c, "pw-1234567890".toCharArray()) }
        assertThrows(IllegalArgumentException::class.java) { Backup.decrypt("not a backup file at all".toByteArray(), "x".toCharArray()) }
    }
    @Test fun freshSaltAndIv() {
        val a = Backup.encrypt("x".toByteArray(), "p".toCharArray()); val b = Backup.encrypt("x".toByteArray(), "p".toCharArray())
        assertFalse(a.contentEquals(b))
    }
}
