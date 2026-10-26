package com.factswallpaper.core.render.diagram

import com.factswallpaper.core.model.DiagramKind
import com.factswallpaper.core.model.Fact

/**
 * Markup kinds accepted by the offline rasterizer. [DiagramKind] deliberately has no
 * `LATEX` entry because formulas are a first-class fact attribute, not a diagram kind.
 */
enum class MarkupKind {
    LATEX,
    MERMAID,
    TIKZ,
    SVG,
    HTML_CANVAS,
    ;

    companion object {
        fun fromDiagramKind(kind: DiagramKind): MarkupKind? = when (kind) {
            DiagramKind.NONE -> null
            DiagramKind.MERMAID -> MERMAID
            DiagramKind.TIKZ -> TIKZ
            DiagramKind.SVG -> SVG
            DiagramKind.HTML_CANVAS -> HTML_CANVAS
        }

        /** Picks the stage markup a card should render, if any. */
        fun forFact(fact: Fact): MarkupKind? {
            if (fact.hasFormula) return LATEX
            return fromDiagramKind(fact.diagramType)
        }
    }
}

/** The source and kind passed to the offline renderer. */
data class StageMarkup(
    val kind: MarkupKind,
    val source: String,
) {
    companion object {
        fun of(fact: Fact): StageMarkup? {
            val kind = MarkupKind.forFact(fact) ?: return null
            val source = if (kind == MarkupKind.LATEX) fact.latex ?: fact.text else fact.diagramSource ?: return null
            if (source.isBlank()) return null
            return StageMarkup(kind, source)
        }
    }
}
