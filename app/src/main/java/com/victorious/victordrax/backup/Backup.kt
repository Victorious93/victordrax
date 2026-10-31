package com.victorious.victordrax.backup

import com.victorious.victordrax.db.Engagement
import com.victorious.victordrax.db.Finding
import com.victorious.victordrax.db.Note
import com.victorious.victordrax.db.Playbook
import com.victorious.victordrax.db.PlaybookStep
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

@Serializable
data class BackupBundle(
    val schema: Int = 2,
    val exportedAt: Long,
    val engagements: List<Engagement>,
    val notes: List<Note>,
    val findings: List<Finding>,
    val playbooks: List<Playbook>,
    val steps: List<PlaybookStep>,
)

/**
 * Encrypted, portable backup: a bundle JSON encrypted with AES-256-GCM using a key derived from the
 * user passphrase via PBKDF2-HMAC-SHA256. Pure (java.security/javax.crypto only), so round-trip and
 * wrong-password behaviour are unit tested.
 *
 * File layout: MAGIC(8) | version(1) | iterations(int) | saltLen(int) | salt | ivLen(int) | iv | ciphertext(+GCM tag)
 */
object Backup {
    private val MAGIC = "VDRAXBK1".toByteArray(Charsets.US_ASCII)
    private const val VERSION = 1
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encodeBundle(b: BackupBundle): String = json.encodeToString(BackupBundle.serializer(), b)
    fun decodeBundle(s: String): BackupBundle = json.decodeFromString(BackupBundle.serializer(), s)

    fun deriveKey(passphrase: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(PBEKeySpec(passphrase, salt, iterations, KEY_BITS)).encoded
    }

    fun encrypt(plaintext: ByteArray, passphrase: CharArray, rnd: SecureRandom = SecureRandom()): ByteArray {
        val salt = ByteArray(SALT_LEN).also { rnd.nextBytes(it) }
        val iv = ByteArray(IV_LEN).also { rnd.nextBytes(it) }
        val key = SecretKeySpec(deriveKey(passphrase, salt), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ct = cipher.doFinal(plaintext)
        val bos = ByteArrayOutputStream()
        DataOutputStream(bos).use { d ->
            d.write(MAGIC); d.writeByte(VERSION); d.writeInt(ITERATIONS)
            d.writeInt(salt.size); d.write(salt); d.writeInt(iv.size); d.write(iv); d.write(ct)
        }
        return bos.toByteArray()
    }

    class WrongPasswordException : GeneralSecurityException("Wrong passphrase or corrupted backup")

    fun decrypt(data: ByteArray, passphrase: CharArray): ByteArray {
        val din = DataInputStream(data.inputStream())
        val magic = ByteArray(MAGIC.size).also { din.readFully(it) }
        require(magic.contentEquals(MAGIC)) { "Not a VictorDrax backup file" }
        val version = din.readByte().toInt(); require(version == VERSION) { "Unsupported backup version $version" }
        val iterations = din.readInt()
        val salt = ByteArray(din.readInt()).also { din.readFully(it) }
        val iv = ByteArray(din.readInt()).also { din.readFully(it) }
        val ct = din.readBytes()
        val key = SecretKeySpec(deriveKey(passphrase, salt, iterations), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return try { cipher.doFinal(ct) } catch (e: javax.crypto.AEADBadTagException) { throw WrongPasswordException() }
    }

    fun writeEncrypted(bundle: BackupBundle, passphrase: CharArray, out: OutputStream) {
        out.write(encrypt(encodeBundle(bundle).toByteArray(Charsets.UTF_8), passphrase)); out.flush()
    }

    fun readEncrypted(passphrase: CharArray, input: InputStream): BackupBundle =
        decodeBundle(String(decrypt(input.readBytes(), passphrase), Charsets.UTF_8))
}
