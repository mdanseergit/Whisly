package com.example.core.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Provides Android Keystore-backed AES-GCM encryption for sensitive local records
 * with automatic key invalidation recovery (`KeyPermanentlyInvalidatedException`)
 * and SHA-256 cryptographic integrity verification for model manifests and files.
 */
object KeystoreCryptoManager {
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    private const val KEY_ALIAS = "whisly_local_storage_aes_key"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val GCM_TAG_LENGTH = 128

    private fun generateFreshKey(): SecretKey? {
        return try {
            val keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                ANDROID_KEYSTORE
            )
            val spec = KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
            keyGenerator.init(spec)
            keyGenerator.generateKey()
        } catch (_: Exception) {
            null
        }
    }

    private fun deleteAndRegenerateKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            if (keyStore.containsAlias(KEY_ALIAS)) {
                keyStore.deleteEntry(KEY_ALIAS)
            }
            generateFreshKey()
        } catch (_: Exception) {
            null
        }
    }

    private fun getOrCreateSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            val existingKey = keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry
            existingKey?.secretKey ?: generateFreshKey()
        } catch (_: Exception) {
            deleteAndRegenerateKey()
        }
    }

    fun encryptText(plainText: String): String {
        val secretKey = getOrCreateSecretKey() ?: return plainText
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
            val combined = ByteArray(1 + iv.size + encrypted.size)
            combined[0] = iv.size.toByte()
            System.arraycopy(iv, 0, combined, 1, iv.size)
            System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
            "ENC1:" + Base64.encodeToString(combined, Base64.NO_WRAP)
        } catch (_: KeyPermanentlyInvalidatedException) {
            val regenerated = deleteAndRegenerateKey() ?: return plainText
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, regenerated)
                val iv = cipher.iv
                val encrypted = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
                val combined = ByteArray(1 + iv.size + encrypted.size)
                combined[0] = iv.size.toByte()
                System.arraycopy(iv, 0, combined, 1, iv.size)
                System.arraycopy(encrypted, 0, combined, 1 + iv.size, encrypted.size)
                "ENC1:" + Base64.encodeToString(combined, Base64.NO_WRAP)
            } catch (_: Exception) {
                plainText
            }
        } catch (_: Exception) {
            plainText
        }
    }

    fun decryptText(cipherPayload: String): String {
        if (!cipherPayload.startsWith("ENC1:")) return cipherPayload
        val secretKey = getOrCreateSecretKey() ?: return cipherPayload.removePrefix("ENC1:")
        return try {
            val raw = Base64.decode(cipherPayload.removePrefix("ENC1:"), Base64.NO_WRAP)
            val ivSize = raw[0].toInt() and 0xFF
            val iv = raw.copyOfRange(1, 1 + ivSize)
            val encrypted = raw.copyOfRange(1 + ivSize, raw.size)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
            String(cipher.doFinal(encrypted), Charsets.UTF_8)
        } catch (_: KeyPermanentlyInvalidatedException) {
            deleteAndRegenerateKey()
            ""
        } catch (_: Exception) {
            cipherPayload
        }
    }

    fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun sha256File(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var read: Int
            while (fis.read(buffer).also { read = it } != -1) {
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun verifyManifestSignature(manifestJson: String, expectedSha256: String): Boolean {
        val actual = sha256Hex(manifestJson.toByteArray(Charsets.UTF_8))
        return actual.equals(expectedSha256.trim(), ignoreCase = true)
    }
}
