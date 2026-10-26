package com.factswallpaper.core.render.diagram

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DiagramValidatorTest {

    @Test
    fun `rejects script tags even when they are entity encoded`() {
        assertThat(DiagramValidator.isValid(MarkupKind.SVG, "<svg><script>alert(1)</script></svg>"))
            .isFalse()
        assertThat(DiagramValidator.isValid(MarkupKind.HTML_CANVAS, "&#x3C;script&#x3E;alert(1)"))
            .isFalse()
    }

    @Test
    fun `rejects javascript urls and inline event handlers`() {
        val cases = listOf(
            MarkupKind.SVG to "<svg><a href=\"javascript:alert(1)\">x</a></svg>",
            MarkupKind.SVG to "<svg><a href=\"JaVaScRiPt:alert(1)\">x</a></svg>",
            MarkupKind.SVG to "<svg onload=\"alert(1)\"></svg>",
            MarkupKind.HTML_CANVAS to "<canvas/onclick=alert(1)>",
            MarkupKind.MERMAID to "graph TD; click A javascript:alert(1)",
        )

        cases.forEach { (kind, source) ->
            assertThat(DiagramValidator.validate(kind, source))
                .isInstanceOf(DiagramValidator.Result.Invalid::class.java)
        }
    }

    @Test
    fun `rejects embedding, external stylesheet and external css import attacks`() {
        val cases = listOf(
            MarkupKind.SVG to "<svg><iframe src=\"https://evil.test\"></iframe></svg>",
            MarkupKind.SVG to "<svg><object data=\"x\"></object></svg>",
            MarkupKind.SVG to "<svg><embed src=\"x\"></svg>",
            MarkupKind.HTML_CANVAS to "<link rel=\"stylesheet\" href=\"https://evil.test/x.css\">",
            MarkupKind.HTML_CANVAS to "<style>@import url(https://evil.test/x.css);</style>",
            MarkupKind.HTML_CANVAS to "data:text/html;base64,PHNjcmlwdD4=",
        )

        cases.forEach { (kind, source) ->
            assertThat(DiagramValidator.isValid(kind, source)).isFalse()
        }
    }

    @Test
    fun `rejects latex file access commands`() {
        listOf(
            "\\input{/etc/passwd}",
            "\\include{secret}",
            "\\write18{rm -rf /}",
            "\\openout1=evil",
            "\\csname relax\\endcsname",
        ).forEach { source ->
            assertThat(DiagramValidator.validate(MarkupKind.LATEX, source))
                .isInstanceOf(DiagramValidator.Result.Invalid::class.java)
        }
    }

    @Test
    fun `rejects structurally broken latex`() {
        assertThat(DiagramValidator.isValid(MarkupKind.LATEX, "\\frac{1}{2")).isFalse()
        assertThat(DiagramValidator.isValid(MarkupKind.LATEX, "\\left( x")).isFalse()
        assertThat(DiagramValidator.isValid(MarkupKind.LATEX, "\\begin{align} x")).isFalse()
        assertThat(DiagramValidator.isValid(MarkupKind.LATEX, "\\begin{align} x \\end{matrix}")).isFalse()
    }

    @Test
    fun `accepts safe svg, latex, mermaid and tikz markup`() {
        val svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><circle cx=\"5\" cy=\"5\" r=\"4\"/></svg>"
        val latex = "\\frac{a^2 + b^2}{c^2} = \\left( \\frac{x}{y} \\right)"
        val mermaid = "graph TD; A[Input] --> B[Output]"
        val tikz = "\\begin{tikzpicture}\\draw (0,0) -- (1,1);\\end{tikzpicture}"

        assertThat(DiagramValidator.validate(MarkupKind.SVG, svg))
            .isEqualTo(DiagramValidator.Result.Valid)
        assertThat(DiagramValidator.isValid(MarkupKind.LATEX, latex)).isTrue()
        assertThat(DiagramValidator.isValid(MarkupKind.MERMAID, mermaid)).isTrue()
        assertThat(DiagramValidator.isValid(MarkupKind.TIKZ, tikz)).isTrue()
    }

    @Test
    fun `rejects empty markup and normalizes whitespace exactly once`() {
        assertThat(DiagramValidator.isValid(MarkupKind.SVG, "   \n\t ")).isFalse()

        val normalized = DiagramValidator.normalize("  \\frac{1}\u0000{2}  and\n\tmore  ")

        assertThat(normalized).isEqualTo("\\frac{1}{2} and more")
    }

    @Test
    fun `entities and control characters cannot hide forbidden fragments`() {
        val hidden = "&#x3C;iframe src=&#x22;https://evil.test&#x22;&#x3E;"

        assertThat(DiagramValidator.normalize(hidden))
            .isEqualTo("<iframe src=\"https://evil.test\">")
        assertThat(DiagramValidator.isValid(MarkupKind.SVG, hidden)).isFalse()
    }
}
