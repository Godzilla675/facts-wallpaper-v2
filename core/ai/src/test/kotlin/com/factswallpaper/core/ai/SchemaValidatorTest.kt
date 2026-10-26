package com.factswallpaper.core.ai

import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.DiagramKind
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SchemaValidatorTest {

    private val validator = SchemaValidator()

    @Test
    fun `extracts json from markdown fences and prose`() {
        val raw = "Here is the result:\n```json\n{\"lessons\":[]}\n```\nDone."

        assertThat(validator.extractJson(raw)).isEqualTo("{\"lessons\":[]}")
    }

    @Test
    fun `valid outline is sorted and sort order is reassigned`() {
        val raw = """
            {
              "lessons": [
                { "title": "Second", "summary": "B", "startPage": 10, "endPage": 20 },
                { "title": "First", "summary": "A", "startPage": 1, "endPage": 9 }
              ]
            }
        """.trimIndent()

        val result = validator.validateOutline(raw, pageCount = 20)

        assertThat(result.isValid).isTrue()
        assertThat(result.value!!.map { it.title }).containsExactly("First", "Second").inOrder()
        assertThat(result.value!!.map { it.sortOrder }).containsExactly(1, 2).inOrder()
    }

    @Test
    fun `outline lessons outside the document or with bad ranges are dropped individually`() {
        val raw = """
            {
              "lessons": [
                { "title": "Good", "summary": "A", "startPage": 1, "endPage": 5 },
                { "title": "Backwards", "summary": "B", "startPage": 9, "endPage": 2 },
                { "title": "Too far", "summary": "C", "startPage": 10, "endPage": 99 },
                { "title": "", "summary": "D", "startPage": 2, "endPage": 3 }
              ]
            }
        """.trimIndent()

        val result = validator.validateOutline(raw, pageCount = 20)

        assertThat(result.isValid).isTrue()
        assertThat(result.value!!.map { it.title }).containsExactly("Good")
        assertThat(result.errors).hasSize(3)
    }

    @Test
    fun `malformed outline fails with a readable error`() {
        val result = validator.validateOutline("{\"lessons\": [", pageCount = 10)

        assertThat(result.isValid).isFalse()
        assertThat(result.errors.first()).contains("Malformed outline JSON")
    }

    @Test
    fun `valid facts are normalized and invalid facts are dropped`() {
        val raw = """
            {
              "facts": [
                {
                  "text": "Entropy of an isolated system never decreases over time.",
                  "latex": null,
                  "requiresLatex": false,
                  "templateType": "TEXT",
                  "diagramType": "NONE",
                  "diagramSource": null,
                  "sourceLessonTitle": "Thermodynamics",
                  "sourcePage": 4,
                  "tags": [" Thermodynamics ", "ENTROPY", "entropy", "heat", "extra"],
                  "confidence": 0.91
                },
                {
                  "text": "short",
                  "latex": null,
                  "requiresLatex": false,
                  "templateType": "TEXT",
                  "diagramType": "NONE",
                  "diagramSource": null,
                  "sourceLessonTitle": null,
                  "sourcePage": 1,
                  "tags": ["x"],
                  "confidence": 0.5
                },
                {
                  "text": "This fact has an unknown template archetype.",
                  "latex": null,
                  "requiresLatex": false,
                  "templateType": "NOPE",
                  "diagramType": "NONE",
                  "diagramSource": null,
                  "sourceLessonTitle": null,
                  "sourcePage": 1,
                  "tags": ["x"],
                  "confidence": 0.5
                }
              ]
            }
        """.trimIndent()

        val result = validator.validateFacts(raw, pageCount = 10)

        assertThat(result.isValid).isTrue()
        assertThat(result.value).hasSize(1)
        val fact = result.value!!.first()
        assertThat(fact.templateType).isEqualTo(CardTemplateType.TEXT.name)
        assertThat(fact.diagramType).isEqualTo(DiagramKind.NONE.name)
        assertThat(fact.tags).containsExactly("thermodynamics", "entropy", "heat", "extra").inOrder()
        assertThat(fact.confidence).isEqualTo(0.91f)
        assertThat(result.errors).hasSize(2)
    }

    @Test
    fun `formula facts require latex`() {
        val raw = """
            {
              "facts": [{
                "text": "Maxwell's equation relates electric and magnetic fields.",
                "latex": null,
                "requiresLatex": true,
                "templateType": "FORMULA",
                "diagramType": "NONE",
                "diagramSource": null,
                "sourceLessonTitle": null,
                "sourcePage": 2,
                "tags": ["electromagnetism"],
                "confidence": 0.9
              }]
            }
        """.trimIndent()

        val result = validator.validateFacts(raw, pageCount = 10)

        assertThat(result.isValid).isFalse()
        assertThat(result.errors.joinToString()).contains("requires LaTeX")
    }

    @Test
    fun `diagram facts require source`() {
        val raw = """
            {
              "facts": [{
                "text": "The water cycle moves through evaporation and condensation.",
                "latex": null,
                "requiresLatex": false,
                "templateType": "DIAGRAM",
                "diagramType": "MERMAID",
                "diagramSource": null,
                "sourceLessonTitle": null,
                "sourcePage": 3,
                "tags": ["water"],
                "confidence": 0.8
              }]
            }
        """.trimIndent()

        val result = validator.validateFacts(raw, pageCount = 10)

        assertThat(result.isValid).isFalse()
        assertThat(result.errors.joinToString()).contains("no source")
    }

    @Test
    fun `deduplicates near identical facts within one batch`() {
        val raw = """
            {
              "facts": [
                {
                  "text": "Entropy of an isolated system never decreases over time.",
                  "latex": null,
                  "requiresLatex": false,
                  "templateType": "TEXT",
                  "diagramType": "NONE",
                  "diagramSource": null,
                  "sourceLessonTitle": null,
                  "sourcePage": 1,
                  "tags": ["entropy"],
                  "confidence": 0.7
                },
                {
                  "text": "Entropy of an isolated system never decreases over time!",
                  "latex": null,
                  "requiresLatex": false,
                  "templateType": "TEXT",
                  "diagramType": "NONE",
                  "diagramSource": null,
                  "sourceLessonTitle": null,
                  "sourcePage": 1,
                  "tags": ["entropy"],
                  "confidence": 0.95
                }
              ]
            }
        """.trimIndent()

        val result = validator.validateFacts(raw, pageCount = 10)

        assertThat(result.value).hasSize(1)
        assertThat(result.value!!.first().confidence).isEqualTo(0.95f)
    }

    @Test
    fun `source page outside the document is dropped but fact is kept`() {
        val raw = """
            {
              "facts": [{
                "text": "A valid fact with an impossible page reference.",
                "latex": null,
                "requiresLatex": false,
                "templateType": "TEXT",
                "diagramType": "NONE",
                "diagramSource": null,
                "sourceLessonTitle": null,
                "sourcePage": 99,
                "tags": ["test"],
                "confidence": 0.8
              }]
            }
        """.trimIndent()

        val result = validator.validateFacts(raw, pageCount = 10)

        assertThat(result.isValid).isTrue()
        assertThat(result.value!!.first().sourcePage).isNull()
    }

    @Test
    fun `verification and diagram payloads are validated`() {
        assertThat(validator.validateVerification("""{"valid":true,"reason":null}""").isValid).isTrue()
        assertThat(
            validator.validateDiagram(
                """{"diagramType":"MERMAID","diagramSource":"flowchart TD\nA-->B","caption":"Flow"}""",
            ).isValid,
        ).isTrue()
        assertThat(
            validator.validateDiagram("""{"diagramType":"NONE","diagramSource":"x","caption":""}""").isValid,
        ).isFalse()
    }

    @Test
    fun `template payload must be a json object`() {
        assertThat(validator.validateTemplate("""{"name":"x","backgroundColor":"#000","slots":[]}""").isValid)
            .isTrue()
        assertThat(validator.validateTemplate("""["not","an","object"]""").isValid).isFalse()
    }
}
