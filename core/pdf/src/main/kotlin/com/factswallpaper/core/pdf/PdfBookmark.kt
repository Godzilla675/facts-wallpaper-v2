package com.factswallpaper.core.pdf

data class PdfBookmark(
    val title: String,
    val pageNumber: Int?,
    val level: Int,
)
