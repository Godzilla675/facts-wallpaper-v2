package com.factswallpaper.core.ai

import com.factswallpaper.core.model.AiRequestLogEntry
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.ProviderKeyStatus
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.ProviderType
import com.factswallpaper.core.model.RoutingStrategy
import com.factswallpaper.core.model.UsageEntry
import com.factswallpaper.core.security.ApiKeyStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf

internal fun testProfile(
    id: String = "profile-1",
    name: String = "Test Provider",
    type: ProviderType = ProviderType.OPENAI,
    baseUrl: String = "https://api.example.com/v1",
    model: String = "test-model",
    supportsPdf: Boolean = true,
    supportsSchema: Boolean = true,
    supportsJsonMode: Boolean = true,
    supportsTemperature: Boolean = true,
    context: Int = 128_000,
    inputPrice: Double = 1.0,
    outputPrice: Double = 2.0,
    quality: Int = 2,
    speed: Int = 2,
    enabled: Boolean = true,
    keyStatus: ProviderKeyStatus = ProviderKeyStatus.UNKNOWN,
): ProviderProfile = ProviderProfile(
    id = id,
    name = name,
    providerType = type,
    baseUrl = baseUrl,
    modelName = model,
    supportsPdfInput = supportsPdf,
    supportsImageInput = true,
    supportsJsonMode = supportsJsonMode,
    supportsJsonSchema = supportsSchema,
    maxContextTokens = context,
    inputUsdPerMillion = inputPrice,
    outputUsdPerMillion = outputPrice,
    speedTier = speed,
    qualityTier = quality,
    enabled = enabled,
    supportsTemperature = supportsTemperature,
    keyStatus = keyStatus,
)

internal fun mockHttpClient(
    handler: suspend io.ktor.client.engine.mock.MockRequestHandleScope.(io.ktor.client.request.HttpRequestData) -> io.ktor.client.request.HttpResponseData,
): HttpClient = HttpClient(MockEngine { request -> handler(request) }) {
    expectSuccess = false
}

internal fun jsonHeaders() = headersOf(
    HttpHeaders.ContentType,
    ContentType.Application.Json.toString(),
)

internal class FakeApiKeyStore(
    private val keys: MutableMap<String, String> = mutableMapOf(),
) : ApiKeyStore {
    override suspend fun getApiKey(profileId: String): String? = keys[profileId]
    override suspend fun setApiKey(profileId: String, apiKey: String) {
        keys[profileId] = apiKey
    }
    override suspend fun deleteApiKey(profileId: String) {
        keys.remove(profileId)
    }
    override suspend fun hasApiKey(profileId: String): Boolean = keys.containsKey(profileId)
}

internal class FakeProfileProvider(
    var profiles: List<ProviderProfile>,
) : AiProfileProvider {
    override suspend fun getProfiles(): List<ProviderProfile> = profiles
    override suspend fun getProfile(profileId: String): ProviderProfile? =
        profiles.firstOrNull { it.id == profileId }
    override suspend fun updateProfile(profile: ProviderProfile) {
        profiles = profiles.map { if (it.id == profile.id) profile else it }
    }
}

internal class FakePreferences(
    var strategy: RoutingStrategy = RoutingStrategy.BALANCED,
    var overrideId: String? = null,
    var suffix: String? = null,
    var diagnostics: Boolean = false,
) : AiPreferencesSource {
    override suspend fun routingStrategy(): RoutingStrategy = strategy
    override suspend fun taskProfileOverride(task: AiTask): String? = overrideId
    override suspend fun promptSuffix(task: AiTask): String? = suffix
    override suspend fun diagnosticsEnabled(): Boolean = diagnostics
}

internal class FakeUsageRecorder : AiUsageRecorder {
    val entries = mutableListOf<UsageEntry>()
    override suspend fun recordUsage(entry: UsageEntry) {
        entries += entry
    }
}

internal class FakeLogRecorder : AiLogRecorder {
    val logs = mutableListOf<AiRequestLogEntry>()
    override suspend fun recordLog(entry: AiRequestLogEntry) {
        logs += entry
    }
}

internal class FakeNetworkMonitor(var online: Boolean = true) : NetworkMonitor {
    override fun isOnline(): Boolean = online
}
