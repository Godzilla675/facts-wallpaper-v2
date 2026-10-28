package com.factswallpaper.core.security

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Synchronous, Keystore-backed storage for the SQLCipher database passphrase and
 * encryption-mode request from spec D4.
 *
 * The database provider needs this state *before* Room is built, so it cannot use
 * the coroutine-based DataStore or [ApiKeyStore] APIs. The 256-bit passphrase is
 * generated once with [SecureRandom] and stored encrypted with [SecretCipher] in a
 * dedicated private preferences file. The mode flag lives next to it so a single
 * synchronous read is enough to decide how to open the database.
 *
 * The DataStore preference `encryptedDatabaseEnabled` mirrors this flag for spec
 * section 5.4/UI, but this store is authoritative because it is readable during
 * Hilt database provisioning.
 */
@Singleton
class DatabaseEncryptionStore @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: SecretCipher,
) {

    private val random = SecureRandom()
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** True when the user asked for an encrypted database. */
    fun isEncryptionRequested(): Boolean = preferences.getBoolean(KEY_REQUESTED, false)

    /**
     * Records the user's request. Uses `commit()` because the value must be durable
     * before the process restarts, otherwise a crash could silently flip the mode.
     */
    fun setEncryptionRequested(enabled: Boolean) {
        preferences.edit().putBoolean(KEY_REQUESTED, enabled).commit()
    }

    /** The encryption state that was actually applied the last time Room was opened. */
    fun appliedEncryption(): Boolean? =
        if (preferences.contains(KEY_APPLIED)) preferences.getBoolean(KEY_APPLIED, false) else null

    fun setAppliedEncryption(encrypted: Boolean) {
        preferences.edit().putBoolean(KEY_APPLIED, encrypted).commit()
    }

    /**
     * Returns the database passphrase, generating and persisting it on first use.
     *
     * A Keystore failure is never healed by generating a new passphrase: doing so
     * would make an existing encrypted database unreadable. Callers get an explicit
     * exception instead.
     */
    @Synchronized
    fun passphrase(): ByteArray {
        preferences.getString(KEY_PASSPHRASE, null)?.let { stored ->
            return try {
                cipher.decrypt(Base64.decode(stored, BASE64_FLAGS))
            } catch (exception: Exception) {
                throw IllegalStateException(
                    "The database passphrase can no longer be decrypted with the Keystore key.",
                    exception,
                )
            }
        }
        val generated = ByteArray(PASSPHRASE_BYTES).also(random::nextBytes)
        val encoded = Base64.encodeToString(cipher.encrypt(generated), BASE64_FLAGS)
        preferences.edit().putString(KEY_PASSPHRASE, encoded).commit()
        return generated
    }

    companion object {
        const val PREFERENCES_NAME = "facts_wallpaper_database_security"
        const val KEY_REQUESTED = "encryption_requested"
        const val KEY_APPLIED = "encryption_applied"
        const val KEY_PASSPHRASE = "__sqlcipher_db_passphrase__"
        const val PASSPHRASE_BYTES = 32

        private const val BASE64_FLAGS = Base64.NO_WRAP
    }
}
