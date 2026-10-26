package com.factswallpaper.core.render.verify

import com.factswallpaper.core.ai.StructuredAiService
import com.factswallpaper.core.render.diagram.MarkupKind
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tier-2 AI formula/diagram verifier backed by [StructuredAiService] (spec §6.6 Task 4, F8).
 * Evaluates whether formulas/diagrams are syntactically sound and renderable before they enter rotation.
 */
@Singleton
class StructuredAiFormulaVerifier @Inject constructor(
    private val structuredAiService: StructuredAiService,
) : FormulaAiVerifier {

    override suspend fun verify(kind: MarkupKind, source: String): FormulaAiVerdict? {
        val validation = try {
            structuredAiService.verifyFormula(source = source)
        } catch (_: Throwable) {
            // When AI is unreachable, unconfigured, or failing, return null so verification
            // outcome stays PendingAi rather than failing prematurely or falsely passing.
            return null
        }
        val envelope = validation.value ?: return null
        return FormulaAiVerdict(
            valid = envelope.valid,
            reason = envelope.reason,
        )
    }
}
