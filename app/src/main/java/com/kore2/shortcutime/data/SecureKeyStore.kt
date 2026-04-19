package com.kore2.shortcutime.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.kore2.shortcutime.llm.ProviderId

class SecureKeyStore(private val prefs: SharedPreferences) {

    fun save(provider: ProviderId, key: String) {
        val trimmed = key.trim()
        val editor = prefs.edit()
        if (trimmed.isEmpty()) editor.remove(provider.name) else editor.putString(provider.name, trimmed)
        editor.apply()
    }

    fun get(provider: ProviderId): String? = prefs.getString(provider.name, null)

    fun clear(provider: ProviderId) {
        prefs.edit().remove(provider.name).apply()
    }

    fun getAllSaved(): Set<ProviderId> =
        ProviderId.values().filter { !get(it).isNullOrBlank() }.toSet()

    companion object {
        private const val FILE_NAME = "api_keys_encrypted"

        fun create(context: Context): SecureKeyStore {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val encryptedPrefs = EncryptedSharedPreferences.create(
                context,
                FILE_NAME,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            return SecureKeyStore(encryptedPrefs)
        }
    }
}
