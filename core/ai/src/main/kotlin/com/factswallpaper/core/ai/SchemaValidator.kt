package com.factswallpaper.core.ai

import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.DiagramKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class OutlineDraft(
    val title: String,
    val summary: String,
    val startPage: Int,
    val endPage: Int,
    val sortOrder: Int,
)

data class ValidationResult<T>(
    val value: T?,
    val errors: List<String> = emptyList(),
) {
    val isValid: Boolean get() = value != null
}

@Serializable
data class OutlineEnvelope(val lessons: List<OutlineLessonJson> = emptyList())

@Serializable
data class OutlineLessonJson(
    val title: String = "",
    val summary: String = "",
    val startPage: Int? = null,
    val endPage: Int? = null,
)

@Serializable
data class FactsEnvelope(val facts: List<FactJson> = emptyList())

@Serializable
data class FactJson(
    val text: String = "",
    val latex: String? = null,
    val requiresLatex: Boolean = false,
    val templateType: String = "TEXT",
    val diagramType: String = "NONE",
    val diagramSource: String? = null,
    val sourceLessonTitle: String? = null,
    val sourcePage: Int? = null,
    val tags: List<String> = emptyList(),
    val confidence: Double = 0.8,
)

@Serializable
data class VerificationEnvelope(
    val valid: Boolean = false,
    val reason: String? = null,
)

@Serializable
data class DiagramEnvelope(
    val diagramType: String = "MERMAID",
    val diagramSource: String = "",
    val caption: String = "",
)

/**
 * Provider-independent final authority for structured AI output (spec §11.2).
 * It parses markdown-fenced JSON, enforces schema constraints locally, and drops
 * individual invalid facts rather than failing an otherwise usable batch.
 */
