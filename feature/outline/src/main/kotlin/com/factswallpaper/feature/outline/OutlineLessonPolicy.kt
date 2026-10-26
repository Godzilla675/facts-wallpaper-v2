package com.factswallpaper.feature.outline

import com.factswallpaper.core.model.Lesson

/**
 * Pure lesson curation rules from spec F6. Kept out of the ViewModel so merge
 * adjacency and split boundaries can be unit-tested without Android.
 */
object OutlineLessonPolicy {

    fun canMerge(selected: List<Lesson>): Boolean {
        if (selected.size < 2) return false
        val sorted = selected.sortedBy { it.startPage }
        return sorted.zipWithNext().all { (left, right) ->
            right.startPage <= left.endPage + 1
        }
    }

    fun merge(selected: List<Lesson>): Lesson? {
        if (!canMerge(selected)) return null
        val sorted = selected.sortedBy { it.startPage }
        val target = sorted.first()
        return target.copy(
            startPage = sorted.minOf { it.startPage },
            endPage = sorted.maxOf { it.endPage },
            summary = sorted.map { it.summary }
                .filter { it.isNotBlank() }
                .distinct()
                .joinToString(" "),
            factCount = sorted.sumOf { it.factCount },
        )
    }

    fun split(lesson: Lesson, atPage: Int, firstId: String, secondId: String): Pair<Lesson, Lesson>? {
        if (atPage <= lesson.startPage || atPage > lesson.endPage) return null
        val first = lesson.copy(
            id = firstId,
            title = "${lesson.title} (1)",
            endPage = atPage - 1,
            factCount = 0,
        )
        val second = lesson.copy(
            id = secondId,
            title = "${lesson.title} (2)",
            startPage = atPage,
            sortOrder = lesson.sortOrder + 1,
            factCount = 0,
        )
        return first to second
    }
}
