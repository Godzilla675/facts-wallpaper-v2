package com.factswallpaper.core.backup

import android.content.Context
import android.util.Base64
import com.factswallpaper.core.security.SecretCipher
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AutomaticBackupFrequency { DAILY, WEEKLY }

data class BackupDestinationStatus(
    val hasDestination: Boolean = false,
    val automaticEnabled: Boolean = false,
    val frequency: AutomaticBackupFrequency = AutomaticBackupFrequency.DAILY,
    val lastBackupAtEpochMs: Long? = null,
    val lastBackupSucceeded: Boolean? = null,
    val lastError: String? = null,
)

data class SavedBackupConfiguration(
    val uri: String,
    val passphrase: String,
    val options: BackupOptions,
)

interface BackupDestinationStore {
    fun observeStatus(): Flow<BackupDestinationStatus>
    suspend fun configure(uri: String, passphrase: String, options: BackupOptions)
    suspend fun setAutomatic(enabled: Boolean, frequency: AutomaticBackupFrequency)
    fun loadConfiguration(): SavedBackupConfiguration?
    suspend fun recordResult(succeeded: Boolean, error: String? = null)
}

@Singleton
class SharedPreferencesBackupDestinationStore @Inject constructor(
    @ApplicationContext context: Context,
    private val secretCipher: SecretCipher,
) : BackupDestinationStore {

    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)
    private val mutableStatus = MutableStateFlow(readStatus())

    override fun observeStatus(): Flow<BackupDestinationStatus> = mutableStatus.asStateFlow()

    override suspend fun configure(uri: String, passphrase: String, options: BackupOptions) {
        require(uri.isNotBlank()) { "Backup destination URI must not be blank." }
        require(passphrase.length >= BackupManagerImpl.MIN_PASSPHRASE_LENGTH) {
            "Passphrase must be at least ${BackupManagerImpl.MIN_PASSPHRASE_LENGTH} characters long."
        }
        val passphraseBytes = passphrase.toByteArray(Charsets.UTF_8)
        val encryptedPassphrase = try {
            secretCipher.encrypt(passphraseBytes)
        } finally {
            passphraseBytes.fill(0)
        }
        preferences.edit()
            .putString(KEY_URI, uri)
            .putString(KEY_ENCRYPTED_PASSPHRASE, Base64.encodeToString(encryptedPassphrase, Base64.NO_WRAP))
            .putBoolean(KEY_INCLUDE_ASSETS, options.includeRenderedAssets)
            .putBoolean(KEY_INCLUDE_PDFS, options.includeOriginalPdfs)
            .apply()
        mutableStatus.value = readStatus()
    }

    override suspend fun setAutomatic(
        enabled: Boolean,
        frequency: AutomaticBackupFrequency,
    ) {
        preferences.edit()
            .putBoolean(KEY_AUTOMATIC_ENABLED, enabled)
            .putString(KEY_FREQUENCY, frequency.name)
            .apply()
        mutableStatus.value = readStatus()
    }

    override fun loadConfiguration(): SavedBackupConfiguration? {
        val uri = preferences.getString(KEY_URI, null) ?: return null
        val encrypted = preferences.getString(KEY_ENCRYPTED_PASSPHRASE, null) ?: return null
        val passphraseBytes = secretCipher.decrypt(Base64.decode(encrypted, Base64.NO_WRAP))
        val passphrase = try {
            passphraseBytes.toString(Charsets.UTF_8)
        } finally {
            passphraseBytes.fill(0)
        }
        return SavedBackupConfiguration(
            uri = uri,
            passphrase = passphrase,
            options = BackupOptions(
                includeRenderedAssets = preferences.getBoolean(KEY_INCLUDE_ASSETS, false),
                includeOriginalPdfs = preferences.getBoolean(KEY_INCLUDE_PDFS, false),
            ),
        )
    }

    override suspend fun recordResult(succeeded: Boolean, error: String?) {
        preferences.edit()
            .putLong(KEY_LAST_BACKUP_AT, System.currentTimeMillis())
            .putBoolean(KEY_LAST_BACKUP_SUCCEEDED, succeeded)
            .putString(KEY_LAST_ERROR, if (succeeded) null else error.orEmpty())
            .apply()
        mutableStatus.value = readStatus()
    }

    private fun readStatus(): BackupDestinationStatus {
        val frequency = preferences.getString(KEY_FREQUENCY, null)
            ?.let { runCatching { AutomaticBackupFrequency.valueOf(it) }.getOrNull() }
            ?: AutomaticBackupFrequency.DAILY
        val hasDestination = preferences.getString(KEY_URI, null) != null
        val hasResult = preferences.contains(KEY_LAST_BACKUP_SUCCEEDED)
        return BackupDestinationStatus(
            hasDestination = hasDestination,
            automaticEnabled = preferences.getBoolean(KEY_AUTOMATIC_ENABLED, false),
            frequency = frequency,
            lastBackupAtEpochMs = preferences.getLong(KEY_LAST_BACKUP_AT, 0L)
                .takeIf { it > 0L },
            lastBackupSucceeded = if (hasResult) {
                preferences.getBoolean(KEY_LAST_BACKUP_SUCCEEDED, false)
            } else {
                null
            },
            lastError = preferences.getString(KEY_LAST_ERROR, null)?.takeIf(String::isNotBlank),
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "backup_destination"
        const val KEY_URI = "destination_uri"
        const val KEY_ENCRYPTED_PASSPHRASE = "encrypted_passphrase"
        const val KEY_INCLUDE_ASSETS = "include_rendered_assets"
        const val KEY_INCLUDE_PDFS = "include_original_pdfs"
        const val KEY_AUTOMATIC_ENABLED = "automatic_enabled"
        const val KEY_FREQUENCY = "frequency"
        const val KEY_LAST_BACKUP_AT = "last_backup_at"
        const val KEY_LAST_BACKUP_SUCCEEDED = "last_backup_succeeded"
        const val KEY_LAST_ERROR = "last_error"
    }
}
