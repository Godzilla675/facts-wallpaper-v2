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
    ): Fact = Fact(
        id = id,
        documentId = documentId,
        lessonId = lessonId,
        text = text,
        latex = latex,
        requiresLatex = latex != null,
        templateType = templateType,
        diagramType = diagramType,
        diagramSource = diagramSource,
        sourceBookTitle = "Book",
        sourceLessonTitle = sourceLessonTitle,
        tags = tags,
        verificationStatus = verificationStatus,
        active = active,
        favorite = favorite,
        createdAtEpochMs = 1_000L,
    )

    @Test
    fun `empty filter matches every fact and reports no active filters`() {
        val filter = FactFilter()

        assertThat(filter.hasActiveFilters).isFalse()
        assertThat(filter.matches(formulaFact)).isTrue()
        assertThat(filter.matches(plainFact)).isTrue()
        assertThat(filter.matches(diagramFact)).isTrue()
    }

    @Test
    fun `document and lesson filters narrow by identity`() {
        assertThat(FactFilter(documentId = "doc-1").matches(formulaFact)).isTrue()
        assertThat(FactFilter(documentId = "doc-2").matches(formulaFact)).isFalse()
        assertThat(FactFilter(lessonId = "lesson-2").matches(plainFact)).isTrue()
        assertThat(FactFilter(lessonId = "lesson-2").matches(formulaFact)).isFalse()
    }

    @Test
    fun `tag filter is case insensitive`() {
        assertThat(FactFilter(tag = "PHYSICS").matches(formulaFact)).isTrue()
        assertThat(FactFilter(tag = "Chemistry").matches(formulaFact)).isFalse()
    }

    @Test
    fun `query searches text latex tags and lesson title`() {
        assertThat(FactFilter(query = "F = ma").matches(formulaFact)).isTrue()
        assertThat(FactFilter(query = "newton").matches(formulaFact)).isTrue()
        assertThat(FactFilter(query = "MECHANICS").matches(formulaFact)).isTrue()
        assertThat(FactFilter(query = "laws of motion").matches(formulaFact)).isTrue()
        assertThat(FactFilter(query = "quantum").matches(formulaFact)).isFalse()
    }

    @Test
    fun `query is trimmed and blank query matches all`() {
        assertThat(FactFilter(query = "   ").matches(plainFact)).isTrue()
        assertThat(FactFilter(query = "  cells  ").matches(plainFact)).isTrue()
    }

    @Test
    fun `status filters cover active ignored favourites formulas and diagrams`() {
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
        assertThat(plainFact.hasFormula).isFalse()
    }
}
