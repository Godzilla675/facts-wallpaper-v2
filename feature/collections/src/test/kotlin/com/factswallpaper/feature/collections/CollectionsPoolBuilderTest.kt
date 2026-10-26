package com.factswallpaper.feature.collections

import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.Collection
import com.factswallpaper.core.model.CollectionSource
import com.factswallpaper.core.model.DiagramKind
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.SmartViewCounts
import com.factswallpaper.core.model.VerificationStatus
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CollectionsPoolBuilderTest {

    @Test
    fun `smart view counts partition all, active, favorite, formula and diagram facts accurately`() {
        val facts = listOf(
            sampleFact(id = "f1", active = true, favorite = true),
            sampleFact(
                id = "f2",
                active = true,
                requiresLatex = true,
                latex = "E = mc^2",
                templateType = CardTemplateType.FORMULA,
            ),
            sampleFact(
                id = "f3",
                active = true,
                diagramType = DiagramKind.MERMAID,
                diagramSource = "graph TD; A-->B",
            ),
            sampleFact(id = "f4", active = false, favorite = true),
        )

        val counts = SmartViewCounts(
            total = facts.size,
            active = facts.count { it.active },
            favorites = facts.count { it.favorite },
            formulas = facts.count { it.hasFormula },
            diagrams = facts.count { it.hasDiagram },
        )

        assertThat(counts.total).isEqualTo(4)
        assertThat(counts.active).isEqualTo(3)
        assertThat(counts.favorites).isEqualTo(2)
        assertThat(counts.formulas).isEqualTo(1)
        assertThat(counts.diagrams).isEqualTo(1)
    }

    @Test
    fun `collections state separates accepted user collections from pending AI suggestions`() {
        val collections = listOf(
            Collection(
                id = "c-user",
                name = "Midterm Prep",
                description = "Exam study deck",
                source = CollectionSource.USER,
                accepted = true,
                includeInRotation = true,
                factCount = 12,
                createdAtEpochMs = 1L,
            ),
            Collection(
                id = "c-ai-pending",
                name = "Thermodynamics concepts",
                description = "Grouped from tags",
                source = CollectionSource.AI_SUGGESTED,
                accepted = false,
                includeInRotation = false,
                factCount = 5,
                createdAtEpochMs = 2L,
            ),
            Collection(
                id = "c-ai-accepted",
                name = "Electromagnetism concepts",
                description = "Accepted AI deck",
                source = CollectionSource.AI_SUGGESTED,
                accepted = true,
                includeInRotation = true,
                factCount = 8,
                createdAtEpochMs = 3L,
            ),
        )

        val accepted = collections.filter { it.accepted }
        val suggested = collections.filter { !it.accepted && it.source == CollectionSource.AI_SUGGESTED }

        assertThat(accepted.map { it.id }).containsExactly("c-user", "c-ai-accepted")
        assertThat(suggested.map { it.id }).containsExactly("c-ai-pending")
    }

    private fun sampleFact(
        id: String,
        active: Boolean = true,
        favorite: Boolean = false,
        latex: String? = null,
        requiresLatex: Boolean = false,
        templateType: CardTemplateType = CardTemplateType.TEXT,
        diagramType: DiagramKind = DiagramKind.NONE,
        diagramSource: String? = null,
    ): Fact = Fact(
        id = id,
        documentId = "doc-1",
        lessonId = null,
        text = "Fact $id",
        latex = latex,
        requiresLatex = requiresLatex,
        templateType = templateType,
        diagramType = diagramType,
        diagramSource = diagramSource,
        diagramAssetId = null,
        verificationStatus = VerificationStatus.VERIFIED,
        sourceBookTitle = "Physics",
        sourceLessonTitle = null,
        sourcePage = 1,
        sourceReferenceModeOverride = null,
        active = active,
        favorite = favorite,
        tags = listOf("physics"),
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )
}