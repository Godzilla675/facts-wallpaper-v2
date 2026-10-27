package com.factswallpaper.core.ai

import com.factswallpaper.core.model.AiRequestLogEntry
import com.factswallpaper.core.model.ProviderKeyStatus
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.UsageEntry
import com.factswallpaper.core.security.ApiKeyStore
import com.factswallpaper.core.security.LogRedactor
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlin.system.measureTimeMillis

data class AiCallOutcome(
    val response: AiResponse,
    val profile: ProviderProfile,
    val latencyMs: Long,
    val usedFailover: Boolean,
)

/**
 * Routes a request, executes it with retries, fails over once when the strategy
 * allows it, and records the usage ledger + redacted request log. This is the
 * only place that touches provider API keys during generation.
 */
@Singleton
class AiCallService @Inject constructor(
    private val profileProvider: AiProfileProvider,
    private val apiKeyStore: ApiKeyStore,
    private val preferences: AiPreferencesSource,
    private val clientFactory: AiClientFactory,
    private val retryPolicy: AiRetryPolicy,
    private val redactor: LogRedactor,
    private val usageRecorder: AiUsageRecorder,
    private val logRecorder: AiLogRecorder,
    private val networkMonitor: NetworkMonitor,
) {

    suspend fun execute(
        request: AiRequest,
        documentId: String?,
        requireDirectPdf: Boolean = false,
        pdfPageCount: Int? = null,
        manualProfileId: String? = null,
        repairTriggered: Boolean = false,
    ): AiCallOutcome {
        if (!networkMonitor.isOnline()) {
            throw AiException(
                AiErrorKind.NETWORK,
                "Offline — AI features need internet; your wallpaper keeps working.",
            )
        }
        val effectiveRequest = withPromptSuffix(request)
        val candidates = candidatesFor(
            request = effectiveRequest,
            requireDirectPdf = requireDirectPdf,
            manualProfileId = manualProfileId,
        ).filter { profile -> fitsDirectPdfLimits(profile, effectiveRequest, pdfPageCount) }
        if (candidates.isEmpty()) {
            throw AiException(
                AiErrorKind.INVALID_REQUEST,
                "No provider profile satisfies the direct-PDF size/page limits.",
            )
        }
        val strategy = preferences.routingStrategy()
        val maxAttempts = if (strategy == com.factswallpaper.core.model.RoutingStrategy.MANUAL) 1 else 2
        var last: AiException? = null
        candidates.take(maxAttempts).forEachIndexed { index, profile ->
            try {
                return executeOnProfile(
                    profile = profile,
                    request = effectiveRequest,
                    documentId = documentId,
                    repairTriggered = repairTriggered,
                    usedFailover = index > 0,
                )
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: AiException) {
                last = exception
                if (exception.invalidatesKey) {
                    markKeyStatus(profile, ProviderKeyStatus.INVALID)
                }
                recordFailure(profile, effectiveRequest, documentId, exception, repairTriggered)
                if (strategy == com.factswallpaper.core.model.RoutingStrategy.MANUAL) throw exception
            }
        }
        throw last ?: AiException(AiErrorKind.UNKNOWN, "No AI provider attempt was made.")
    }

    /** Pre-selects the profile the router would use without touching the ledger. */
    suspend fun resolveProfile(
        request: AiRequest,
        requireDirectPdf: Boolean = false,
        manualProfileId: String? = null,
    ): ProviderProfile = candidatesFor(
        request = withPromptSuffix(request),
        requireDirectPdf = requireDirectPdf,
        manualProfileId = manualProfileId,
    ).first()

    private suspend fun candidatesFor(
        request: AiRequest,
        requireDirectPdf: Boolean,
        manualProfileId: String?,
    ): List<ProviderProfile> {
        val profiles = profileProvider.getProfiles()
        val configuredIds = apiKeyStore.configuredProfileIds(profiles.map { it.id })
        val strategy = preferences.routingStrategy()
        val overrideId = manualProfileId ?: preferences.taskProfileOverride(request.task)
        return AiRouter.rankProfiles(
            profiles = profiles,
            configuredProfileIdsWithKeys = configuredIds,
            task = request.task,
            requireDirectPdf = requireDirectPdf,
            strategy = strategy,
            manualOverrideId = overrideId,
        )
    }

    private suspend fun withPromptSuffix(request: AiRequest): AiRequest {
        val suffix = preferences.promptSuffix(request.task)
        return if (suffix.isNullOrBlank()) {
            request
        } else {
            request.copy(
                systemPrompt = request.systemPrompt +
                    "\n\nAdditional user instruction: " + suffix.trim(),
            )
        }
    }

    /** Connection tests and explicit profile actions bypass routing and the ledger. */
    suspend fun executeOnProfile(
        profile: ProviderProfile,
        request: AiRequest,
        documentId: String? = null,
        repairTriggered: Boolean = false,
        usedFailover: Boolean = false,
        recordTelemetry: Boolean = true,
    ): AiCallOutcome {
        val apiKey = apiKeyStore.getApiKey(profile.id)
            ?: throw AiException(
                AiErrorKind.AUTHENTICATION,
                "No API key is configured for ${profile.name}.",
            )
        val effectiveRequest = request.copy(
            maxOutputTokens = request.maxOutputTokens
                .coerceAtLeast(MIN_OUTPUT_TOKENS)
                .coerceAtMost(profile.outputTokenLimit()),
        )
        redactor.register(apiKey)
        var currentProfile = profile
        val client = clientFactory.create(
            profile = currentProfile,
            apiKey = apiKey,
            onTemperatureUnsupported = {
                currentProfile = currentProfile.copy(supportsTemperature = false)
                runCatching { profileProvider.updateProfile(currentProfile) }
            },
            onResponseFormatUnsupported = {
                currentProfile = currentProfile.copy(
                    supportsJsonSchema = false,
                    supportsJsonMode = false,
                )
                runCatching { profileProvider.updateProfile(currentProfile) }
            },
        )
        var response: AiResponse? = null
        val elapsed = measureTimeMillis {
            response = retryPolicy.run { client.execute(effectiveRequest) }
        }
        val result = response ?: throw AiException(AiErrorKind.UNKNOWN, "Provider returned no response.")
        markKeyStatus(currentProfile, ProviderKeyStatus.VALID)
        if (recordTelemetry) {
            recordSuccess(
                profile = currentProfile,
                request = effectiveRequest,
                response = result,
                documentId = documentId,
                latencyMs = elapsed,
                repairTriggered = repairTriggered,
            )
        }
        return AiCallOutcome(
            response = result,
            profile = currentProfile,
            latencyMs = elapsed,
            usedFailover = usedFailover,
        )
    }

    /**
     * Records a locally detected validation failure (bad JSON after the single
     * repair pass) so it appears in the prompt inspector even though the HTTP
     * call itself succeeded.
     */
    suspend fun recordContentFailure(
        task: com.factswallpaper.core.model.AiTask,
        profile: ProviderProfile,
        rawResponse: String,
        message: String,
        repairTriggered: Boolean = true,
    ) {
        if (!preferences.diagnosticsEnabled()) return
        runCatching {
            logRecorder.recordLog(
                AiRequestLogEntry(
                    id = UUID.randomUUID().toString(),
                    taskType = task,
                    providerProfileId = profile.id,
                    modelName = profile.modelName,
                    httpStatusCode = null,
                    durationMs = 0,
                    inputTokens = 0,
                    outputTokens = 0,
                    repairTriggered = repairTriggered,
                    success = false,
                    redactedRequestSummary = "",
                    redactedResponseSnippet = redactor.redactSnippet(rawResponse, 400),
                    errorMessage = redactor.redactSnippet(message, 400),
                    timestampEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun markKeyStatus(profile: ProviderProfile, status: ProviderKeyStatus) {
        if (profile.keyStatus == status) return
        runCatching {
            profileProvider.updateProfile(profile.copy(keyStatus = status))
        }
    }

    private fun fitsDirectPdfLimits(
        profile: ProviderProfile,
        request: AiRequest,
        pdfPageCount: Int?,
    ): Boolean {
        val bytes = request.pdfBytes ?: return true
        if (bytes.size.toLong() > profile.maxInlinePdfBytes) return false
        if (pdfPageCount != null && pdfPageCount > profile.maxDirectPdfPages) return false
        return true
    }

    private suspend fun recordSuccess(
        profile: ProviderProfile,
        request: AiRequest,
        response: AiResponse,
        documentId: String?,
        latencyMs: Long,
        repairTriggered: Boolean,
    ) {
        val cost = CostEstimator.estimateCostUsd(
            profile = profile,
            inputTokens = response.inputTokens,
            outputTokens = response.outputTokens,
        )
        runCatching {
            usageRecorder.recordUsage(
                UsageEntry(
                    id = UUID.randomUUID().toString(),
                    providerProfileId = profile.id,
                    providerType = profile.providerType,
                    modelName = response.model,
                    documentId = documentId,
                    taskType = request.task,
                    inputTokens = response.inputTokens,
                    outputTokens = response.outputTokens,
                    estimatedCostUsd = cost,
                    timestampEpochMs = System.currentTimeMillis(),
                ),
            )
        }
        if (!preferences.diagnosticsEnabled()) return
        runCatching {
            logRecorder.recordLog(
                AiRequestLogEntry(
                    id = UUID.randomUUID().toString(),
                    taskType = request.task,
                    providerProfileId = profile.id,
                    modelName = response.model,
                    httpStatusCode = 200,
                    durationMs = latencyMs,
                    inputTokens = response.inputTokens,
                    outputTokens = response.outputTokens,
                    repairTriggered = repairTriggered,
                    success = true,
                    redactedRequestSummary = redactor.redactSnippet(
                        request.systemPrompt + "\n" + request.userPrompt,
                        400,
                    ),
                    redactedResponseSnippet = redactor.redactSnippet(response.text, 400),
                    errorMessage = null,
                    timestampEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    private suspend fun recordFailure(
        profile: ProviderProfile,
        request: AiRequest,
        documentId: String?,
        exception: AiException,
        repairTriggered: Boolean,
    ) {
        if (!preferences.diagnosticsEnabled()) return
        runCatching {
            logRecorder.recordLog(
                AiRequestLogEntry(
                    id = UUID.randomUUID().toString(),
                    taskType = request.task,
                    providerProfileId = profile.id,
                    modelName = profile.modelName,
                    httpStatusCode = exception.statusCode,
                    durationMs = 0,
                    inputTokens = 0,
                    outputTokens = 0,
                    repairTriggered = repairTriggered,
                    success = false,
                    redactedRequestSummary = redactor.redactSnippet(
                        request.systemPrompt + "\n" + request.userPrompt,
                        400,
                    ),
                    redactedResponseSnippet = "",
                    errorMessage = redactor.redactSnippet(exception.message, 400),
                    timestampEpochMs = System.currentTimeMillis(),
                ),
            )
        }

    }
}
