package com.victorious.victordrax.data

import java.io.File
import java.io.InputStream
import java.security.MessageDigest

object Checksum {
    fun sha256(input: InputStream, onBytes: (Long) -> Unit = {}): String {
        val md = MessageDigest.getInstance("SHA-256")
        val buf = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buf); if (n < 0) break
            md.update(buf, 0, n); total += n; onBytes(total)
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun sha256(file: File): String = file.inputStream().use { sha256(it) }

    fun matches(actual: String, expected: String): Boolean =
        expected.isNotBlank() && actual.equals(expected.trim(), ignoreCase = true)

    /** Accepts either a bare hash or a `sha256sum` style line ("<hash>  file"). */
    fun parseExpected(text: String): String? =
        text.trim().split(Regex("\\s+")).firstOrNull()?.takeIf { Validation.isSha256(it) }?.lowercase()
}
