package com.factswallpaper.core.ai

import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.ProviderKeyStatus
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.ProviderType
import com.factswallpaper.core.security.ApiKeyStore
import io.ktor.client.HttpClient
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import com.factswallpaper.core.ai.client.getJson
import com.factswallpaper.core.ai.client.normalizedBaseUrl
import com.factswallpaper.core.ai.client.obj
import com.factswallpaper.core.ai.client.string

data class AiModelInfo(
    val id: String,
    val displayName: String? = null,
)

data class ModelCatalogResult(
    val models: List<AiModelInfo> = emptyList(),
    val error: String? = null,
)

data class ConnectionTestResult(
    val success: Boolean,
    val latencyMs: Long,
    val message: String,
    val keyStatus: ProviderKeyStatus,
)

/**
 * Implements the "Test Connection" and "Fetch Available Models" actions from
 * spec §11.1. Model discovery is deliberately non-fatal: any failure returns a
 * message while free-text model entry stays available.
 */
interface ProviderDiagnosticsService {
    suspend fun testConnection(profile: ProviderProfile): ConnectionTestResult
    suspend fun fetchModels(
        profile: ProviderProfile,
        apiKeyOverride: String? = null,
    ): ModelCatalogResult
}

@Singleton
class DefaultProviderDiagnosticsService @Inject constructor(
    private val callService: AiCallService,
    private val profileProvider: AiProfileProvider,
    private val apiKeyStore: ApiKeyStore,
    private val httpClient: HttpClient,
    private val json: Json,
) : ProviderDiagnosticsService {

    override suspend fun testConnection(profile: ProviderProfile): ConnectionTestResult {
        val apiKey = apiKeyStore.getApiKey(profile.id)
        if (apiKey.isNullOrBlank()) {
            return ConnectionTestResult(
                success = false,
                latencyMs = 0,
                message = "No API key is configured for ${profile.name}.",
                keyStatus = ProviderKeyStatus.UNKNOWN,
            )
        }
        val request = AiRequest(
            task = AiTask.JSON_REPAIR,
            systemPrompt = "You are a connectivity probe. Reply exactly as instructed.",
            userPrompt = "Reply with the JSON {\"ok\":true}",
            temperature = 0.0,
            maxOutputTokens = 1_024,
        )
        val started = System.currentTimeMillis()
        return try {
            val outcome = callService.executeOnProfile(
                profile = profile,
                request = request,
                recordTelemetry = false,
            )
            val elapsed = System.currentTimeMillis() - started
            val hasResponse = outcome.response.text.isNotBlank() ||
                outcome.response.reasoningText.isNotBlank()
            val message = when {
                hasResponse -> "Connected in ${elapsed} ms."
                outcome.response.finishReason.equals("length", ignoreCase = true) ->
                    "The model used its whole output budget on reasoning. Raise max output tokens or disable thinking in Advanced parameters, then test again."
                else -> "The provider connected but returned no content."
            }
            ConnectionTestResult(
                success = hasResponse,
                latencyMs = elapsed,
                message = message,
                keyStatus = ProviderKeyStatus.VALID,
            )
        } catch (exception: AiException) {
            val elapsed = System.currentTimeMillis() - started
            val status = if (exception.invalidatesKey) {
                runCatching {
                    profileProvider.updateProfile(
                        profile.copy(keyStatus = ProviderKeyStatus.INVALID),
                    )
                }
                ProviderKeyStatus.INVALID
            } else {
                profile.keyStatus
            }
            ConnectionTestResult(
                success = false,
                latencyMs = elapsed,
                message = buildString {
                    append(exception.userMessage)
                    val detail = exception.message.orEmpty().take(240)
                    if (detail.isNotBlank() && !detail.equals(exception.userMessage, true)) {
                        append(" (")
                        append(detail)
                        append(')')
                    }
                },
                keyStatus = status,
            )
        }
    }

    override suspend fun fetchModels(
        profile: ProviderProfile,
        apiKeyOverride: String?,
    ): ModelCatalogResult {
        val apiKey = apiKeyOverride?.takeIf { it.isNotBlank() }
            ?: apiKeyStore.getApiKey(profile.id)
        if (apiKey.isNullOrBlank()) {
            return ModelCatalogResult(error = "Add an API key before fetching models.")
        }
        val url = modelsUrl(profile)
        val headers = modelsHeaders(profile, apiKey)
        return try {
            val body = httpClient.getJson(url, headers) ?: "{}"
            val models = parseModels(profile.providerType, body)
            if (models.isEmpty()) {
                ModelCatalogResult(error = "The provider returned no models.")
            } else {
                ModelCatalogResult(models = models.sortedBy { it.id.lowercase() })
            }
        } catch (exception: AiException) {
            ModelCatalogResult(error = exception.userMessage)
        } catch (exception: Throwable) {
            ModelCatalogResult(error = exception.message ?: "Could not reach the provider.")
        }
    }

    private fun modelsUrl(profile: ProviderProfile): String {
        val base = profile.baseUrl.normalizedBaseUrl()
        return when (profile.providerType) {
            ProviderType.GEMINI -> {
                val versioned = if (
                    base.contains("/v1beta") ||
                    base.contains("/v1")
                ) {
                    base
                } else {
                    "$base/v1beta"
                }
                "$versioned/models?pageSize=200"
            }
            ProviderType.ANTHROPIC -> "$base/models?limit=100"
            ProviderType.AZURE_OPENAI -> {
                val version = profile.apiVersion?.takeIf { it.isNotBlank() }
                    ?: "2025-01-01-preview"
                "$base/openai/models?api-version=$version"
            }
            else -> "$base/models"
        }
    }

    private fun modelsHeaders(profile: ProviderProfile, apiKey: String): Map<String, String> =
        when (profile.providerType) {
            ProviderType.GEMINI -> mapOf("x-goog-api-key" to apiKey)
            ProviderType.ANTHROPIC -> mapOf(
                "x-api-key" to apiKey,
                "anthropic-version" to "2023-06-01",
            )
            ProviderType.AZURE_OPENAI -> mapOf("api-key" to apiKey)
            else -> buildMap {
                put("Authorization", "Bearer $apiKey")
                putAll(SchemaAdapter.customHeaders(profile.customHeadersJson))
            }
        }

    private fun parseModels(type: ProviderType, body: String): List<AiModelInfo> {
        val root = runCatching { json.parseToJsonElement(body) }.getOrNull() ?: return emptyList()
        return when (type) {
            ProviderType.GEMINI -> {
                val models = (root as? JsonObject)?.get("models") as? JsonArray
                models.orEmpty().mapNotNull { element ->
                    val item = element as? JsonObject ?: return@mapNotNull null
                    val rawName = item.string("name") ?: return@mapNotNull null
                    val methods = (item["supportedGenerationMethods"] as? JsonArray)
                        ?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
                    if (!methods.isNullOrEmpty() && methods.none { it == "generateContent" }) {
                        return@mapNotNull null
                    }
                    AiModelInfo(
                        id = rawName.removePrefix("models/"),
                        displayName = item.string("displayName"),
                    )
                }
            }
            ProviderType.ANTHROPIC -> {
                val data = (root as? JsonObject)?.get("data") as? JsonArray
                data.orEmpty().mapNotNull { element ->
                    val item = element as? JsonObject ?: return@mapNotNull null
                    val id = item.string("id") ?: return@mapNotNull null
                    AiModelInfo(id = id, displayName = item.string("display_name"))
                }
            }
            else -> {
                val data = when (root) {
                    is JsonArray -> root
                    is JsonObject -> (root["data"] as? JsonArray)
                        ?: (root["models"] as? JsonArray)
                        ?: (root["value"] as? JsonArray)
                    else -> null
                }
                data.orEmpty().mapNotNull { element ->
                    when (element) {
                        is JsonObject -> {
                            val id = element.string("id")
                                ?: element.string("name")
                                ?: return@mapNotNull null
                            AiModelInfo(
                                id = id.removePrefix("models/"),
                                displayName = element.string("display_name"),
                            )
                        }
                        is JsonPrimitive -> element.contentOrNull?.let { AiModelInfo(id = it) }
                        else -> null
                    }
                }
            }
        }
    }
}
