package com.factswallpaper.core.ai

import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.FactDensity
import com.factswallpaper.core.model.ProviderProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

/**
 * Performs the single allowed `AiTask.JSON_REPAIR` pass from spec ?6.6/?F7.
 * The caller owns validation so the repaired payload is checked by the same
 * provider-independent [SchemaValidator] as the original response.
 */
@Singleton
class JsonRepairService @Inject constructor(
    private val callService: AiCallService,
) {

    suspend fun repair(
        profile: ProviderProfile,
        canonicalSchema: String,
        malformed: String,
        maxOutputTokens: Int = 4096,
    ): AiCallOutcome = callService.executeOnProfile(
        profile = profile,
        request = AiRequest(
            task = AiTask.JSON_REPAIR,
            systemPrompt = AiPrompts.JsonRepairSystemPrompt,
            userPrompt = AiPrompts.repairUserPrompt(canonicalSchema, malformed),
            jsonSchema = canonicalSchema,
            temperature = 0.0,
            maxOutputTokens = maxOutputTokens.coerceAtLeast(512),
        ),
        repairTriggered = true,
    )
}

data class AiCostEstimate(
    val profile: ProviderProfile,
    val estimatedInputTokens: Int,
    val estimatedOutputTokens: Int,
    val estimatedCostUsd: Double,
)

data class OutlineGenerationResult(
    val lessons: List<OutlineDraft>,
    val warnings: List<String>,
    val profile: ProviderProfile,
    val usedFailover: Boolean,
    val estimatedCostUsd: Double,
    val actualCostUsd: Double,
)

data class FactsGenerationResult(
    val facts: List<FactDraft>,
    val warnings: List<String>,
    val profile: ProviderProfile,
    val usedFailover: Boolean,
    val estimatedCostUsd: Double,
    val actualCostUsd: Double,
    val repairUsed: Boolean,
)

data class DiagramGenerationResult(
    val diagram: DiagramEnvelope,
    val profile: ProviderProfile,
    val actualCostUsd: Double,
)

data class TemplateGenerationResult(
    val templateJson: String,
    val profile: ProviderProfile,
    val actualCostUsd: Double,
)

/**
 * High-level structured generation pipeline for outline and facts tasks.
 * It resolves the router's chosen profile up front so chunk sizing matches the
 * model that will actually run, chunks text on page/paragraph boundaries,
 * validates every response locally, performs at most one repair pass, and
 * deduplicates facts across chunks.
 */
interface StructuredAiService {
    suspend fun generateOutline(
        documentId: String?,
        pages: List<PageText>,
        pdfBytes: ByteArray? = null,
        pdfFileName: String? = null,
        pdfPageCount: Int? = null,
        manualProfileId: String? = null,
        progress: ((completed: Int, total: Int) -> Unit)? = null,
    ): OutlineGenerationResult

    suspend fun generateFacts(
        documentId: String?,
        lessonTitle: String,
        pages: List<PageText>,
        documentPageCount: Int,
        density: FactDensity,
        includeFormulas: Boolean,
        includeDiagrams: Boolean,
        manualProfileId: String? = null,
        existingFactTexts: List<String> = emptyList(),
        extraInstruction: String? = null,
        progress: ((completed: Int, total: Int) -> Unit)? = null,
    ): FactsGenerationResult

    suspend fun estimateOutline(
        pages: List<PageText>,
        pdfPageCount: Int? = null,
        manualProfileId: String? = null,
    ): AiCostEstimate

    suspend fun estimateFacts(
        lessonTitle: String,
        pages: List<PageText>,
        density: FactDensity,
        includeFormulas: Boolean,
        includeDiagrams: Boolean,
        manualProfileId: String? = null,
    ): AiCostEstimate

    suspend fun verifyFormula(
        source: String,
        manualProfileId: String? = null,
    ): ValidationResult<VerificationEnvelope>

    suspend fun generateDiagram(
        description: String,
        kindHint: String? = null,
        manualProfileId: String? = null,
    ): DiagramGenerationResult

    suspend fun generateTemplate(
        description: String,
        manualProfileId: String? = null,
    ): TemplateGenerationResult

