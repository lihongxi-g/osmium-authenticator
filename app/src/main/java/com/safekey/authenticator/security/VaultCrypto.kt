package com.safekey.authenticator.security

import com.safekey.authenticator.model.EncryptedVault
import com.safekey.authenticator.model.EncryptedVaultArgon2
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.SecureRandom
import java.util.Arrays
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Thrown when a backup names a KDF this build does not implement.
 *
 * Deliberately distinct from a password failure: the file is fine, the app is
 * too old. The UI must say "update Osmium", never "wrong password".
 */
class UnsupportedKdfException(message: String) : IllegalArgumentException(message)

/**
 * Thrown when an Argon2 run cannot fit into this process's heap. Raised
 * *before* allocating, so the app reports the problem instead of dying with an
 * OutOfMemoryError.
 */
class KdfMemoryException(message: String) : IllegalArgumentException(message)

/**
 * Password-based encryption for the portable export/import format.
 *
 * Two KDFs, one symmetric layer (AES-256-GCM, 16-byte salt, 12-byte IV,
 * 128-bit tag):
 *
 * - **version 1** — PBKDF2-HMAC-SHA256, 120,000 iterations. The original
 *   format. Written by [BackupEncryptionLevel.STANDARD] and still the only
 *   thing the WebDAV / auto-backup / LAN paths produce.
 * - **version 2** — Argon2id, parameters written into the envelope. `m`, `t`
 *   and `p` are read from the file, so restoring does not depend on the
 *   exporting phone's setting.
 *
 * Decryption dispatches on the envelope's `kdf` string — never on `version` —
 * so a v1 file takes the exact path it always took.
 */
object VaultCrypto {

    private const val KDF_PBKDF2 = "PBKDF2-HMAC-SHA256"
    private const val KDF_ARGON2ID = "argon2id"

    private const val ITERATIONS = 120_000

    /**
     * Accepted iteration range for imported envelopes. The count comes from the
     * file itself, so without a bound a malformed or hostile backup could pin a
     * core for hours inside an uninterruptible PBKDF2 run.
     */
    private const val MIN_ITERATIONS = 10_000
    private const val MAX_ITERATIONS = 1_000_000

    // Argon2 bounds for imported envelopes, same reasoning: these numbers are
    // attacker-controlled, so everything is validated before a single byte is
    // allocated.
    private const val MIN_ARGON2_TIME = 1
    private const val MAX_ARGON2_TIME = 4
    private const val MIN_ARGON2_PARALLELISM = 1
    private const val MAX_ARGON2_PARALLELISM = 4
    private const val MAX_ARGON2_MEMORY_KIB = 512 * 1024

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val KEY_BITS = 256
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    /**
     * Encrypt the plaintext vault JSON with a user password. Returns the
     * envelope JSON.
     *
     * [level] only affects the file being written now; it is never used when
     * decrypting.
     */
    @JvmOverloads
    fun encrypt(
        plaintextJson: String,
        password: CharArray,
        level: BackupEncryptionLevel = BackupEncryptionLevel.STANDARD,
        ignoreMemoryLimit: Boolean = false
    ): String =
        if (level.isArgon2) encryptArgon2(plaintextJson, password, level, ignoreMemoryLimit)
        else encryptPbkdf2(plaintextJson, password)

    /**
     * Decrypt an envelope. Throws [IllegalArgumentException] on wrong password
     * or corrupted data (GCM tag verification failure), [UnsupportedKdfException]
     * when the file needs a newer app, and [KdfMemoryException] when this
     * device cannot hold the KDF's working memory.
     */
    @JvmOverloads
    fun decrypt(
        envelopeJson: String,
        password: CharArray,
        ignoreMemoryLimit: Boolean = false
    ): String {
        val kdf = try {
            val root = json.parseToJsonElement(envelopeJson) as? JsonObject
                ?: throw IllegalArgumentException("Not an Osmium encrypted file")
            (root["kdf"] as? JsonPrimitive)?.contentOrNull ?: KDF_PBKDF2
        } catch (e: IllegalArgumentException) {
            throw e
        } catch (e: Exception) {
            throw IllegalArgumentException("Not an Osmium encrypted file", e)
        }
        return when (kdf) {
            KDF_PBKDF2 -> decryptPbkdf2(envelopeJson, password)
            KDF_ARGON2ID -> decryptArgon2(envelopeJson, password, ignoreMemoryLimit)
            else -> throw UnsupportedKdfException("Unsupported key derivation function: $kdf")
        }
    }

