package com.factswallpaper.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class FactFilterTest {

    private val formulaFact = fact(
        id = "f1",
        documentId = "doc-1",
        lessonId = "lesson-1",
        text = "Newton's second law is F = ma",
        latex = "F = ma",
        tags = listOf("Physics", "mechanics"),
        sourceLessonTitle = "Laws of Motion",
        templateType = CardTemplateType.FORMULA,
        verificationStatus = VerificationStatus.VERIFIED,
    )

    private val plainFact = fact(
        id = "f2",
        documentId = "doc-1",
        lessonId = "lesson-2",
        text = "Cells are the basic unit of life",
        tags = listOf("biology"),
        sourceLessonTitle = "Cell Theory",
        active = false,
        favorite = true,
    )

    private val diagramFact = fact(
        id = "f3",
        documentId = "doc-2",
        lessonId = null,
        text = "Water cycle diagram",
        tags = listOf("biology", "earth"),
        diagramType = DiagramKind.MERMAID,
        diagramSource = "graph TD; A-->B",
    )

    private fun fact(
        id: String,
        documentId: String,
        lessonId: String?,
        text: String,
        latex: String? = null,
        tags: List<String> = emptyList(),
        sourceLessonTitle: String? = null,
        templateType: CardTemplateType = CardTemplateType.TEXT,
        verificationStatus: VerificationStatus = VerificationStatus.UNVERIFIED,
        active: Boolean = true,
        favorite: Boolean = false,
        diagramType: DiagramKind = DiagramKind.NONE,
        diagramSource: String? = null,
        nextReviewDueEpochMs: Long? = null,
    ): Fact = Fact(
        id = id,
        documentId = documentId,
        lessonId = lessonId,
        text = text,
        latex = latex,
        tags = tags,
        sourceLessonTitle = sourceLessonTitle,
        templateType = templateType,
        verificationStatus = verificationStatus,
        active = active,
        favorite = favorite,
        diagramType = diagramType,
        diagramSource = diagramSource,
        sourceBookTitle = "Source Book",
        createdAtEpochMs = 1000L,
        nextReviewDueEpochMs = nextReviewDueEpochMs,
    )

    @Test
    fun `empty filter matches every fact`() {
        val filter = FactFilter()
        assertThat(filter.matches(formulaFact)).isTrue()
        assertThat(filter.matches(plainFact)).isTrue()
        assertThat(filter.matches(diagramFact)).isTrue()
        assertThat(filter.hasActiveFilters).isFalse()
    }

    @Test
    fun `document and lesson filters match IDs exactly`() {
        assertThat(FactFilter(documentId = "doc-1").matches(formulaFact)).isTrue()
        assertThat(FactFilter(documentId = "doc-2").matches(formulaFact)).isFalse()
        assertThat(FactFilter(lessonId = "lesson-2").matches(plainFact)).isTrue()
        assertThat(FactFilter(lessonId = "lesson-2").matches(formulaFact)).isFalse()
    }

    @Test
    fun `tag filter matches case-insensitively`() {
        assertThat(FactFilter(tag = "PHYSICS").matches(formulaFact)).isTrue()
        assertThat(FactFilter(tag = "Chemistry").matches(formulaFact)).isFalse()
    }

    @Test
    fun `query filter checks text formula lesson and tags`() {
        assertThat(FactFilter(query = "F = ma").matches(formulaFact)).isTrue()
        assertThat(FactFilter(query = "newton").matches(formulaFact)).isTrue()
        assertThat(FactFilter(query = "MECHANICS").matches(formulaFact)).isTrue()
        assertThat(FactFilter(query = "laws of motion").matches(formulaFact)).isTrue()
        assertThat(FactFilter(query = "quantum").matches(formulaFact)).isFalse()
    }

    @Test
    fun `query trims leading and trailing whitespace`() {
        assertThat(FactFilter(query = "   ").matches(plainFact)).isTrue()
        assertThat(FactFilter(query = "  cells  ").matches(plainFact)).isTrue()
    }

    @Test
    fun `status filters cover active ignored favourites formulas diagrams and due for review`() {
        assertThat(FactFilter(status = FactStatusFilter.ACTIVE).matches(formulaFact)).isTrue()
        assertThat(FactFilter(status = FactStatusFilter.ACTIVE).matches(plainFact)).isFalse()
        assertThat(FactFilter(status = FactStatusFilter.IGNORED).matches(plainFact)).isTrue()
        assertThat(FactFilter(status = FactStatusFilter.IGNORED).matches(formulaFact)).isFalse()
        assertThat(FactFilter(status = FactStatusFilter.FAVORITES).matches(plainFact)).isTrue()
        assertThat(FactFilter(status = FactStatusFilter.FAVORITES).matches(formulaFact)).isFalse()
        assertThat(FactFilter(status = FactStatusFilter.FORMULAS).matches(formulaFact)).isTrue()
        assertThat(FactFilter(status = FactStatusFilter.FORMULAS).matches(diagramFact)).isFalse()
        assertThat(FactFilter(status = FactStatusFilter.DIAGRAMS).matches(diagramFact)).isTrue()
        assertThat(FactFilter(status = FactStatusFilter.DIAGRAMS).matches(formulaFact)).isFalse()

        // Due for review: active and (due is null or due in past)
        assertThat(FactFilter(status = FactStatusFilter.DUE_FOR_REVIEW).matches(formulaFact)).isTrue()
        val futureDueFact = formulaFact.copy(nextReviewDueEpochMs = System.currentTimeMillis() + 100_000L)
        assertThat(FactFilter(status = FactStatusFilter.DUE_FOR_REVIEW).matches(futureDueFact)).isFalse()
        val pastDueFact = formulaFact.copy(nextReviewDueEpochMs = System.currentTimeMillis() - 1000L)
        assertThat(FactFilter(status = FactStatusFilter.DUE_FOR_REVIEW).matches(pastDueFact)).isTrue()
        // Inactive fact should not be due
        assertThat(FactFilter(status = FactStatusFilter.DUE_FOR_REVIEW).matches(plainFact)).isFalse()
    }

    @Test
    fun `filters combine instead of replacing each other`() {
        val filter = FactFilter(
            documentId = "doc-1",
            status = FactStatusFilter.ALL,
            query = "biology",
        )

        assertThat(filter.matches(plainFact)).isTrue()
        assertThat(filter.matches(formulaFact)).isFalse()
        assertThat(filter.matches(diagramFact)).isFalse()
        assertThat(filter.hasActiveFilters).isTrue()
    }

    @Test
    fun `fact convenience flags describe formula and diagram content`() {
        assertThat(formulaFact.hasFormula).isTrue()
        assertThat(formulaFact.hasDiagram).isFalse()
        assertThat(diagramFact.hasDiagram).isTrue()
    }
}
