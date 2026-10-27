package com.factswallpaper.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Fact(
    val id: String,
    val documentId: String,
    val lessonId: String?,
    val text: String,
    val latex: String? = null,
    val requiresLatex: Boolean = false,
    val templateType: CardTemplateType = CardTemplateType.TEXT,
    val preferredTemplateId: String? = null,
    val diagramType: DiagramKind = DiagramKind.NONE,
    val diagramSource: String? = null,
    val diagramAssetId: String? = null,
    val sourceReferenceModeOverride: SourceReferenceMode? = null,
    val regenerationInstruction: String? = null,
    val previousText: String? = null,
    val sourceBookTitle: String,
    val sourceLessonTitle: String? = null,
    val sourcePage: Int? = null,
    val tags: List<String> = emptyList(),
    val confidence: Float = 1.0f,
    val verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
    val active: Boolean = true,
    val favorite: Boolean = false,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val reviewGotItCount: Int = 0,
    val reviewAgainCount: Int = 0,
) {
    val hasFormula: Boolean get() = requiresLatex || templateType == CardTemplateType.FORMULA || !latex.isNullOrBlank()
    val hasDiagram: Boolean get() = diagramType != DiagramKind.NONE
}

/** Immutable filter state for the facts list. Pure logic so it is unit-testable. */
@Serializable
data class FactFilter(
    val documentId: String? = null,
    val lessonId: String? = null,
    val query: String = "",
    val status: FactStatusFilter = FactStatusFilter.ALL,
    val tag: String? = null,
) {
    val hasActiveFilters: Boolean
        get() = documentId != null || lessonId != null || query.isNotBlank() ||
            status != FactStatusFilter.ALL || tag != null

    fun matches(fact: Fact): Boolean {
        if (documentId != null && fact.documentId != documentId) return false
        if (lessonId != null && fact.lessonId != lessonId) return false
        if (tag != null && fact.tags.none { it.equals(tag, ignoreCase = true) }) return false
        if (!matchesStatus(fact)) return false
        val needle = query.trim()
        if (needle.isEmpty()) return true
        return fact.text.contains(needle, ignoreCase = true) ||
            fact.latex?.contains(needle, ignoreCase = true) == true ||
            fact.tags.any { it.contains(needle, ignoreCase = true) } ||
            fact.sourceLessonTitle?.contains(needle, ignoreCase = true) == true
    }

    private fun matchesStatus(fact: Fact): Boolean = when (status) {
        FactStatusFilter.ALL -> true
        FactStatusFilter.ACTIVE -> fact.active
        FactStatusFilter.IGNORED -> !fact.active
        FactStatusFilter.FAVORITES -> fact.favorite
        FactStatusFilter.FORMULAS -> fact.hasFormula
        FactStatusFilter.DIAGRAMS -> fact.hasDiagram
    }
}

/** Counters for the collections screen smart views. */
@Serializable
data class SmartViewCounts(
    val total: Int = 0,
    val active: Int = 0,
    val favorites: Int = 0,
    val formulas: Int = 0,
    val diagrams: Int = 0,
)

@Serializable
data class RotationPoolSummary(
    val factCount: Int = 0,
    val collectionCount: Int = 0,
    val folderCount: Int = 0,
)
