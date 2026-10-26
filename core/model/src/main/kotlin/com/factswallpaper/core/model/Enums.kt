package com.factswallpaper.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PdfProcessingRoute { DIRECT_PDF, EXTRACT_TEXT, LOCAL_OCR, OCR_REQUIRES_OVERRIDE }

@Serializable
enum class OcrEngineType { ML_KIT, TESSERACT, PADDLE_PPOCR }

@Serializable
enum class OcrExecutionMode { FOREGROUND, BACKGROUND }

@Serializable
enum class FactDensity(val minFactsPerLesson: Int, val maxFactsPerLesson: Int) {
    CONCISE(3, 5),
    BALANCED(6, 10),
    DEEP(12, 18)
}

@Serializable
enum class CardTemplateType {
    TEXT, QUOTE, BULLET, DEFINITION, QA, FORMULA, DIAGRAM, TIMELINE
}

@Serializable
enum class DiagramKind { NONE, MERMAID, TIKZ, SVG, HTML_CANVAS }

@Serializable
enum class VerificationStatus { UNVERIFIED, VERIFIED, FAILED }

@Serializable
enum class CollectionSource { USER, AI_SUGGESTED, SMART_FILTER }

@Serializable
enum class TemplateEngineKind { NATIVE_JSON, HTML_CANVAS, SVG_TEMPLATE }

@Serializable
enum class SourceReferenceMode { FULL, COMPACT, PAGE_ONLY, HIDDEN }

@Serializable
enum class WallpaperTarget { HOME, LOCK, BOTH, LIVE }

@Serializable
enum class WallpaperScheduleMode { ON_UNLOCK, INTERVAL, MANUAL }

@Serializable
enum class RotationOrder { SEQUENTIAL, RANDOM }

@Serializable
enum class ThemeMode { LIGHT, DARK, AMOLED, DYNAMIC }

@Serializable
enum class ProviderType {
    GEMINI, OPENAI, ANTHROPIC, AZURE_OPENAI, OPENROUTER, DEEPSEEK, QWEN, OPENAI_COMPATIBLE
}

@Serializable
enum class RoutingStrategy { CHEAPEST, BALANCED, HIGHEST_QUALITY, MANUAL }

/** Result of the most recent provider connection test. */
@Serializable
enum class ProviderKeyStatus { UNKNOWN, VALID, INVALID }

@Serializable
enum class AiTask {
    OUTLINE,
    FACTS,
    JSON_REPAIR,
    FORMULA_VERIFICATION,
    DIAGRAM_GENERATION,
    TEMPLATE_GENERATION
}

/** Lifecycle of a document as it moves through inspection, OCR, outline and fact extraction. */
@Serializable
enum class ExtractionStatus {
    IMPORTED,
    ANALYZED,
    OCR_IN_PROGRESS,
    OCR_PAUSED,
    OCR_DONE,
    OUTLINE_READY,
    FACTS_READY,
    ERROR;

    val isOcrActive: Boolean get() = this == OCR_IN_PROGRESS || this == OCR_PAUSED

    companion object {
        fun fromStorage(value: String): ExtractionStatus =
            entries.firstOrNull { it.name == value } ?: IMPORTED
    }
}

/** The filter chips used by the Facts screen. */
@Serializable
enum class FactStatusFilter {
    ALL,
    ACTIVE,
    IGNORED,
    FAVORITES,
    FORMULAS,
    DIAGRAMS
}