    suspend fun regenerateFact(
        originalText: String,
        pageContent: String? = null,
        lessonTitle: String? = null,
        extraInstruction: String? = null,
        manualProfileId: String? = null,
    ): FactDraft
}

fun StructuredAiService(
    callService: AiCallService,
    validator: SchemaValidator,
    repairService: JsonRepairService,
): StructuredAiService = DefaultStructuredAiService(callService, validator, repairService)

@Singleton
class DefaultStructuredAiService @Inject constructor(
    private val callService: AiCallService,
    private val validator: SchemaValidator,
    private val repairService: JsonRepairService,
) : StructuredAiService {

    override suspend fun generateOutline(
        documentId: String?,
        pages: List<PageText>,
        pdfBytes: ByteArray?,
        pdfFileName: String?,
        pdfPageCount: Int?,
        manualProfileId: String?,
        progress: ((completed: Int, total: Int) -> Unit)?,
    ): OutlineGenerationResult {
        val pageCount = pdfPageCount ?: pages.maxOfOrNull { it.pageNumber } ?: 0
        val baseRequest = outlineRequest(
            userPrompt = "",
            pdfBytes = pdfBytes,
            pdfFileName = pdfFileName,
        )
        val profile = callService.resolveProfile(
            request = baseRequest,
            requireDirectPdf = pdfBytes != null,
            manualProfileId = manualProfileId,
        )
        val directPdf = pdfBytes != null && profile.supportsPdfInput
        val chunks: List<TextChunk?> = if (directPdf) {
            listOf(null)
        } else {
            TextChunker.chunk(pages, profile.maxContextTokens)
        }
        if (chunks.isEmpty()) {
            throw AiException(
                AiErrorKind.INVALID_REQUEST,
                "There is no extracted text to outline. Run OCR or choose a text-based PDF.",
            )
        }

        val estimate = estimateOutlineCostUsd(
            profile = profile,
            pages = pages,
            pdfPageCount = if (directPdf) pageCount else null,
        )
        val warnings = mutableListOf<String>()
        val lessons = mutableListOf<OutlineDraft>()
        var actualCost = 0.0
        var usedFailover = false
        var validChunks = 0

        chunks.forEachIndexed { index, chunk ->
            val request = if (directPdf) {
                baseRequest.copy(
                    userPrompt = "Partition the attached PDF into lessons. " +
                        "Use exact 1-indexed PDF page numbers.\n" +
                        "Return JSON with a \"lessons\" array.",
                )
            } else {
                outlineRequest(
                    userPrompt = AiPrompts.outlineUserPromptFromText(chunk!!.text),
                )
            }
            val outcome = callService.execute(
                request = request,
                documentId = documentId,
                requireDirectPdf = directPdf,
                pdfPageCount = pageCount,
                manualProfileId = manualProfileId,
            )
            usedFailover = usedFailover || outcome.usedFailover
            actualCost += outcomeCost(outcome)

            var validation = validator.validateOutline(outcome.response.text, pageCount)
            if (!validation.isValid) {
                val repaired = runCatching {
                    repairService.repair(
                        profile = outcome.profile,
                        canonicalSchema = AiSchemas.OutlineJsonSchema,
                        malformed = outcome.response.text,
                    )
                }.getOrNull()
                if (repaired != null) {
                    actualCost += outcomeCost(repaired)
                    validation = validator.validateOutline(repaired.response.text, pageCount)
                }
            }

            val chunkLessons = validation.value
            if (chunkLessons == null) {
                warnings += "Chunk ${index + 1}: ${validation.errors.joinToString("; ").take(400)}"
                callService.recordContentFailure(
                    task = AiTask.OUTLINE,
                    profile = outcome.profile,
                    rawResponse = outcome.response.text,
                    message = validation.errors.joinToString("; "),
                )
            } else {
                validChunks++
                warnings += validation.errors.map { "Chunk ${index + 1}: $it" }
                lessons += chunkLessons
            }
            progress?.invoke(index + 1, chunks.size)
        }

        if (validChunks == 0) {
            throw AiException(
                AiErrorKind.CONTENT,
                "The provider returned no usable lessons after one repair attempt.",
            )
        }
        return OutlineGenerationResult(
            lessons = mergeOutline(lessons),
            warnings = warnings.distinct().take(20),
            profile = profile,
            usedFailover = usedFailover,
            estimatedCostUsd = estimate.estimatedCostUsd,
            actualCostUsd = actualCost,
        )
    }

    override suspend fun generateFacts(
        documentId: String?,
        lessonTitle: String,
        pages: List<PageText>,
        documentPageCount: Int,
        density: FactDensity,
        includeFormulas: Boolean,
        includeDiagrams: Boolean,
        manualProfileId: String?,
        existingFactTexts: List<String>,
        extraInstruction: String?,
        progress: ((completed: Int, total: Int) -> Unit)?,
    ): FactsGenerationResult {
        if (pages.isEmpty() || pages.all { it.text.isBlank() }) {
            throw AiException(
                AiErrorKind.INVALID_REQUEST,
                "No extracted text is available for this lesson. Run OCR first.",
            )
        }
        val baseRequest = factsRequest(
            lessonTitle = lessonTitle,
            material = "",
            density = density,
            includeFormulas = includeFormulas,
            includeDiagrams = includeDiagrams,
            extraInstruction = extraInstruction,
        )
        val profile = callService.resolveProfile(
            request = baseRequest,
            requireDirectPdf = false,
            manualProfileId = manualProfileId,
        )
        val chunks = TextChunker.chunk(pages, profile.maxContextTokens)
        val estimate = estimateFactsCostUsd(
            profile = profile,
            lessonTitle = lessonTitle,
            pages = pages,
            density = density,
            includeFormulas = includeFormulas,
            includeDiagrams = includeDiagrams,
        )
        val warnings = mutableListOf<String>()
        val facts = mutableListOf<FactDraft>()
        var actualCost = 0.0
        var usedFailover = false
        var repairUsed = false
        var validChunks = 0

        chunks.forEachIndexed { index, chunk ->
            val request = factsRequest(
                lessonTitle = lessonTitle,
                material = chunk.text,
                density = density,
                includeFormulas = includeFormulas,
                includeDiagrams = includeDiagrams,
                extraInstruction = extraInstruction,
            )
            val outcome = callService.execute(
                request = request,
                documentId = documentId,
                pdfPageCount = documentPageCount,
                manualProfileId = manualProfileId,
            )
            usedFailover = usedFailover || outcome.usedFailover
            actualCost += outcomeCost(outcome)

            var validation = validator.validateFacts(
                raw = outcome.response.text,
                pageCount = documentPageCount,
                maxFacts = density.maxFactsPerLesson * chunks.size,
            )
            if (!validation.isValid) {
                repairUsed = true
                val repaired = runCatching {
                    repairService.repair(
                        profile = outcome.profile,
                        canonicalSchema = AiSchemas.FactsJsonSchema,
                        malformed = outcome.response.text,
                    )
                }.getOrNull()
                if (repaired != null) {
                    actualCost += outcomeCost(repaired)
                    validation = validator.validateFacts(
                        raw = repaired.response.text,
                        pageCount = documentPageCount,
                        maxFacts = density.maxFactsPerLesson * chunks.size,
                    )
                }
            }

            val chunkFacts = validation.value
            if (chunkFacts == null) {
                warnings += "Chunk ${index + 1}: ${validation.errors.joinToString("; ").take(400)}"
                callService.recordContentFailure(
                    task = AiTask.FACTS,
                    profile = outcome.profile,
                    rawResponse = outcome.response.text,
                    message = validation.errors.joinToString("; "),
                )
            } else {
                validChunks++
                warnings += validation.errors.map { "Chunk ${index + 1}: $it" }
                facts += chunkFacts
            }
            progress?.invoke(index + 1, chunks.size)
        }

        if (validChunks == 0) {
            throw AiException(
                AiErrorKind.CONTENT,
                "The provider returned no usable facts after one repair attempt.",
            )
        }
        val deduplicated = TextChunker.deduplicate(
            facts = facts,
            threshold = 0.82,
            existing = existingFactTexts,
        )
        return FactsGenerationResult(
            facts = deduplicated,
            warnings = warnings.distinct().take(20),
            profile = profile,
            usedFailover = usedFailover,
            estimatedCostUsd = estimate.estimatedCostUsd,
            actualCostUsd = actualCost,
            repairUsed = repairUsed,
        )
    }

    override suspend fun estimateOutline(
        pages: List<PageText>,
        pdfPageCount: Int?,
        manualProfileId: String?,
    ): AiCostEstimate {
        val baseRequest = outlineRequest(userPrompt = "", pdfBytes = null, pdfFileName = null)
        val profile = callService.resolveProfile(
            request = baseRequest,
            requireDirectPdf = false,
            manualProfileId = manualProfileId,
        )
        return estimateOutlineCostUsd(
            profile = profile,
            pages = pages,
            pdfPageCount = pdfPageCount,
        )
    }

    override suspend fun estimateFacts(
        lessonTitle: String,
        pages: List<PageText>,
        density: FactDensity,
        includeFormulas: Boolean,
        includeDiagrams: Boolean,
        manualProfileId: String?,
    ): AiCostEstimate {
        val baseRequest = factsRequest(
            lessonTitle = lessonTitle,
            material = "",
            density = density,
            includeFormulas = includeFormulas,
            includeDiagrams = includeDiagrams,
        )
        val profile = callService.resolveProfile(
            request = baseRequest,
            requireDirectPdf = false,
            manualProfileId = manualProfileId,
        )
        return estimateFactsCostUsd(
            profile = profile,
            lessonTitle = lessonTitle,
            pages = pages,
            density = density,
            includeFormulas = includeFormulas,
            includeDiagrams = includeDiagrams,
        )
    }

    override suspend fun verifyFormula(
        source: String,
        manualProfileId: String?,
    ): ValidationResult<VerificationEnvelope> {
        val request = AiRequest(
            task = AiTask.FORMULA_VERIFICATION,
            systemPrompt = AiPrompts.FormulaVerificationSystemPrompt,
            userPrompt = AiPrompts.formulaVerificationUserPrompt(source, "LATEX_OR_DIAGRAM"),
            jsonSchema = AiSchemas.FormulaVerificationJsonSchema,
            temperature = 0.0,
            maxOutputTokens = 512,
        )
        val outcome = callService.execute(
            request = request,
            documentId = null,
            manualProfileId = manualProfileId,
        )
        var validation = validator.validateVerification(outcome.response.text)
        if (!validation.isValid) {
            val repaired = runCatching {
                repairService.repair(
                    profile = outcome.profile,
                    canonicalSchema = AiSchemas.FormulaVerificationJsonSchema,
                    malformed = outcome.response.text,
                    maxOutputTokens = 512,
                )
            }.getOrNull()
            if (repaired != null) {
                validation = validator.validateVerification(repaired.response.text)
            }
        }
        return validation
    }

    override suspend fun generateDiagram(
        description: String,
        kindHint: String?,
        manualProfileId: String?,
    ): DiagramGenerationResult {
        val request = AiRequest(
            task = AiTask.DIAGRAM_GENERATION,
            systemPrompt = AiPrompts.DiagramSystemPrompt,
            userPrompt = AiPrompts.diagramUserPrompt(description, kindHint),
            jsonSchema = AiSchemas.DiagramJsonSchema,
            temperature = 0.3,
            maxOutputTokens = 2048,
        )
        val outcome = callService.execute(
            request = request,
            documentId = null,
            manualProfileId = manualProfileId,
        )
        var validation = validator.validateDiagram(outcome.response.text)
        var cost = outcomeCost(outcome)
        if (!validation.isValid) {
            val repaired = runCatching {
                repairService.repair(
                    profile = outcome.profile,
                    canonicalSchema = AiSchemas.DiagramJsonSchema,
                    malformed = outcome.response.text,
                    maxOutputTokens = 2048,
                )
            }.getOrNull()
            if (repaired != null) {
                cost += outcomeCost(repaired)
                validation = validator.validateDiagram(repaired.response.text)
            }
        }
        val diagram = validation.value ?: throw AiException(
            AiErrorKind.CONTENT,
            "The provider returned no usable diagram: ${validation.errors.joinToString("; ")}",
        )
        return DiagramGenerationResult(
            diagram = diagram,
            profile = outcome.profile,
            actualCostUsd = cost,
        )
    }

    override suspend fun generateTemplate(
        description: String,
        manualProfileId: String?,
    ): TemplateGenerationResult {
        val request = AiRequest(
            task = AiTask.TEMPLATE_GENERATION,
            systemPrompt = AiPrompts.TemplateSystemPrompt,
            userPrompt = AiPrompts.templateUserPrompt(description),
            jsonSchema = AiSchemas.TemplateJsonSchema,
            temperature = 0.4,
            maxOutputTokens = 2048,
        )
        val outcome = callService.execute(
            request = request,
            documentId = null,
            manualProfileId = manualProfileId,
        )
        var validation = validator.validateTemplate(outcome.response.text)
        var cost = outcomeCost(outcome)
        if (!validation.isValid) {
            val repaired = runCatching {
                repairService.repair(
                    profile = outcome.profile,
                    canonicalSchema = AiSchemas.TemplateJsonSchema,
                    malformed = outcome.response.text,
                    maxOutputTokens = 2048,
                )
            }.getOrNull()
            if (repaired != null) {
                cost += outcomeCost(repaired)
                validation = validator.validateTemplate(repaired.response.text)
            }
        }
        val template = validation.value ?: throw AiException(
            AiErrorKind.CONTENT,
            "The provider returned no usable template: ${validation.errors.joinToString("; ")}",
        )
        return TemplateGenerationResult(
            templateJson = template,
            profile = outcome.profile,
            actualCostUsd = cost,
        )
    }

    override suspend fun regenerateFact(
        originalText: String,
        pageContent: String?,
        lessonTitle: String?,
        extraInstruction: String?,
        manualProfileId: String?,
    ): FactDraft {
        val userPrompt = buildString {
            if (!pageContent.isNullOrBlank()) {
                appendLine("Source context:")
                appendLine(pageContent.take(8_000))
                appendLine()
            }
            if (!lessonTitle.isNullOrBlank()) {
                appendLine("Lesson: " + lessonTitle)
                appendLine()
            }
            appendLine("Original fact:")
            appendLine(originalText)
            appendLine()
            if (!extraInstruction.isNullOrBlank()) {
                appendLine("User instruction: " + extraInstruction)
            } else {
                appendLine("Instruction: Rewrite this fact to be clearer, sharper, and well-suited for a mobile lock screen wallpaper.")
            }
        }
        val request = AiRequest(
            task = AiTask.FACTS,
            systemPrompt = AiPrompts.FactsSystemPrompt,
            userPrompt = userPrompt,
            jsonSchema = AiSchemas.FactsJsonSchema,
            temperature = 0.4,
            maxOutputTokens = 2048,
        )
        val outcome = callService.execute(
            request = request,
            documentId = null,
            manualProfileId = manualProfileId,
        )
        var validation = validator.validateFacts(
            raw = outcome.response.text,
            pageCount = 10_000,
            maxFacts = 10,
        )
        if (!validation.isValid) {
            val repaired = runCatching {
                repairService.repair(
                    profile = outcome.profile,
                    canonicalSchema = AiSchemas.FactsJsonSchema,
                    malformed = outcome.response.text,
                    maxOutputTokens = 2048,
                )
            }.getOrNull()
            if (repaired != null) {
                validation = validator.validateFacts(
                    raw = repaired.response.text,
                    pageCount = 10_000,
                    maxFacts = 10,
                )
            }
        }
        val facts = validation.value ?: throw AiException(
            AiErrorKind.CONTENT,
            validation.errors.firstOrNull() ?: "Fact regeneration response could not be validated.",
        )
        return facts.firstOrNull() ?: throw AiException(
            AiErrorKind.CONTENT,
            "AI returned no facts in regeneration.",
        )
    }

    private fun outlineRequest(
        userPrompt: String,
        pdfBytes: ByteArray? = null,
        pdfFileName: String? = null,
    ): AiRequest = AiRequest(
        task = AiTask.OUTLINE,
        systemPrompt = AiPrompts.OutlineSystemPrompt,
        userPrompt = userPrompt,
        pdfBytes = pdfBytes,
        pdfFileName = pdfFileName,
        jsonSchema = AiSchemas.OutlineJsonSchema,
        temperature = 0.2,
        maxOutputTokens = 8192,
    )

    private fun factsRequest(
        lessonTitle: String,
        material: String,
        density: FactDensity,
        includeFormulas: Boolean,
        includeDiagrams: Boolean,
        extraInstruction: String? = null,
    ): AiRequest = AiRequest(
        task = AiTask.FACTS,
        systemPrompt = AiPrompts.FactsSystemPrompt,
        userPrompt = buildString {
            append(
                AiPrompts.factsUserPromptFromText(
                    lessonTitle = lessonTitle,
                    material = material,
                    density = density,
                    includeFormulas = includeFormulas,
                    includeDiagrams = includeDiagrams,
                ),
            )
            if (!extraInstruction.isNullOrBlank()) {
                append("\n\nAdditional regeneration instruction: ")
                append(extraInstruction.trim())
            }
        },
        jsonSchema = AiSchemas.FactsJsonSchema,
        temperature = 0.3,
        maxOutputTokens = 8192,
    )

    private fun estimateOutlineCostUsd(
        profile: ProviderProfile,
        pages: List<PageText>,
        pdfPageCount: Int?,
    ): AiCostEstimate {
        val inputTokens = if (pdfPageCount != null && pdfPageCount > 0) {
            CostEstimator.estimatePdfTokens(pdfPageCount)
        } else {
            CostEstimator.estimateTextTokens(
                AiPrompts.OutlineSystemPrompt + TextChunker.formatPages(pages),
            )
        }
        val outputTokens = (pages.size.coerceAtLeast(1) * 60).coerceIn(800, 8_000)
        return AiCostEstimate(
            profile = profile,
            estimatedInputTokens = inputTokens,
            estimatedOutputTokens = outputTokens,
            estimatedCostUsd = CostEstimator.estimateCostUsd(profile, inputTokens, outputTokens),
        )
    }

    private fun estimateFactsCostUsd(
        profile: ProviderProfile,
        lessonTitle: String,
        pages: List<PageText>,
        density: FactDensity,
        includeFormulas: Boolean,
        includeDiagrams: Boolean,
    ): AiCostEstimate {
        val chunks = TextChunker.chunk(pages, profile.maxContextTokens)
        val promptPerChunk = AiPrompts.factsUserPromptFromText(
            lessonTitle = lessonTitle,
            material = "",
            density = density,
            includeFormulas = includeFormulas,
            includeDiagrams = includeDiagrams,
        )
        val inputTokens = chunks.sumOf {
            CostEstimator.estimateTextTokens(promptPerChunk + it.text)
        }
        val outputPerChunk = density.maxFactsPerLesson * 140 + 300
        val outputTokens = outputPerChunk * chunks.size.coerceAtLeast(1)
        return AiCostEstimate(
            profile = profile,
            estimatedInputTokens = inputTokens,
            estimatedOutputTokens = outputTokens,
            estimatedCostUsd = CostEstimator.estimateCostUsd(profile, inputTokens, outputTokens),
        )
    }

    private fun outcomeCost(outcome: AiCallOutcome): Double = CostEstimator.estimateCostUsd(
        profile = outcome.profile,
        inputTokens = outcome.response.inputTokens,
        outputTokens = outcome.response.outputTokens,
    )

    private fun mergeOutline(lessons: List<OutlineDraft>): List<OutlineDraft> {
        val sorted = lessons.sortedWith(compareBy({ it.startPage }, { it.endPage }))
        val merged = mutableListOf<OutlineDraft>()
        sorted.forEach { draft ->
            val last = merged.lastOrNull()
            val overlaps = last != null && (
                draft.startPage <= last.endPage ||
                    (draft.title.equals(last.title, ignoreCase = true) &&
                        draft.startPage <= last.endPage + 1)
                )
            if (last != null && overlaps) {
                merged[merged.lastIndex] = last.copy(
                    endPage = max(last.endPage, draft.endPage),
                    summary = listOf(last.summary, draft.summary)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(" "),
                )
            } else {
                merged += draft
            }
        }
        return merged.mapIndexed { index, draft -> draft.copy(sortOrder = index + 1) }
    }
}
