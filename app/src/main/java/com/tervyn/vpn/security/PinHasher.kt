package com.tervyn.vpn.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** PBKDF2-HMAC-SHA256 hashing for the app-lock PIN. Stored as `iterations$salt$hash`. */
object PinHasher {
    private const val ITERATIONS = 120_000

    fun hash(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        return "$ITERATIONS$" + b64(salt) + "$" + b64(derive(pin, salt, ITERATIONS))
    }

    fun verify(pin: String, stored: String): Boolean {
        val p = stored.split('$')
        if (p.size != 3) return false
        val it = p[0].toIntOrNull() ?: return false
        val expected = Base64.getDecoder().decode(p[2])
        return MessageDigest.isEqual(expected, derive(pin, Base64.getDecoder().decode(p[1]), it))
    }

    fun isValidPin(pin: String) = pin.length in 4..8 && pin.all(Char::isDigit)

    private fun derive(pin: String, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(pin.toCharArray(), salt, iterations, 256)).encoded

    private fun b64(b: ByteArray) = Base64.getEncoder().encodeToString(b)
}