class SchemaValidator {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    fun extractJson(raw: String): String {
        var text = raw.trim()
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```", RegexOption.IGNORE_CASE)
            .find(text)
            ?.groupValues
            ?.getOrNull(1)
        if (!fenced.isNullOrBlank()) text = fenced.trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        return if (start >= 0 && end > start) text.substring(start, end + 1) else text
    }

    fun validateOutline(raw: String, pageCount: Int): ValidationResult<List<OutlineDraft>> {
        val envelope = runCatching {
            json.decodeFromString(OutlineEnvelope.serializer(), extractJson(raw))
        }.getOrElse { error ->
            return ValidationResult(null, listOf("Malformed outline JSON: ${error.message}"))
        }
        val errors = mutableListOf<String>()
        val valid = envelope.lessons.mapIndexedNotNull { index, lesson ->
            val title = lesson.title.trim()
            val summary = lesson.summary.trim()
            val start = lesson.startPage
            val end = lesson.endPage
            when {
                title.isEmpty() -> {
                    errors += "Lesson $index has no title"
                    null
                }
                summary.isEmpty() -> {
                    errors += "Lesson $index has no summary"
                    null
                }
                start == null || end == null -> {
                    errors += "Lesson $index has no page range"
                    null
                }
                start < 1 || end < start -> {
                    errors += "Lesson $index has an invalid page range"
                    null
                }
                pageCount > 0 && end > pageCount -> {
                    errors += "Lesson $index exceeds the document page count"
                    null
                }
                else -> OutlineDraft(
                    title = title,
                    summary = summary,
                    startPage = start,
                    endPage = end,
                    sortOrder = 0,
                )
            }
        }
            .sortedBy { it.startPage }
            .mapIndexed { index, draft -> draft.copy(sortOrder = index + 1) }

        return if (valid.isEmpty()) {
            ValidationResult(null, errors.ifEmpty { listOf("No valid lessons returned") })
        } else {
            ValidationResult(valid, errors)
        }
    }

    fun validateFacts(
        raw: String,
        pageCount: Int,
        maxFacts: Int = Int.MAX_VALUE,
    ): ValidationResult<List<FactDraft>> {
        val envelope = runCatching {
            json.decodeFromString(FactsEnvelope.serializer(), extractJson(raw))
        }.getOrElse { error ->
            return ValidationResult(null, listOf("Malformed facts JSON: ${error.message}"))
        }
        val errors = mutableListOf<String>()
        val valid = envelope.facts.mapIndexedNotNull { index, fact ->
            val text = fact.text.trim()
            val template = runCatching { CardTemplateType.valueOf(fact.templateType.uppercase()) }
                .getOrNull()
            val diagram = runCatching { DiagramKind.valueOf(fact.diagramType.uppercase()) }
                .getOrNull()
            val confidence = fact.confidence
            val requiresLatex = fact.requiresLatex || template == CardTemplateType.FORMULA
            val latex = fact.latex?.trim()?.takeIf { it.isNotEmpty() }
            val diagramSource = fact.diagramSource?.trim()?.takeIf { it.isNotEmpty() }
            val tags = fact.tags.map { it.trim().lowercase() }
                .filter { it.isNotEmpty() }
                .distinct()
                .take(4)
            val sourcePage = fact.sourcePage?.takeIf { it >= 1 && (pageCount <= 0 || it <= pageCount) }
            when {
                text.length < 10 -> {
                    errors += "Fact $index is shorter than 10 characters"
                    null
                }
                text.length > 2_000 -> {
                    errors += "Fact $index is implausibly long"
                    null
                }
                template == null -> {
                    errors += "Fact $index has an unknown template type"
                    null
                }
                diagram == null -> {
                    errors += "Fact $index has an unknown diagram type"
                    null
                }
                confidence !in 0.0..1.0 -> {
                    errors += "Fact $index has confidence outside 0..1"
                    null
                }
                requiresLatex && latex == null -> {
                    errors += "Fact $index requires LaTeX but has none"
                    null
                }
                diagram != DiagramKind.NONE && diagramSource == null -> {
                    errors += "Fact $index has a diagram type but no source"
                    null
                }
                tags.isEmpty() -> {
                    errors += "Fact $index has no tags"
                    null
                }
                else -> FactDraft(
                    text = text,
                    latex = latex,
                    requiresLatex = requiresLatex,
                    templateType = template.name,
                    diagramType = diagram.name,
                    diagramSource = diagramSource,
                    sourceLessonTitle = fact.sourceLessonTitle?.trim()?.takeIf { it.isNotEmpty() },
                    sourcePage = sourcePage,
                    tags = tags,
                    confidence = confidence.toFloat(),
                )
            }
        }.take(maxFacts)

        val deduplicated = TextChunker.deduplicate(valid)
        return if (deduplicated.isEmpty()) {
            ValidationResult(null, errors.ifEmpty { listOf("No valid facts returned") })
        } else {
            ValidationResult(deduplicated, errors)
        }
    }

    fun validateVerification(raw: String): ValidationResult<VerificationEnvelope> {
        val envelope = runCatching {
            json.decodeFromString(VerificationEnvelope.serializer(), extractJson(raw))
        }.getOrElse { error ->
            return ValidationResult(null, listOf("Malformed verification JSON: ${error.message}"))
        }
        return ValidationResult(envelope)
    }

    fun validateDiagram(raw: String): ValidationResult<DiagramEnvelope> {
        val envelope = runCatching {
            json.decodeFromString(DiagramEnvelope.serializer(), extractJson(raw))
        }.getOrElse { error ->
            return ValidationResult(null, listOf("Malformed diagram JSON: ${error.message}"))
        }
        val kind = runCatching { DiagramKind.valueOf(envelope.diagramType.uppercase()) }.getOrNull()
        return when {
            kind == null || kind == DiagramKind.NONE ->
                ValidationResult(null, listOf("Unknown diagram type"))
            envelope.diagramSource.trim().length < 10 ->
                ValidationResult(null, listOf("Diagram source is too short"))
            else -> ValidationResult(envelope)
        }
    }

    fun validateTemplate(raw: String): ValidationResult<String> {
        val element = runCatching { json.parseToJsonElement(extractJson(raw)) }.getOrNull()
        return if (element is JsonObject) {
            ValidationResult(element.toString())
        } else {
            ValidationResult(null, listOf("Template response is not a JSON object"))
        }
    }

    fun validateCollections(raw: String, validFactIds: Set<String>): ValidationResult<List<SuggestedCollectionItem>> {
        val element = runCatching { json.parseToJsonElement(extractJson(raw)) }.getOrNull()
        val root = element as? JsonObject
            ?: return ValidationResult(null, listOf("Collections response is not a JSON object"))
        val collectionsArray = root["collections"] as? JsonArray
            ?: return ValidationResult(null, listOf("Missing 'collections' array"))
        val items = collectionsArray.mapNotNull { itemElement ->
            val obj = itemElement as? JsonObject ?: return@mapNotNull null
            val name = (obj["name"] as? JsonPrimitive)?.content?.trim()
            val description = (obj["description"] as? JsonPrimitive)?.content?.trim() ?: ""
            val factIds = (obj["factIds"] as? JsonArray)?.mapNotNull {
                (it as? JsonPrimitive)?.content?.trim()
            }?.filter { it in validFactIds }?.distinct() ?: emptyList()
            if (!name.isNullOrBlank() && factIds.size >= 2) {
                SuggestedCollectionItem(name = name, description = description, factIds = factIds)
            } else null
        }
        return if (items.isEmpty()) {
            ValidationResult(null, listOf("No valid collections with at least 2 facts were found"))
        } else {
            ValidationResult(items)
        }
    }
}
