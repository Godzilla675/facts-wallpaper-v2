package com.factswallpaper.core.render.verify

import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.VerificationStatus
import com.factswallpaper.core.render.diagram.DiagramValidator
import com.factswallpaper.core.render.diagram.MarkupKind
import com.factswallpaper.core.render.diagram.StageMarkup
import javax.inject.Inject
import javax.inject.Singleton

/** Result returned by a Tier-2 formula verifier. */
data class FormulaAiVerdict(
    val valid: Boolean,
    val reason: String? = null,
)

/**
 * Tier-2 (AI) formula/diagram verifier from spec §6.6 Task 4.
 *
 * Implemented in `:core:ai` (P3) and contributed through a Hilt multibinding. The render
 * module only owns the port so Tier 2 can stay optional until a provider is configured.
 * A provider *must not* return `valid = true` without actually checking the markup.
 */
interface FormulaAiVerifier {
    suspend fun verify(kind: MarkupKind, source: String): FormulaAiVerdict?
}

sealed interface FormulaVerificationOutcome {
    /** The fact carries no formula or diagram markup. */
    data object NotApplicable : FormulaVerificationOutcome

    /** Tier 1 passed, but Tier 2 is disabled or no AI verifier is configured. */
    data object PendingAi : FormulaVerificationOutcome

    data object Verified : FormulaVerificationOutcome

    data class Failed(val reason: String) : FormulaVerificationOutcome
}

/**
 * Two-tier verification from F8:
 *  1. [DiagramValidator] always runs locally and rejects dangerous or structurally broken
 *     markup.
 *  2. Every registered [FormulaAiVerifier] runs only when the owning document opted into
 *     `autoVerifyFormulas`. A failure or a negative verdict fails the item; a missing or
 *     erroring verifier leaves it pending rather than claiming a false VERIFIED state.
 */
@Singleton
class FormulaVerificationService @Inject constructor(
    private val verifiers: @JvmSuppressWildcards Set<FormulaAiVerifier>,
    private val documentRepository: DocumentRepository,
    private val factRepository: FactRepository,
) {

    suspend fun verify(fact: Fact): FormulaVerificationOutcome {
        val stage = StageMarkup.of(fact) ?: return FormulaVerificationOutcome.NotApplicable
        val documentSettings = documentRepository.getSettings(fact.documentId)
        val autoVerify = documentSettings?.autoVerifyFormulas ?: true
        return verify(stage.kind, stage.source, autoVerify)
    }

    suspend fun verify(
        kind: MarkupKind,
        source: String,
        autoVerify: Boolean,
    ): FormulaVerificationOutcome {
        when (val structural = DiagramValidator.validate(kind, source)) {
            is DiagramValidator.Result.Invalid ->
                return FormulaVerificationOutcome.Failed(structural.reason)
            DiagramValidator.Result.Valid -> Unit
        }
        if (!autoVerify) return FormulaVerificationOutcome.PendingAi
        if (verifiers.isEmpty()) return FormulaVerificationOutcome.PendingAi

        var pending = false
        for (verifier in verifiers) {
            val verdict = try {
                verifier.verify(kind, source)
            } catch (_: Throwable) {
                pending = true
                continue
            }
            if (verdict == null) {
                pending = true
                continue
            }
            if (!verdict.valid) {
                return FormulaVerificationOutcome.Failed(
                    verdict.reason?.takeIf { it.isNotBlank() } ?: "AI verification rejected the markup",
                )
            }
        }
        return if (pending) FormulaVerificationOutcome.PendingAi else FormulaVerificationOutcome.Verified
    }

    /**
     * Runs both tiers and persists the resulting [VerificationStatus]. Pending items keep
     * their previous status (an already-verified fact is not demoted by a transient outage).
     */
    suspend fun verifyAndPersist(fact: Fact): FormulaVerificationOutcome {
        val outcome = verify(fact)
        val updatedStatus = when (outcome) {
            FormulaVerificationOutcome.NotApplicable -> fact.verificationStatus
            FormulaVerificationOutcome.PendingAi -> fact.verificationStatus
            FormulaVerificationOutcome.Verified -> VerificationStatus.VERIFIED
            is FormulaVerificationOutcome.Failed -> VerificationStatus.FAILED
        }
        if (updatedStatus != fact.verificationStatus) {
            factRepository.saveFact(
                fact.copy(
                    verificationStatus = updatedStatus,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
        return outcome
    }
}
