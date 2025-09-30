/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright (c) 2024 Live Without Belief
 */
package info.lwb.auth

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Abstraction for persisting sensitive authentication-related values.
 * Implementations must provide confidentiality & integrity guarantees (e.g. encrypted prefs).
 */
interface SecureStorage {
    /** Store opaque ID token string (overwrites existing). */
    fun putIdToken(token: String)

    /** Retrieve previously stored ID token or null if absent. */
    fun getIdToken(): String?

    /** Persist token expiry epoch seconds; null removes stored value. */
    fun putTokenExpiry(epochSeconds: Long?)

    /** Obtain previously stored token expiry or null if not set. */
    fun getTokenExpiry(): Long?

    /** Remove all stored secure values. */
    fun clear()

    /** Persist optional user profile fields. */
    fun putProfile(name: String?, email: String?, avatar: String?)

    /** Fetch a triple of profile fields (name, email, avatar). */
    fun getProfile(): Triple<String?, String?, String?>
}

/** SecureStorage implementation backed by AndroidX EncryptedSharedPreferences. */
class EncryptedPrefsSecureStorage(context: Context) : SecureStorage {
    private val masterKey = MasterKey
        .Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    override fun putIdToken(token: String) {
        prefs.edit().putString(KEY_ID_TOKEN, token).apply()
    }

    override fun getIdToken(): String? = prefs.getString(KEY_ID_TOKEN, null)

    override fun putTokenExpiry(epochSeconds: Long?) {
        prefs
            .edit()
            .apply {
                if (epochSeconds == null) {
                    remove(KEY_ID_TOKEN_EXP)
                } else {
                    putLong(KEY_ID_TOKEN_EXP, epochSeconds)
                }
            }.apply()
    }

    override fun getTokenExpiry(): Long? = if (prefs.contains(KEY_ID_TOKEN_EXP)) {
        prefs.getLong(KEY_ID_TOKEN_EXP, -1L).takeIf { it > 0 }
    } else {
        null
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    override fun putProfile(name: String?, email: String?, avatar: String?) {
        prefs
            .edit()
            .putString(KEY_NAME, name)
            .putString(KEY_EMAIL, email)
            .putString(KEY_AVATAR, avatar)
            .apply()
    }

    override fun getProfile(): Triple<String?, String?, String?> =
        Triple(
            prefs.getString(KEY_NAME, null),
            prefs.getString(KEY_EMAIL, null),
            prefs.getString(KEY_AVATAR, null),
        )

    private companion object {
        const val PREFS_FILE = "auth.secure.prefs"
        const val KEY_ID_TOKEN = "idToken"
        const val KEY_ID_TOKEN_EXP = "idTokenExp"
        const val KEY_NAME = "name"
        const val KEY_EMAIL = "email"
        const val KEY_AVATAR = "avatar"
    }
}
