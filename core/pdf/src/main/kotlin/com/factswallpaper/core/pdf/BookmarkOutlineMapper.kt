package com.factswallpaper.core.pdf

import com.factswallpaper.core.ai.OutlineDraft

object BookmarkOutlineMapper {

    fun map(
        bookmarks: List<PdfBookmark>,
        pageRangeStart: Int,
        pageRangeEnd: Int,
    ): List<OutlineDraft> {
        if (pageRangeStart < 1 || pageRangeEnd < pageRangeStart) return emptyList()
        val valid = bookmarks.mapNotNull { bookmark ->
            val page = bookmark.pageNumber ?: return@mapNotNull null
            if (bookmark.title.isBlank() || bookmark.level < 0) return@mapNotNull null
            IndexedBookmark(bookmark.copy(pageNumber = page))
        }
        val ancestors = mutableListOf<PdfBookmark>()
        val leaves = mutableListOf<BookmarkLesson>()
        valid.forEachIndexed { index, indexed ->
            val bookmark = indexed.bookmark
            val bookmarkPage = bookmark.pageNumber ?: return@forEachIndexed
            while (ancestors.size > bookmark.level) ancestors.removeAt(ancestors.lastIndex)
            while (ancestors.size < bookmark.level) {
                ancestors += ancestors.lastOrNull() ?: bookmark
            }
            val hasChildren = valid.getOrNull(index + 1)?.bookmark?.level?.let { it > bookmark.level } == true
            val parent = ancestors.firstOrNull()
            if (!hasChildren) {
                val rawStart = if (bookmark.level > 0 && parent != null &&
                    leaves.none { it.chapterTitle == parent.title }
                ) {
                    minOf(bookmarkPage, parent.pageNumber ?: bookmarkPage)
                } else {
                    bookmarkPage
                }
                leaves += BookmarkLesson(
                    title = bookmark.title,
                    chapterTitle = parent?.title?.takeIf { bookmark.level > 0 },
                    startPage = rawStart,
                )
            }
            if (ancestors.size == bookmark.level) ancestors += bookmark else ancestors[bookmark.level] = bookmark
        }

        val orderedLeaves = leaves.sortedBy { it.startPage }
        return orderedLeaves.mapIndexedNotNull { index, lesson ->
            val nextPage = orderedLeaves.drop(index + 1)
                .firstOrNull { it.startPage > lesson.startPage }
                ?.startPage
            val start = lesson.startPage.coerceAtLeast(pageRangeStart)
            val end = ((nextPage ?: (pageRangeEnd + 1)) - 1).coerceAtMost(pageRangeEnd)
            if (start > pageRangeEnd || end < start) {
                null
            } else {
                OutlineDraft(
                    title = lesson.title,
                    summary = "Section from the PDF's embedded bookmarks.",
                    startPage = start,
                    endPage = end,
                    sortOrder = index + 1,
                    chapterTitle = lesson.chapterTitle,
                )
            }
        }
    }

    private data class IndexedBookmark(
        val bookmark: PdfBookmark,
    )

    private data class BookmarkLesson(
        val title: String,
        val chapterTitle: String?,
        val startPage: Int,
    )
}
