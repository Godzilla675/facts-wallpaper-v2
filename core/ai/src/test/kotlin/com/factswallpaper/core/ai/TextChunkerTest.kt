package com.factswallpaper.core.ai

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextChunkerTest {

    @Test
    fun `max chunk tokens follows the 55 percent rule with clamps`() {
        assertThat(TextChunker.maxChunkTokens(1_000)).isEqualTo(4_000)
        assertThat(TextChunker.maxChunkTokens(100_000)).isEqualTo(55_000)
        assertThat(TextChunker.maxChunkTokens(1_000_000)).isEqualTo(120_000)
    }

    @Test
    fun `format pages emits exact markers`() {
        val formatted = TextChunker.formatPages(
            listOf(PageText(1, "First page"), PageText(2, "Second page")),
        )

        assertThat(formatted).contains("--- [PAGE 1] ---")
        assertThat(formatted).contains("--- [PAGE 2] ---")
        assertThat(formatted).contains("First page")
    }

    @Test
    fun `small documents stay in one chunk with page numbers`() {
        val pages = listOf(PageText(1, "a".repeat(100)), PageText(2, "b".repeat(100)))

        val chunks = TextChunker.chunk(pages, maxContextTokens = 100_000)

        assertThat(chunks).hasSize(1)
        assertThat(chunks[0].pageNumbers).containsExactly(1, 2).inOrder()
        assertThat(chunks[0].startPage).isEqualTo(1)
        assertThat(chunks[0].endPage).isEqualTo(2)
    }

    @Test
    fun `large documents split on paragraph boundaries with overlap`() {
        val pages = (1..10).map { page ->
            PageText(
                pageNumber = page,
                text = (1..8).joinToString("\n\n") { paragraph ->
                    "Page $page paragraph $paragraph " + "word ".repeat(120)
                },
            )
        }

        val chunks = TextChunker.chunk(pages, maxContextTokens = 10_000)

        assertThat(chunks.size).isGreaterThan(1)
        chunks.forEach { chunk ->
            assertThat(chunk.pageNumbers).isNotEmpty()
            assertThat(chunk.startPage).isAtMost(chunk.endPage)
            assertThat(CostEstimator.estimateTextTokens(chunk.text))
                .isAtMost(TextChunker.maxChunkTokens(10_000) + 400)
        }
        val allPages = chunks.flatMap { it.pageNumbers }.distinct().sorted()
        assertThat(allPages).containsExactlyElementsIn((1..10).toList())
    }

    @Test
    fun `oversized single paragraph is sliced instead of overflowing`() {
        val page = PageText(1, "x".repeat(120_000))

        val chunks = TextChunker.chunk(listOf(page), maxContextTokens = 10_000)

        assertThat(chunks.size).isGreaterThan(1)
        assertThat(chunks.all { it.pageNumbers == listOf(1) }).isTrue()
        assertThat(chunks.all { CostEstimator.estimateTextTokens(it.text) <= 6_200 }).isTrue()
    }

    @Test
    fun `similarity uses normalized word bigrams`() {
        val a = "Photosynthesis converts light energy into chemical energy."
        val b = "photosynthesis converts light energy into chemical energy"

        assertThat(TextChunker.similarity(a, b)).isGreaterThan(0.9)
        assertThat(TextChunker.similarity("entropy always increases", "mitochondria is the powerhouse"))
            .isLessThan(0.2)
    }

    @Test
    fun `deduplicate keeps the higher confidence duplicate`() {
        val low = FactDraft(text = "Entropy of an isolated system never decreases.", confidence = 0.5f)
        val high = FactDraft(text = "Entropy of an isolated system never decreases.", confidence = 0.9f)

        val kept = TextChunker.deduplicate(listOf(low, high))

        assertThat(kept).hasSize(1)
        assertThat(kept[0].confidence).isEqualTo(0.9f)
    }

    @Test
    fun `deduplicate removes facts already present in the database`() {
        val existing = listOf("The mitochondria is the powerhouse of the cell.")
        val candidate = FactDraft(text = "The mitochondria is the powerhouse of the cell.")

        assertThat(TextChunker.deduplicate(listOf(candidate), existing = existing)).isEmpty()
    }

    @Test
    fun `deduplicate keeps genuinely different facts`() {
        val facts = listOf(
            FactDraft(text = "Newton's second law states that force equals mass times acceleration."),
            FactDraft(text = "The derivative of sine is cosine."),
        )

        assertThat(TextChunker.deduplicate(facts)).hasSize(2)
    }
}
