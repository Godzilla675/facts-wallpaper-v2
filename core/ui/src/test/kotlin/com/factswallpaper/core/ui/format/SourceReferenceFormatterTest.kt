package com.factswallpaper.core.ui.format

import com.factswallpaper.core.model.SourceReferenceMode
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SourceReferenceFormatterTest {

    @Test
    fun `full mode includes book lesson and page`() {
        assertThat(
            SourceReferenceFormatter.format(
                bookTitle = "Physics",
                lessonTitle = "Relativity",
                page = 42,
                mode = SourceReferenceMode.FULL,
            )
        ).isEqualTo("Physics · Relativity · p. 42")
    }

    @Test
    fun `compact mode includes book and page but not lesson`() {
        assertThat(
            SourceReferenceFormatter.format(
                bookTitle = "Physics",
                lessonTitle = "Relativity",
                page = 42,
                mode = SourceReferenceMode.COMPACT,
            )
        ).isEqualTo("Physics · p. 42")
    }

    @Test
    fun `page only mode falls back to the book when no page exists`() {
        assertThat(
            SourceReferenceFormatter.format(
                bookTitle = "Physics",
                lessonTitle = "Relativity",
                page = 42,
                mode = SourceReferenceMode.PAGE_ONLY,
            )
        ).isEqualTo("p. 42")
        assertThat(
            SourceReferenceFormatter.format(
                bookTitle = "Physics",
                lessonTitle = null,
                page = null,
                mode = SourceReferenceMode.PAGE_ONLY,
            )
        ).isEqualTo("Physics")
    }

    @Test
    fun `hidden mode always returns an empty string`() {
        assertThat(
            SourceReferenceFormatter.format(
                bookTitle = "Physics",
                lessonTitle = "Relativity",
                page = 42,
                mode = SourceReferenceMode.HIDDEN,
            )
        ).isEmpty()
    }

    @Test
    fun `blank and missing parts are omitted cleanly`() {
        assertThat(
            SourceReferenceFormatter.format(
                bookTitle = "  ",
                lessonTitle = "  ",
                page = 0,
                mode = SourceReferenceMode.FULL,
                unknownBook = "Unknown source",
            )
        ).isEqualTo("Unknown source")

        assertThat(
            SourceReferenceFormatter.format(
                bookTitle = "Physics",
                lessonTitle = null,
                page = null,
                mode = SourceReferenceMode.FULL,
            )
        ).isEqualTo("Physics")
    }

    @Test
    fun `custom labels are honoured by callers`() {
        assertThat(
            SourceReferenceFormatter.format(
                bookTitle = "Physics",
                lessonTitle = "Relativity",
                page = 7,
                mode = SourceReferenceMode.FULL,
                unknownBook = "Unknown",
                pagePrefix = "page ",
            )
        ).isEqualTo("Physics · Relativity · page 7")
    }
}
