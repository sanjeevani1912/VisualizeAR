package com.example.dhruvar.data.remote

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

/**
 * Remembers the server address and the signed-in session on this phone.
 *
 * The session token is a credential, so it is encrypted (AES-GCM) with a key that lives in the
 * Android Keystore and never leaves the device's secure hardware or system store. The password
 * is never stored. Preferences are excluded from device backups (see backup_rules.xml), and a
 * token that cannot be decrypted is treated as "signed out".
 */
class SessionStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var serverUrl: String
        get() = prefs.getString(KEY_URL, "").orEmpty()
        set(value) {
            prefs.edit().putString(KEY_URL, value).apply()
        }

    fun saveSession(session: TokenSession) {
        val user = JSONObject()
            .put("id", session.user.id)
            .put("username", session.user.username)
            .put("name", session.user.name)
            .put("rank", session.user.rank)
            .put("role", session.user.role)
        prefs.edit()
            .putString(KEY_TOKEN, encrypt(session.token))
            .putString(KEY_USER, user.toString())
            .apply()
    }

    fun token(): String? {
        val stored = prefs.getString(KEY_TOKEN, null) ?: return null
        return try {
            decrypt(stored)
        } catch (_: Exception) {
            clearSession()
            null
        }
    }

    fun user(): SignedInUser? {
        val stored = prefs.getString(KEY_USER, null) ?: return null
        return try {
            val u = JSONObject(stored)
            SignedInUser(
                id = u.getString("id"),
                username = u.getString("username"),
                name = u.getString("name"),
                rank = u.optString("rank"),
                role = u.getString("role")
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clearSession() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_USER).apply()
    }

    private fun key(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(cipher.iv + sealed, Base64.NO_WRAP)
    }

    private fun decrypt(stored: String): String {
        val bytes = Base64.decode(stored, Base64.NO_WRAP)
        val iv = bytes.copyOfRange(0, IV_BYTES)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, iv))
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    private companion object {
        const val PREFS_NAME = "dhruvar_session"
        const val KEY_URL = "server_url"
        const val KEY_TOKEN = "token"
        const val KEY_USER = "user"
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "dhruvar_session_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
