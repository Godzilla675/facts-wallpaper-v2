package com.factswallpaper.core.render.verify

import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.DiagramKind
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.FactFilter
import com.factswallpaper.core.model.FactTag
import com.factswallpaper.core.model.PdfProcessingRoute
import com.factswallpaper.core.model.SmartViewCounts
import com.factswallpaper.core.model.VerificationStatus
import com.factswallpaper.core.render.diagram.MarkupKind
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Test

class FormulaVerificationServiceTest {

    @Test
    fun `tier 1 rejects dangerous or broken markup before calling AI verifiers`() = runBlocking {
        var aiCalled = false
        val verifier = object : FormulaAiVerifier {
            override suspend fun verify(kind: MarkupKind, source: String): FormulaAiVerdict {
                aiCalled = true
                return FormulaAiVerdict(valid = true)
            }
        }
        val fakeFacts = FakeFactRepository()
        val service = FormulaVerificationService(
            verifiers = setOf(verifier),
            documentRepository = FakeDocumentRepository(autoVerifyFormulas = true),
            factRepository = fakeFacts,
        )

        val brokenFact = formulaFact(latex = "\\write18{evil}")
        val outcome = service.verifyAndPersist(brokenFact)

        assertThat(outcome).isInstanceOf(FormulaVerificationOutcome.Failed::class.java)
        assertThat(aiCalled).isFalse()
        assertThat(fakeFacts.savedFacts.single().verificationStatus)
            .isEqualTo(VerificationStatus.FAILED)
    }

    @Test
    fun `tier 2 marks formula VERIFIED when sanitizer and AI verifier both succeed`() = runBlocking {
        val verifier = object : FormulaAiVerifier {
            override suspend fun verify(kind: MarkupKind, source: String): FormulaAiVerdict =
                FormulaAiVerdict(valid = true)
        }
        val fakeFacts = FakeFactRepository()
        val service = FormulaVerificationService(
            verifiers = setOf(verifier),
            documentRepository = FakeDocumentRepository(autoVerifyFormulas = true),
            factRepository = fakeFacts,
        )

        val validFact = formulaFact(latex = "E = mc^2")
        val outcome = service.verifyAndPersist(validFact)

        assertThat(outcome).isEqualTo(FormulaVerificationOutcome.Verified)
        assertThat(fakeFacts.savedFacts.single().verificationStatus)
            .isEqualTo(VerificationStatus.VERIFIED)
    }

    @Test
    fun `tier 2 marks formula FAILED when AI verifier rejects semantics`() = runBlocking {
        val verifier = object : FormulaAiVerifier {
            override suspend fun verify(kind: MarkupKind, source: String): FormulaAiVerdict =
                FormulaAiVerdict(valid = false, reason = "Undefined operator")
        }
        val fakeFacts = FakeFactRepository()
        val service = FormulaVerificationService(
            verifiers = setOf(verifier),
            documentRepository = FakeDocumentRepository(autoVerifyFormulas = true),
            factRepository = fakeFacts,
        )

        val fact = formulaFact(latex = "a + b = c")
        val outcome = service.verifyAndPersist(fact)

        assertThat(outcome).isEqualTo(FormulaVerificationOutcome.Failed("Undefined operator"))
        assertThat(fakeFacts.savedFacts.single().verificationStatus)
            .isEqualTo(VerificationStatus.FAILED)
    }

    @Test
    fun `missing AI verifier or disabled autoVerify leaves status pending without false positive`() = runBlocking {
        val fakeFacts = FakeFactRepository()
        val service = FormulaVerificationService(
            verifiers = emptySet(),
            documentRepository = FakeDocumentRepository(autoVerifyFormulas = true),
            factRepository = fakeFacts,
        )

        val fact = formulaFact(latex = "F = ma")
        val outcome = service.verifyAndPersist(fact)

        assertThat(outcome).isEqualTo(FormulaVerificationOutcome.PendingAi)
        assertThat(fakeFacts.savedFacts).isEmpty()
    }

    private fun formulaFact(latex: String): Fact = Fact(
        id = "fact-formula",
        documentId = "doc-1",
        lessonId = null,
        text = "Energy-mass equivalence",
        latex = latex,
        requiresLatex = true,
        templateType = CardTemplateType.FORMULA,
        diagramType = DiagramKind.NONE,
        diagramSource = null,
        diagramAssetId = null,
        verificationStatus = VerificationStatus.UNVERIFIED,
        sourceBookTitle = "Relativity",
        sourceLessonTitle = null,
        sourcePage = 10,
        sourceReferenceModeOverride = null,
        active = true,
        favorite = false,
        tags = emptyList(),
        createdAtEpochMs = 1L,
        updatedAtEpochMs = 1L,
    )

    private class FakeDocumentRepository(
        private val autoVerifyFormulas: Boolean,
    ) : DocumentRepository {
        override fun observeDocuments(): Flow<List<Document>> = flowOf(emptyList())
        override fun observeDocument(documentId: String): Flow<Document?> = flowOf(null)
        override suspend fun getDocument(documentId: String): Document? = null
        override suspend fun findByHash(fileHash: String): Document? = null
        override fun observeSettings(documentId: String): Flow<DocumentSettings?> = flowOf(null)
        override suspend fun getSettings(documentId: String): DocumentSettings =
            DocumentSettings(
                documentId = documentId,
                pageRangeEnd = 10,
                autoVerifyFormulas = autoVerifyFormulas,
            )
        override suspend fun saveDocument(document: Document) = Unit
        override suspend fun saveDocumentWithSettings(document: Document, settings: DocumentSettings) = Unit
        override suspend fun saveSettings(settings: DocumentSettings) = Unit
        override suspend fun setIncludedInRotation(documentId: String, included: Boolean) = Unit
        override suspend fun updateExtractionStatus(
            documentId: String,
            status: ExtractionStatus,
            errorMessage: String?,
        ) = Unit
        override suspend fun deleteDocument(documentId: String): AppResult<Unit> = AppResult.Success(Unit)
        override fun observeRouteCounts(): Flow<Map<PdfProcessingRoute, Int>> = flowOf(emptyMap())
    }

    private class FakeFactRepository : FactRepository {
        val savedFacts = mutableListOf<Fact>()
        override fun observeFacts(filter: FactFilter): Flow<List<Fact>> = MutableStateFlow(emptyList())
        override fun observeFact(factId: String): Flow<Fact?> = flowOf(null)
        override suspend fun getFact(factId: String): Fact? = null
        override suspend fun saveFact(fact: Fact) {
            savedFacts += fact
        }
        override suspend fun saveFacts(facts: List<Fact>) {
            savedFacts += facts
        }
        override suspend fun setActive(factId: String, active: Boolean) = Unit
        override suspend fun setFavorite(factId: String, favorite: Boolean) = Unit
        override suspend fun deleteFact(factId: String): AppResult<Unit> = AppResult.Success(Unit)
        override fun observeTags(): Flow<List<FactTag>> = flowOf(emptyList())
        override fun observeSmartViewCounts(): Flow<SmartViewCounts> = flowOf(SmartViewCounts())
        override fun observeTotalCount(): Flow<Int> = flowOf(0)
    }
}