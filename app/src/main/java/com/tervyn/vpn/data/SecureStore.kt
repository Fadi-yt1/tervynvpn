package com.tervyn.vpn.data

import android.content.Context
import com.tervyn.vpn.security.SecretCipher

/** Small encrypted key/value store for API credentials (values are Keystore-encrypted before storage). */
class SecureStore(context: Context, private val cipher: SecretCipher) {
    private val prefs = context.getSharedPreferences("tervyn_secure", Context.MODE_PRIVATE)

    fun proxioKey(): String? = prefs.getString("proxio_key", null)?.let(cipher::decrypt)?.takeIf { it.isNotBlank() }
    fun setProxioKey(key: String?) {
        prefs.edit().apply { if (key.isNullOrBlank()) remove("proxio_key") else putString("proxio_key", cipher.encrypt(key.trim())) }.apply()
    }
    fun clearAll() = prefs.edit().clear().apply()
}
