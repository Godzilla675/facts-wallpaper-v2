package com.factswallpaper.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import java.security.SecureRandom
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AndroidKeystoreApiKeyStoreTest {

    private lateinit var context: Context
    private lateinit var store: AndroidKeystoreApiKeyStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(AndroidKeystoreApiKeyStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        store = AndroidKeystoreApiKeyStore(context, AesGcmSecretCipher(testKey()))
    }

    @Test
    fun `round trips a key without persisting the raw value`() = runTest {
        store.setApiKey("profile-1", "sk-super-secret-value")

        assertThat(store.getApiKey("profile-1")).isEqualTo("sk-super-secret-value")
        assertThat(store.hasApiKey("profile-1")).isTrue()

        val rawPreferences = context.getSharedPreferences(
            AndroidKeystoreApiKeyStore.PREFERENCES_NAME,
            Context.MODE_PRIVATE,
        ).all
        assertThat(rawPreferences.keys).containsExactly(
            AndroidKeystoreApiKeyStore.preferenceKey("profile-1"),
        )
        assertThat(rawPreferences.values.joinToString()).doesNotContain("sk-super-secret-value")
        assertThat(rawPreferences.keys.first()).startsWith(AndroidKeystoreApiKeyStore.KEY_PREFIX)
        assertThat(rawPreferences.keys.first()).doesNotContain("profile-1")
    }

    @Test
    fun `deleting a key removes only that profile`() = runTest {
        store.setApiKey("a", "sk-key-a-12345678")
        store.setApiKey("b", "sk-key-b-12345678")

        store.deleteApiKey("a")

        assertThat(store.getApiKey("a")).isNull()
        assertThat(store.getApiKey("b")).isEqualTo("sk-key-b-12345678")
        assertThat(store.configuredProfileIds(listOf("a", "b", "c")))
            .containsExactly("b")
    }

    @Test
    fun `corrupt payload self heals by deleting the entry`() = runTest {
        val preferenceKey = AndroidKeystoreApiKeyStore.preferenceKey("broken")
        context.getSharedPreferences(AndroidKeystoreApiKeyStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(preferenceKey, "not-a-valid-base64-payload")
            .commit()

        assertThat(store.getApiKey("broken")).isNull()
        assertThat(
            context.getSharedPreferences(AndroidKeystoreApiKeyStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .contains(preferenceKey)
        ).isFalse()
    }

    @Test
    fun `payload encrypted with a different key is rejected and removed`() = runTest {
        val otherStore = AndroidKeystoreApiKeyStore(context, AesGcmSecretCipher(testKey()))
        otherStore.setApiKey("foreign", "sk-foreign-key-1234")

        // A new store instance uses a fresh in-memory key, so the payload cannot be decrypted.
        val newStore = AndroidKeystoreApiKeyStore(context, AesGcmSecretCipher(testKey()))
        assertThat(newStore.getApiKey("foreign")).isNull()
        assertThat(
            context.getSharedPreferences(AndroidKeystoreApiKeyStore.PREFERENCES_NAME, Context.MODE_PRIVATE)
                .contains(AndroidKeystoreApiKeyStore.preferenceKey("foreign"))
        ).isFalse()
    }

    @Test
    fun `preference key is a stable sha256 base64 url token`() {
        val first = AndroidKeystoreApiKeyStore.preferenceKey("profile-1")
        val second = AndroidKeystoreApiKeyStore.preferenceKey("profile-1")

        assertThat(first).isEqualTo(second)
        assertThat(first).matches("api_key_[A-Za-z0-9_-]{43}")
    }

    private fun testKey(): SecretKey {
        val generator = KeyGenerator.getInstance("AES")
        generator.init(256, SecureRandom())
        return generator.generateKey()
    }
}
