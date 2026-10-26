package com.factswallpaper.core.model

import kotlinx.serialization.Serializable

@Serializable
data class Lesson(
    val id: String,
    val documentId: String,
    val title: String,
    val summary: String,
    val startPage: Int,
    val endPage: Int,
    val selected: Boolean = true,
    val sortOrder: Int,
    val factCount: Int = 0,
) {
    val pageRangeLabel: String get() = "$startPage–$endPage"
}
