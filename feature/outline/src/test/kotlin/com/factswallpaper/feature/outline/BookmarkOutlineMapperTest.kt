package com.factswallpaper.feature.outline

import com.factswallpaper.core.pdf.PdfBookmark
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BookmarkOutlineMapperTest {

    @Test
    fun `maps nested bookmarks to chapter lessons with bounded page ranges`() {
        val lessons = BookmarkOutlineMapper.map(
            bookmarks = listOf(
                PdfBookmark("Chapter One", 2, 0),
                PdfBookmark("Foundations", 3, 1),
                PdfBookmark("Applications", 8, 1),
                PdfBookmark("Chapter Two", 14, 0),
                PdfBookmark("Extensions", 16, 1),
            ),
            pageRangeStart = 1,
            pageRangeEnd = 20,
        )

        assertThat(lessons.map { it.title })
            .containsExactly("Foundations", "Applications", "Extensions")
            .inOrder()
        assertThat(lessons.map { it.chapterTitle })
            .containsExactly("Chapter One", "Chapter One", "Chapter Two")
            .inOrder()
        assertThat(lessons.map { it.startPage to it.endPage })
            .containsExactly(2 to 7, 8 to 13, 14 to 20)
            .inOrder()
    }

    @Test
    fun `ignores bookmarks without destinations and clips to selected page range`() {
        val lessons = BookmarkOutlineMapper.map(
            bookmarks = listOf(
                PdfBookmark("No destination", null, 0),
                PdfBookmark("Early", 1, 0),
                PdfBookmark("Selected section", 5, 0),
            ),
            pageRangeStart = 4,
            pageRangeEnd = 7,
        )

        assertThat(lessons.map { it.title }).containsExactly("Early", "Selected section")
        assertThat(lessons.map { it.startPage to it.endPage })
            .containsExactly(4 to 4, 5 to 7)
            .inOrder()
    }
}
