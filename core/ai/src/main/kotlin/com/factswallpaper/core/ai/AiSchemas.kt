package com.factswallpaper.core.ai

import com.factswallpaper.core.model.FactDensity

/**
 * Verbatim schemas and prompt templates from spec §6.6. Keep these strings in
 * sync with the specification; the schemas are canonical and are adapted per
 * provider by [SchemaAdapter] before they go on the wire.
 */
object AiSchemas {

    val OutlineJsonSchema: String = """
        {
          "type": "object",
          "required": ["lessons"],
          "properties": {
            "lessons": {
              "type": "array",
              "items": {
                "type": "object",
                "required": ["title", "summary", "startPage", "endPage"],
                "properties": {
                  "title": { "type": "string" },
                  "summary": { "type": "string" },
                  "startPage": { "type": "integer", "minimum": 1 },
                  "endPage": { "type": "integer", "minimum": 1 }
                },
                "additionalProperties": false
              }
            }
          },
          "additionalProperties": false
        }
    """.trimIndent()

    val FactsJsonSchema: String = """
        {
          "type": "object",
          "required": ["facts"],
          "properties": {
            "facts": {
              "type": "array",
              "items": {
                "type": "object",
                "required": [
                  "text",
                  "latex",
                  "requiresLatex",
                  "templateType",
                  "diagramType",
                  "diagramSource",
                  "sourceLessonTitle",
                  "sourcePage",
                  "tags",
                  "confidence"
                ],
                "properties": {
                  "text": { "type": "string", "minLength": 10 },
                  "latex": { "type": ["string", "null"] },
                  "requiresLatex": { "type": "boolean" },
                  "templateType": {
                    "type": "string",
                    "enum": ["TEXT", "QUOTE", "BULLET", "DEFINITION", "QA", "FORMULA", "DIAGRAM", "TIMELINE"]
                  },
                  "diagramType": {
                    "type": "string",
                    "enum": ["NONE", "MERMAID", "TIKZ", "SVG", "HTML_CANVAS"]
                  },
                  "diagramSource": { "type": ["string", "null"] },
                  "sourceLessonTitle": { "type": ["string", "null"] },
                  "sourcePage": { "type": ["integer", "null"] },
                  "tags": {
                    "type": "array",
                    "items": { "type": "string" }
                  },
                  "confidence": { "type": "number", "minimum": 0.0, "maximum": 1.0 }
                },
                "additionalProperties": false
              }
            }
          },
          "additionalProperties": false
        }
    """.trimIndent()

    val FormulaVerificationJsonSchema: String = """
        {
          "type": "object",
          "required": ["valid", "reason"],
          "properties": {
            "valid": { "type": "boolean" },
            "reason": { "type": ["string", "null"] }
          },
          "additionalProperties": false
        }
    """.trimIndent()

    val DiagramJsonSchema: String = """
        {
          "type": "object",
          "required": ["diagramType", "diagramSource", "caption"],
          "properties": {
            "diagramType": { "type": "string", "enum": ["MERMAID", "TIKZ", "SVG", "HTML_CANVAS"] },
            "diagramSource": { "type": "string", "minLength": 10 },
            "caption": { "type": "string" }
          },
          "additionalProperties": false
        }
    """.trimIndent()

    /** Matches the CardTemplateSpec schema owned by :core:render (spec §7.1). */
    val TemplateJsonSchema: String = """
        {
          "type": "object",
          "required": ["name", "backgroundColor", "slots"],
          "properties": {
            "name": { "type": "string" },
            "backgroundColor": { "type": "string" },
            "textColor": { "type": "string" },
            "accentColor": { "type": "string" },
            "fontFamily": { "type": "string" },
            "slots": {
              "type": "array",
              "items": {
                "type": "object",
                "required": ["id", "content"],
                "properties": {
                  "id": { "type": "string" },
                  "content": { "type": "string" },
                  "fontSizeSp": { "type": "number" },
                  "fontWeight": { "type": "integer" },
                  "alignment": { "type": "string" }
                },
                "additionalProperties": false
              }
            }
          },
          "additionalProperties": false
        }
    """.trimIndent()
}

object AiPrompts {

    const val OutlineSystemPrompt: String = """You are an expert academic curriculum analyst. Given study material with explicit [PAGE N] markers (or an attached PDF), partition the material into a logical sequence of study lessons, chapters, or topical sections.
Rules:
1. Every lesson MUST have a concise title, a 1-2 sentence summary of core concepts covered, and accurate 1-indexed startPage and endPage integers (endPage >= startPage).
2. Skip boilerplate front-matter (copyright, blank pages, table of contents) and back-matter (bibliography, index) unless they contain substantive study content.
3. Respond ONLY with valid JSON matching the required schema. Do not wrap in markdown code fences."""

