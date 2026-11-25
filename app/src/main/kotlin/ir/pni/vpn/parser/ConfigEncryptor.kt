package ir.pni.vpn.parser

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Soldier-Client compatible AES-256-GCM configuration encryptor/decryptor (`pni://enc:...`).
 * Protects shared proxy configurations inside restricted messaging platforms.
 */
@OptIn(ExperimentalEncodingApi::class)
object ConfigEncryptor {

    private const val PREFIX_PNI = "pni://enc:"
    private const val PREFIX_SOLDIER = "soldier://enc:"
    private const val DEFAULT_PASSPHRASE = "PNI-Iran-Freedom-Default-Key"

    fun encrypt(plainConfig: String, passphrase: String = ""): String {
        val key = deriveKey(passphrase.ifBlank { DEFAULT_PASSPHRASE })
        val iv = ByteArray(12).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plainConfig.toByteArray(Charsets.UTF_8))
        val packed = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, packed, 0, iv.size)
        System.arraycopy(ciphertext, 0, packed, iv.size, ciphertext.size)
        val b64 = Base64.UrlSafe.encode(packed)
        return "$PREFIX_PNI$b64"
    }

    fun decrypt(encoded: String, passphrase: String = ""): String? {
        val clean = encoded.trim()
        val b64 = when {
            clean.startsWith(PREFIX_PNI, ignoreCase = true) -> clean.substring(PREFIX_PNI.length)
            clean.startsWith(PREFIX_SOLDIER, ignoreCase = true) -> clean.substring(PREFIX_SOLDIER.length)
            else -> return null
        }
        return runCatching {
            val padded = when (b64.length % 4) {
                2 -> "$b64=="
                3 -> "$b64="
                else -> b64
            }
            val packed = Base64.UrlSafe.decode(padded)
            if (packed.size < 13) return null
            val iv = packed.copyOfRange(0, 12)
            val ciphertext = packed.copyOfRange(12, packed.size)
            val key = deriveKey(passphrase.ifBlank { DEFAULT_PASSPHRASE })
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun deriveKey(passphrase: String): SecretKeySpec {
        val digest = MessageDigest.getInstance("SHA-256").digest(passphrase.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest, "AES")
    }
}
