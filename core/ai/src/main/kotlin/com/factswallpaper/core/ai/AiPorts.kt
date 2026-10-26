package com.factswallpaper.core.ai

import com.factswallpaper.core.model.AiRequestLogEntry
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.RoutingStrategy
import com.factswallpaper.core.model.UsageEntry

/** Narrow ports implemented by :core:data so :core:ai never depends on it. */
interface AiProfileProvider {
    suspend fun getProfiles(): List<ProviderProfile>
    suspend fun getProfile(profileId: String): ProviderProfile?
    suspend fun updateProfile(profile: ProviderProfile)
}

interface AiPreferencesSource {
    suspend fun routingStrategy(): RoutingStrategy
    suspend fun taskProfileOverride(task: AiTask): String?
    suspend fun promptSuffix(task: AiTask): String?
    suspend fun diagnosticsEnabled(): Boolean
}

interface AiUsageRecorder {
    suspend fun recordUsage(entry: UsageEntry)
}

interface AiLogRecorder {
    suspend fun recordLog(entry: AiRequestLogEntry)
}
