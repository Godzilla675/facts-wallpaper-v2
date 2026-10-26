package com.factswallpaper.core.security

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.GeneralSecurityException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hardware-backed API key store per spec F23.
 *
 * Keys live in the dedicated `facts_wallpaper_secure_keys` preferences file under
 * `"api_key_" + Base64UrlSafeNoWrap(SHA-256(profileId))`. The profile id itself is
 * never written to that file. Any corrupt payload or Keystore failure deletes the
 * bad entry and returns `null`, so a broken key can never brick settings.
 */
@Singleton
class AndroidKeystoreApiKeyStore @Inject constructor(
    @ApplicationContext context: Context,
    private val cipher: SecretCipher,
) : ApiKeyStore {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    internal constructor(context: Context, cipher: SecretCipher, dispatcher: CoroutineDispatcher) : this(
        context,
        cipher,
    ) {
        this.dispatcher = dispatcher
    }

    private var dispatcher: CoroutineDispatcher = Dispatchers.IO

    override suspend fun getApiKey(profileId: String): String? = withContext(dispatcher) {
        val key = preferenceKey(profileId)
        val encoded = preferences.getString(key, null) ?: return@withContext null
        try {
            val payload = Base64.decode(encoded, BASE64_FLAGS)
            String(cipher.decrypt(payload), StandardCharsets.UTF_8)
        } catch (exception: GeneralSecurityException) {
            selfHeal(key)
            null
        } catch (exception: IllegalArgumentException) {
            selfHeal(key)
            null
        } catch (exception: RuntimeException) {
            selfHeal(key)
            null
        }
    }

    override suspend fun setApiKey(profileId: String, apiKey: String) = withContext(dispatcher) {
        require(apiKey.isNotBlank()) { "API key must not be blank" }
        val encrypted = cipher.encrypt(apiKey.trim().toByteArray(StandardCharsets.UTF_8))
        preferences.edit()
            .putString(preferenceKey(profileId), Base64.encodeToString(encrypted, BASE64_FLAGS))
            .apply()
    }

    override suspend fun deleteApiKey(profileId: String) = withContext(dispatcher) {
        preferences.edit().remove(preferenceKey(profileId)).apply()
    }

    override suspend fun hasApiKey(profileId: String): Boolean = withContext(dispatcher) {
        preferences.contains(preferenceKey(profileId))
    }

    override suspend fun configuredProfileIds(candidateProfileIds: Collection<String>): Set<String> =
        withContext(dispatcher) {
            val keys = preferences.all.keys
            candidateProfileIds.filterTo(LinkedHashSet()) { preferenceKey(it) in keys }
        }

    private fun selfHeal(preferenceKey: String) {
        runCatching { preferences.edit().remove(preferenceKey).apply() }
    }

    companion object {
        const val PREFERENCES_NAME = "facts_wallpaper_secure_keys"
        const val KEY_PREFIX = "api_key_"

        private const val BASE64_FLAGS =
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

        /** Exposed for the redactor registry and unit tests. */
        fun preferenceKey(profileId: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(profileId.toByteArray(StandardCharsets.UTF_8))
            return KEY_PREFIX + Base64.encodeToString(digest, BASE64_FLAGS)
        }
    }
}
