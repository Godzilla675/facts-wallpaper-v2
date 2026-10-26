package com.factswallpaper.core.ui.format

import com.factswallpaper.core.model.SourceReferenceMode

/**
 * Pure provenance formatter shared by badges, the fact list and the wallpaper
 * mock-up. Keeping it free of Android resources makes it directly unit-testable.
 *
 * Output follows spec 9.3: `Book · Lesson · p. 42`, with missing parts omitted.
 */
object SourceReferenceFormatter {

    const val SEPARATOR: String = " \u00B7 "

    fun format(
        bookTitle: String?,
        lessonTitle: String?,
        page: Int?,
        mode: SourceReferenceMode,
        unknownBook: String = "Unknown source",
        pagePrefix: String = "p. ",
    ): String {
        if (mode == SourceReferenceMode.HIDDEN) return ""

        val book = bookTitle?.trim().orEmpty().ifEmpty { unknownBook }
        val lesson = lessonTitle?.trim()?.takeIf { it.isNotEmpty() }
        val pageLabel = page?.takeIf { it > 0 }?.let { "$pagePrefix$it" }

        return when (mode) {
            SourceReferenceMode.FULL ->
                listOfNotNull(book, lesson, pageLabel).joinToString(SEPARATOR)

            SourceReferenceMode.COMPACT ->
                listOfNotNull(book, pageLabel).joinToString(SEPARATOR)

            SourceReferenceMode.PAGE_ONLY ->
                pageLabel ?: book

            SourceReferenceMode.HIDDEN -> ""
        }
    }
}
