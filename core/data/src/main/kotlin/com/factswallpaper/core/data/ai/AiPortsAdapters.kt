package com.factswallpaper.core.data.ai

import com.factswallpaper.core.ai.AiLogRecorder
import com.factswallpaper.core.ai.AiPreferencesSource
import com.factswallpaper.core.ai.AiProfileProvider
import com.factswallpaper.core.ai.AiUsageRecorder
import com.factswallpaper.core.data.repository.DiagnosticsRepository
import com.factswallpaper.core.data.repository.ProviderProfileRepository
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.model.AiRequestLogEntry
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.RoutingStrategy
import com.factswallpaper.core.model.UsageEntry
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * Binds the narrow `:core:ai` ports to the P1 repositories. Keeping the ports in
 * `:core:ai` (instead of depending on `:core:data`) preserves the module graph
 * direction while still using the single Room/DataStore source of truth.
 */
@Singleton
class RepositoryAiProfileProvider @Inject constructor(
    private val profiles: ProviderProfileRepository,
) : AiProfileProvider {

    override suspend fun getProfiles(): List<ProviderProfile> = profiles.getProfiles()

    override suspend fun getProfile(profileId: String): ProviderProfile? =
        profiles.getProfile(profileId)

    override suspend fun updateProfile(profile: ProviderProfile) {
        profiles.saveProfile(profile)
    }
}

@Singleton
class SettingsAiPreferencesSource @Inject constructor(
    private val settings: SettingsRepository,
) : AiPreferencesSource {

    override suspend fun routingStrategy(): RoutingStrategy =
        settings.observePreferences().first().routingStrategy

    override suspend fun taskProfileOverride(task: AiTask): String? =
        settings.observePreferences().first().taskProfileOverrides[task]

    override suspend fun promptSuffix(task: AiTask): String? =
        settings.observePreferences().first().customTaskPromptSuffixes[task]

    override suspend fun diagnosticsEnabled(): Boolean =
        settings.observePreferences().first().enableAiDiagnosticsLog
}

@Singleton
class RepositoryAiTelemetry @Inject constructor(
    private val diagnostics: DiagnosticsRepository,
) : AiUsageRecorder, AiLogRecorder {

    override suspend fun recordUsage(entry: UsageEntry) {
        diagnostics.insertUsage(entry)
    }

    override suspend fun recordLog(entry: AiRequestLogEntry) {
        diagnostics.insertLog(entry)
    }
}
