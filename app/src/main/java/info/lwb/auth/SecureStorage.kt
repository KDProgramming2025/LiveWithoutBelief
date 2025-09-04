package info.lwb.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

interface SecureStorage {
    fun putIdToken(token: String)
    fun getIdToken(): String?
    fun clear()
    fun putProfile(name: String?, email: String?, avatar: String?)
    fun getProfile(): Triple<String?, String?, String?>
}

class EncryptedPrefsSecureStorage(context: Context) : SecureStorage {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "auth.secure.prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun putIdToken(token: String) { prefs.edit().putString("idToken", token).apply() }
    override fun getIdToken(): String? = prefs.getString("idToken", null)
    override fun clear() { prefs.edit().clear().apply() }
    override fun putProfile(name: String?, email: String?, avatar: String?) {
        prefs.edit()
            .putString("name", name)
            .putString("email", email)
            .putString("avatar", avatar)
            .apply()
    }
    override fun getProfile(): Triple<String?, String?, String?> =
        Triple(prefs.getString("name", null), prefs.getString("email", null), prefs.getString("avatar", null))
}