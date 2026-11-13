package com.tervyn.vpn.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Encrypts/decrypts small secrets (proxy passwords, API keys, share links). */
interface SecretCipher {
    fun encrypt(plain: String): String
    fun decrypt(cipher: String): String?
}

/**
 * AES-256-GCM with a non-exportable key held in the Android Keystore (hardware-backed where available).
 * Ciphertext format: `k1:` + base64(iv || ciphertext+tag).
 */
class KeystoreCipher(private val alias: String = "tervyn_master_v1") : SecretCipher {
    private val key: SecretKey by lazy { loadOrCreate() }

    private fun loadOrCreate(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    override fun encrypt(plain: String): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key)
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return "k1:" + Base64.getEncoder().encodeToString(out)
    }

    override fun decrypt(cipher: String): String? = try {
        require(cipher.startsWith("k1:"))
        val raw = Base64.getDecoder().decode(cipher.substring(3))
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, raw, 0, 12))
        String(c.doFinal(raw, 12, raw.size - 12), Charsets.UTF_8)
    } catch (_: Exception) { null }
}

/** Test/fallback cipher. Never used in production code paths. */
class PlainCipherForTests : SecretCipher {
    override fun encrypt(plain: String) = "p:" + plain
    override fun decrypt(cipher: String) = cipher.removePrefix("p:")
}
