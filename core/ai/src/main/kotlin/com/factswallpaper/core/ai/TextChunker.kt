package com.factswallpaper.core.ai

import kotlin.math.ceil

data class PageText(
    val pageNumber: Int,
    val text: String,
)

data class TextChunk(
    val text: String,
    val startPage: Int,
    val endPage: Int,
    val pageNumbers: List<Int>,
)

data class FactDraft(
    val text: String,
    val latex: String? = null,
    val requiresLatex: Boolean = false,
    val templateType: String = "TEXT",
    val diagramType: String = "NONE",
    val diagramSource: String? = null,
    val sourceLessonTitle: String? = null,
    val sourcePage: Int? = null,
    val tags: List<String> = emptyList(),
    val confidence: Float = 0.8f,
)

/**
 * Semantic chunker from spec §6.5. Pages are formatted with explicit
 * `--- [PAGE N] ---` markers; chunks split on paragraph boundaries and carry a
 * one-paragraph overlap so facts spanning a boundary are still captured.
 */
object TextChunker {

    const val OVERLAP_CHARS = 600

    fun maxChunkTokens(maxContextTokens: Int): Int =
        (maxContextTokens * 0.55).toInt().coerceIn(4_000, 120_000)

    fun formatPages(pages: List<PageText>): String = buildString {
        pages.forEach { page ->
            append("\n--- [PAGE ${page.pageNumber}] ---\n")
            append(page.text.trim())
            append('\n')
        }
    }

    fun chunk(pages: List<PageText>, maxContextTokens: Int): List<TextChunk> {
        if (pages.isEmpty()) return emptyList()
        val limit = maxChunkTokens(maxContextTokens)
        val formatted = formatPages(pages)
        if (CostEstimator.estimateTextTokens(formatted) <= limit) {
            return listOf(
                TextChunk(
                    text = formatted,
                    startPage = pages.minOf { it.pageNumber },
                    endPage = pages.maxOf { it.pageNumber },
                    pageNumbers = pages.map { it.pageNumber }.distinct().sorted(),
                ),
            )
        }

        data class Unit(val page: Int, val text: String, val firstOfPage: Boolean)

        val units = buildList {
            pages.sortedBy { it.pageNumber }.forEach { page ->
                val paragraphs = page.text.split(Regex("\\n\\s*\\n"))
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .ifEmpty { listOf(page.text.trim()) }
                paragraphs.forEachIndexed { index, paragraph ->
                    splitOversized(paragraph, limit).forEachIndexed { sliceIndex, slice ->
                        add(
                            Unit(
                                page = page.pageNumber,
                                text = slice,
                                firstOfPage = index == 0 && sliceIndex == 0,
                            ),
                        )
                    }
                }
            }
        }

        val chunks = mutableListOf<TextChunk>()
        val current = StringBuilder()
        val currentPages = linkedSetOf<Int>()
        var currentStart: Int? = null

        fun flush(overlapUnit: Unit?) {
            if (current.isBlank()) return
            val pageNumbers = currentPages.toList()
            chunks += TextChunk(
                text = current.toString().trim(),
                startPage = currentStart ?: pageNumbers.first(),
                endPage = pageNumbers.lastOrNull() ?: (currentStart ?: 1),
                pageNumbers = pageNumbers,
            )
            current.clear()
            currentPages.clear()
            currentStart = null
            if (overlapUnit != null) {
                val overlapText = overlapUnit.text.takeLast(OVERLAP_CHARS)
                if (overlapUnit.firstOfPage) {
                    current.append("--- [PAGE ${overlapUnit.page}] ---\n")
                }
                current.append(overlapText)
                currentPages += overlapUnit.page
                currentStart = overlapUnit.page
            }
        }

        var previous: Unit? = null
        units.forEach { unit ->
            val block = buildString {
                if (unit.firstOfPage) append("--- [PAGE ${unit.page}] ---\n")
                append(unit.text)
            }
            val separator = if (current.isEmpty()) "" else "\n\n"
            val candidate = current.toString() + separator + block
            if (CostEstimator.estimateTextTokens(candidate) > limit && current.isNotEmpty()) {
                flush(previous)
            }
            if (current.isNotEmpty()) current.append("\n\n")
            current.append(block)
            currentPages += unit.page
            if (currentStart == null) currentStart = unit.page
            previous = unit
        }
        flush(null)
        return chunks.ifEmpty {
            listOf(
                TextChunk(
                    text = formatted,
                    startPage = pages.minOf { it.pageNumber },
                    endPage = pages.maxOf { it.pageNumber },
                    pageNumbers = pages.map { it.pageNumber }.distinct().sorted(),
                ),
            )
        }
    }

    /**
     * Splits a single oversized paragraph so no unit can exceed the token
     * budget. Prefers sentence boundaries, then word boundaries, then a hard
     * cut; the page marker is attached to the first slice by the caller.
     */
    private fun splitOversized(paragraph: String, maxTokens: Int): List<String> {
        val charLimit = (maxTokens * 4).coerceAtLeast(400)
        if (paragraph.length <= charLimit) return listOf(paragraph)
        val slices = mutableListOf<String>()
        var remaining = paragraph.trim()
        while (remaining.length > charLimit) {
            val window = remaining.substring(0, charLimit)
            val sentenceCut = window.lastIndexOfAny(charArrayOf('.', '!', '?', '\n'))
            val wordCut = window.lastIndexOf(' ')
            val cut = when {
                sentenceCut >= charLimit / 2 -> sentenceCut + 1
                wordCut >= charLimit / 2 -> wordCut
                else -> charLimit
            }
            slices += remaining.substring(0, cut).trim()
            remaining = remaining.substring(cut).trim()
        }
        if (remaining.isNotEmpty()) slices += remaining
        return slices.ifEmpty { listOf(paragraph) }
    }

    /** Word-bigram Jaccard similarity used by the fact deduplicator. */
    fun similarity(left: String, right: String): Double {
        val a = normalize(left)
        val b = normalize(right)
        if (a == b) return 1.0
        val aWords = a.split(' ').filter { it.isNotEmpty() }
        val bWords = b.split(' ').filter { it.isNotEmpty() }
        if (aWords.size < 2 || bWords.size < 2) return 0.0
        val aBigrams = aWords.zipWithNext { x, y -> "$x $y" }.toSet()
        val bBigrams = bWords.zipWithNext { x, y -> "$x $y" }.toSet()
        if (aBigrams.isEmpty() || bBigrams.isEmpty()) return 0.0
        val intersection = aBigrams.intersect(bBigrams).size
        val union = aBigrams.union(bBigrams).size
        return if (union == 0) 0.0 else intersection.toDouble() / union
    }

    fun deduplicate(
        facts: List<FactDraft>,
        threshold: Double = 0.82,
        existing: List<String> = emptyList(),
    ): List<FactDraft> {
        val kept = mutableListOf<FactDraft>()
        val keptTexts = existing.toMutableList()
        facts.forEach { candidate ->
            val duplicateIndex = keptTexts.indexOfFirst { similarity(it, candidate.text) >= threshold }
            if (duplicateIndex < 0) {
                kept += candidate
                keptTexts += candidate.text
            } else if (duplicateIndex < kept.size) {
                val current = kept[duplicateIndex]
                if (candidate.confidence > current.confidence) {
                    kept[duplicateIndex] = candidate
                    keptTexts[duplicateIndex] = candidate.text
                }
            }
        }
        return kept
    }

    fun normalize(text: String): String =
        text.lowercase()
            .replace(Regex("[^a-z0-9 ]"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
}
