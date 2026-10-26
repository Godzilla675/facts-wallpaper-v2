package com.factswallpaper.core.ai

import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.RoutingStrategy

/**
 * Capability-aware router implementing the exact scoring formula in spec §6.4.
 * The router never sees API key material: callers pass the ids that have keys.
 */
object AiRouter {

    fun eligibleProfiles(
        profiles: List<ProviderProfile>,
        configuredProfileIdsWithKeys: Set<String>,
        task: AiTask,
        requireDirectPdf: Boolean,
    ): List<ProviderProfile> = profiles.filter { profile ->
        profile.enabled &&
            profile.id in configuredProfileIdsWithKeys &&
            (!requireDirectPdf || profile.supportsPdfInput) &&
            (profile.supportsJsonSchema || profile.supportsJsonMode)
    }

    /**
     * Returns candidates best-first. For MANUAL the pinned profile is the only
     * candidate; other strategies allow one failover candidate per §11.5.
     */
    fun rankProfiles(
        profiles: List<ProviderProfile>,
        configuredProfileIdsWithKeys: Set<String>,
        task: AiTask,
        requireDirectPdf: Boolean,
        strategy: RoutingStrategy,
        manualOverrideId: String? = null,
    ): List<ProviderProfile> {
        val eligible = eligibleProfiles(profiles, configuredProfileIdsWithKeys, task, requireDirectPdf)
        if (eligible.isEmpty()) {
            throw AiException(
                kind = AiErrorKind.INVALID_REQUEST,
                message = "No enabled AI provider profile with an API key satisfies requirements " +
                    "(requireDirectPdf=$requireDirectPdf).",
            )
        }

        if (strategy == RoutingStrategy.MANUAL) {
            val pinned = manualOverrideId?.let { id -> eligible.firstOrNull { it.id == id } }
                ?: throw AiException(
                    AiErrorKind.INVALID_REQUEST,
                    "Manual routing is selected but no pinned profile with a key satisfies this task.",
                )
            return listOf(pinned)
        }

        val ranked = when (strategy) {
            RoutingStrategy.CHEAPEST -> eligible.sortedBy {
                it.inputUsdPerMillion + (it.outputUsdPerMillion * 0.5)
            }
            RoutingStrategy.HIGHEST_QUALITY -> eligible.sortedWith(
                compareByDescending<ProviderProfile> { it.qualityTier }
                    .thenByDescending { it.supportsJsonSchema }
                    .thenByDescending { it.maxContextTokens },
            )
            RoutingStrategy.BALANCED, RoutingStrategy.MANUAL -> eligible.sortedByDescending { profile ->
                val qualityScore = profile.qualityTier * 30.0
                val speedScore = profile.speedTier * 20.0
                val schemaBonus = if (profile.supportsJsonSchema) 25.0 else 0.0
                val contextBonus = (profile.maxContextTokens.coerceAtMost(1_000_000) / 100_000.0) * 2.5
                val blendedCost = profile.inputUsdPerMillion + (profile.outputUsdPerMillion * 0.5)
                val costPenalty = blendedCost * 8.0
                qualityScore + speedScore + schemaBonus + contextBonus - costPenalty
            }
        }
        return ranked
    }

    fun selectProfile(
        profiles: List<ProviderProfile>,
        configuredProfileIdsWithKeys: Set<String>,
        task: AiTask,
        requireDirectPdf: Boolean,
        strategy: RoutingStrategy,
        manualOverrideId: String? = null,
    ): ProviderProfile = rankProfiles(
        profiles = profiles,
        configuredProfileIdsWithKeys = configuredProfileIdsWithKeys,
        task = task,
        requireDirectPdf = requireDirectPdf,
        strategy = strategy,
        manualOverrideId = manualOverrideId,
    ).first()
}
