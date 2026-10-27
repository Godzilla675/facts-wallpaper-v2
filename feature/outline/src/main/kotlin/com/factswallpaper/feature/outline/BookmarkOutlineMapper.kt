package com.factswallpaper.feature.outline

import com.factswallpaper.core.ai.OutlineDraft
import com.factswallpaper.core.pdf.PdfBookmark

internal object BookmarkOutlineMapper {

    fun map(
        bookmarks: List<PdfBookmark>,
        pageRangeStart: Int,
        pageRangeEnd: Int,
    ): List<OutlineDraft> =
        com.factswallpaper.core.pdf.BookmarkOutlineMapper.map(
            bookmarks = bookmarks,
            pageRangeStart = pageRangeStart,
            pageRangeEnd = pageRangeEnd,
        )
}