    const val FactsSystemPrompt: String = """You are an elite scientific and academic study-card creator. Extract high-retention, self-contained study facts from the provided lesson material for a mobile lock-screen/home-screen wallpaper app.
Rules:
1. Each fact's "text" MUST be self-contained, accurate, and glanceable (80 to 260 characters). Never write vague references like "As shown in Figure 2" or "In this section".
2. Choose the best "templateType":
   - "DEFINITION" for key terms ("Term: precise definition").
   - "QA" for active-recall question & answer pairs ("Q: ... \nA: ...").
   - "BULLET" for 2-4 compact bullet points separated by "\n• ".
   - "QUOTE" for memorable laws, theorems, or foundational principles.
   - "FORMULA" when an equation is central (set "requiresLatex": true and put raw KaTeX math without surrounding $$ delimiters in "latex").
   - "DIAGRAM" when a process, hierarchy, cycle, or structure is best understood visually (set "diagramType" to "MERMAID" or "SVG" and populate "diagramSource" with valid standalone Mermaid or SVG markup).
   - "TIMELINE" for chronological steps or historical sequences.
   - "TEXT" for crisp declarative explanations.
3. Attribute every fact to its exact 1-indexed "sourcePage" from the [PAGE N] markers and "sourceLessonTitle".
4. Provide 1 to 4 lowercase topical "tags".
5. Respond ONLY with valid JSON matching the schema."""

    const val JsonRepairSystemPrompt: String = """You are a deterministic JSON repair engine. You are given a target JSON Schema and a malformed or markdown-wrapped JSON response.
Return ONLY the repaired, syntactically valid JSON object that strictly conforms to the provided JSON Schema. Do not add commentary or markdown code fences."""

    const val FormulaVerificationSystemPrompt: String = """Verify whether the provided LaTeX formula or diagram markup (Mermaid / TikZ / SVG / HTML) is syntactically valid, mathematically coherent, and free of any script injection or external network dependencies.
Set "valid": true only if it will render cleanly in KaTeX / Mermaid.js / TikZJax / SVG without syntax errors."""

    const val DiagramSystemPrompt: String = """Generate a clean, compact, high-contrast academic diagram for a mobile study card (aspect ratio ~16:10).
Prefer "MERMAID" (flowchart TD/LR, sequenceDiagram, stateDiagram-v2) or standalone inline "SVG" (with viewBox="0 0 600 380", transparent background, and legible stroke/text colors). Use "TIKZ" (\begin{tikzpicture}...\end{tikzpicture}) only for physics/chemistry/math vector diagrams. Never include <script>, external images, or external fonts."""

    const val TemplateSystemPrompt: String = """You are a mobile typography and card layout designer. Given the user's style description, output a valid CardTemplateSpec JSON object defining colors, typography, padding, border, and slot arrangement for a mobile study wallpaper card. Ensure high text contrast (WCAG AA+) and clock-safe top spacing."""

    fun outlineUserPrompt(pages: List<PageText>, suffix: String? = null): String =
        outlineUserPromptFromText(TextChunker.formatPages(pages), suffix)

    fun outlineUserPromptFromText(material: String, suffix: String? = null): String = buildString {
        append("Partition the following study material into lessons. Use the exact page numbers from the [PAGE N] markers.\n")
        append(material)
        append("\nReturn JSON with a \"lessons\" array.")
        appendSuffix(suffix)
    }

    fun factsUserPrompt(
        lessonTitle: String,
        pages: List<PageText>,
        density: FactDensity,
        includeFormulas: Boolean,
        includeDiagrams: Boolean,
        suffix: String? = null,
    ): String = factsUserPromptFromText(
        lessonTitle = lessonTitle,
        material = TextChunker.formatPages(pages),
        density = density,
        includeFormulas = includeFormulas,
        includeDiagrams = includeDiagrams,
        suffix = suffix,
    )

    fun factsUserPromptFromText(
        lessonTitle: String,
        material: String,
        density: FactDensity,
        includeFormulas: Boolean,
        includeDiagrams: Boolean,
        suffix: String? = null,
    ): String = buildString {
        append("Lesson: ")
        append(lessonTitle)
        append("\nProduce between ")
        append(density.minFactsPerLesson)
        append(" and ")
        append(density.maxFactsPerLesson)
        append(" facts.\n")
        append("Include LaTeX formulas: ")
        append(includeFormulas)
        append(". Include visual diagrams: ")
        append(includeDiagrams)
        append(".\n")
        if (!includeFormulas) append("Do not return FORMULA facts; explain equations in plain text.\n")
        if (!includeDiagrams) append("Do not return DIAGRAM facts or diagramSource values.\n")
        append(material)
        append("\nReturn JSON with a \"facts\" array.")
        appendSuffix(suffix)
    }

    fun repairUserPrompt(schema: String, malformed: String): String = buildString {
        append("Target JSON Schema:\n")
        append(schema)
        append("\n\nMalformed response:\n")
        append(malformed.take(24_000))
        append("\n\nReturn only the repaired JSON object.")
    }

    fun formulaVerificationUserPrompt(source: String, kind: String): String =
        "Kind: $kind\nSource:\n${source.take(12_000)}\n\nReturn JSON {\"valid\":boolean,\"reason\":string|null}."

    fun diagramUserPrompt(description: String, kindHint: String? = null): String = buildString {
        append("Create a diagram for this study fact:\n")
        append(description)
        if (!kindHint.isNullOrBlank()) append("\nPreferred format: $kindHint")
        append("\nReturn JSON with diagramType, diagramSource and caption.")
    }

    fun templateUserPrompt(description: String): String =
        "Style description:\n$description\n\nReturn a valid CardTemplateSpec JSON object."

    fun schemaInstruction(schema: String): String =
        "\n\nRespond ONLY with a single JSON object that strictly matches this JSON Schema. " +
            "Do not use markdown fences.\n$schema"

    private fun StringBuilder.appendSuffix(suffix: String?) {
        if (!suffix.isNullOrBlank()) {
            append("\nAdditional user instruction: ")
            append(suffix.trim())
        }
    }
}