    // ---- version 1: PBKDF2 (unchanged) ----

    private fun encryptPbkdf2(plaintextJson: String, password: CharArray): String {
        val salt = randomBytes(SALT_BYTES)
        val key = derivePbkdf2Key(password, salt, ITERATIONS)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintextJson.toByteArray(Charsets.UTF_8))
        val envelope = EncryptedVault(
            iterations = ITERATIONS,
            salt = Base64.getEncoder().encodeToString(salt),
            iv = Base64.getEncoder().encodeToString(cipher.iv),
            ciphertext = Base64.getEncoder().encodeToString(ciphertext)
        )
        return json.encodeToString(envelope)
    }

    private fun decryptPbkdf2(envelopeJson: String, password: CharArray): String {
        val envelope = try {
            json.decodeFromString<EncryptedVault>(envelopeJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not an Osmium encrypted file", e)
        }
        requireFormat(envelope.format)
        val salt = decodeBase64(envelope.salt)
        val iv = decodeBase64(envelope.iv)
        val ciphertext = decodeBase64(envelope.ciphertext)
        if (envelope.iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw IllegalArgumentException(
                "Unsupported key derivation parameters (${envelope.iterations} iterations)"
            )
        }
        val key = derivePbkdf2Key(password, salt, envelope.iterations)
        return decryptGcm(key, iv, ciphertext)
    }

    // ---- version 2: Argon2id ----

    private fun encryptArgon2(
        plaintextJson: String,
        password: CharArray,
        level: BackupEncryptionLevel,
        ignoreMemoryLimit: Boolean
    ): String {
        val salt = randomBytes(SALT_BYTES)
        // Same refusal as the read path: the RAM gate only looks at total
        // memory, while the JVM heap can be much smaller (256 MB on a phone
        // with 8 GB). Fail with a message the UI can show instead of an OOM.
        // The developer-mode override skips this too, by design.
        if (!ignoreMemoryLimit) {
            requireHeapForArgon2(
                level.memoryKib,
                plaintextJson.toByteArray(Charsets.UTF_8).size * 3
            )
        }
        val key = deriveArgon2Key(password, salt, level)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val ciphertext = cipher.doFinal(plaintextJson.toByteArray(Charsets.UTF_8))
        val envelope = EncryptedVaultArgon2(
            memoryKib = level.memoryKib,
            timeCost = level.timeCost,
            parallelism = level.parallelism,
            level = level.id,
            salt = Base64.getEncoder().encodeToString(salt),
            iv = Base64.getEncoder().encodeToString(cipher.iv),
            ciphertext = Base64.getEncoder().encodeToString(ciphertext)
        )
        return json.encodeToString(envelope)
    }

    private fun decryptArgon2(
        envelopeJson: String,
        password: CharArray,
        ignoreMemoryLimit: Boolean
    ): String {
        val envelope = try {
            json.decodeFromString<EncryptedVaultArgon2>(envelopeJson)
        } catch (e: Exception) {
            throw IllegalArgumentException("Not an Osmium encrypted file", e)
        }
        requireFormat(envelope.format)
        if (envelope.version != 2) {
            throw IllegalArgumentException("Unsupported Argon2 envelope version (${envelope.version})")
        }
        validateArgon2Parameters(envelope.memoryKib, envelope.timeCost, envelope.parallelism)

        val salt = decodeBase64(envelope.salt)
        val iv = decodeBase64(envelope.iv)
        val ciphertext = decodeBase64(envelope.ciphertext)
        if (!ignoreMemoryLimit) {
            requireHeapForArgon2(envelope.memoryKib, ciphertext.size)
        }
        val key = deriveArgon2Key(
            password,
            salt,
            envelope.memoryKib,
            envelope.timeCost,
            envelope.parallelism
        )
        return decryptGcm(key, iv, ciphertext)
    }

    /**
     * Rejects hostile or broken parameters before any memory is touched.
     * `memory_kib` is KiB: 64 MiB arrives here as 65536.
     */
    private fun validateArgon2Parameters(memoryKib: Int, timeCost: Int, parallelism: Int) {
        if (parallelism !in MIN_ARGON2_PARALLELISM..MAX_ARGON2_PARALLELISM) {
            throw IllegalArgumentException("Unsupported key derivation parameters (p=$parallelism)")
        }
        if (timeCost !in MIN_ARGON2_TIME..MAX_ARGON2_TIME) {
            throw IllegalArgumentException("Unsupported key derivation parameters (t=$timeCost)")
        }
        // Argon2 requires m >= 8 * p (RFC 9106 §3.1).
        if (memoryKib < 8 * parallelism || memoryKib > MAX_ARGON2_MEMORY_KIB) {
            throw IllegalArgumentException("Unsupported key derivation parameters (m=$memoryKib KiB)")
        }
    }

    /**
     * BouncyCastle's Argon2 allocates its whole block matrix on the Java heap.
     * Android caps that heap per app (roughly 128 MB on a 2 GB phone, 256-512 MB
     * otherwise), so a perfectly valid 128 MiB backup would otherwise crash a
     * low-memory phone with OutOfMemoryError. Refusing early lets the UI say
     * "not enough memory" instead.
     */
    private fun requireHeapForArgon2(memoryKib: Int, ciphertextBytes: Int) {
        // Each 1 KiB block is a long[128] plus an object header: ~1.05 KiB.
        val blocks = memoryKib.toLong() * 1024L * 105 / 100
        val needed = blocks + ciphertextBytes.toLong() * 3
        val maxHeap = Runtime.getRuntime().maxMemory()
        if (needed > maxHeap / 10 * 8) {
            throw KdfMemoryException(
                "Argon2 needs $memoryKib KiB; this process can use ${maxHeap / (1024 * 1024)} MiB"
            )
        }
    }

    // ---- key derivation ----

    private fun derivePbkdf2Key(
        password: CharArray,
        salt: ByteArray,
        iterations: Int
    ): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password, salt, iterations, KEY_BITS)
        val keyBytes = try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return try {
            SecretKeySpec(keyBytes, "AES")
        } finally {
            Arrays.fill(keyBytes, 0)
        }
    }

    private fun deriveArgon2Key(
        password: CharArray,
        salt: ByteArray,
        level: BackupEncryptionLevel
    ): SecretKeySpec = deriveArgon2Key(
        password, salt, level.memoryKib, level.timeCost, level.parallelism
    )

    private fun deriveArgon2Key(
        password: CharArray,
        salt: ByteArray,
        memoryKib: Int,
        timeCost: Int,
        parallelism: Int
    ): SecretKeySpec {
        val parameters = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
            .withSalt(salt)
            .withParallelism(parallelism)
            .withMemoryAsKB(memoryKib)
            .withIterations(timeCost)
            .build()
        val keyBytes = ByteArray(KEY_BITS / 8)
        try {
            val generator = Argon2BytesGenerator()
            // init() allocates the block matrix, so it has to sit inside the
            // catch: with the developer override on, this is where an
            // over-sized level actually runs out of memory.
            generator.init(parameters)
            generator.generateBytes(password, keyBytes)
        } catch (e: OutOfMemoryError) {
            // Last line of defence behind requireHeapForArgon2.
            throw KdfMemoryException("Out of memory deriving the backup key")
        }
        return try {
            SecretKeySpec(keyBytes, "AES")
        } finally {
            Arrays.fill(keyBytes, 0)
        }
    }

    // ---- shared ----

    private fun decryptGcm(key: SecretKeySpec, iv: ByteArray, ciphertext: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        val plaintext = try {
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw IllegalArgumentException("Wrong password or corrupted data", e)
        }
        return String(plaintext, Charsets.UTF_8)
    }

    private fun requireFormat(format: String) {
        // accept vaults exported by old SafeKey builds too
        if (format != "osmium-encrypted" && format != "safekey-encrypted") {
            throw IllegalArgumentException("Not an Osmium encrypted file")
        }
    }

    private fun decodeBase64(value: String): ByteArray = try {
        Base64.getDecoder().decode(value)
    } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException("Not an Osmium encrypted file", e)
    }

    private fun randomBytes(size: Int): ByteArray =
        ByteArray(size).also { SecureRandom().nextBytes(it) }
}
