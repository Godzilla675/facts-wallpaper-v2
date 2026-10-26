package com.factswallpaper.core.render

import java.security.MessageDigest

/** SHA-256 helpers shared by the render cache, diagram assets and verification. */
object RenderHash {

    fun sha256Hex(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString(separator = "") { byte -> "%02x".format(byte) }

    /** Content hash of the fact fields that affect rendered pixels (§7.5). */
    fun factContentHash(
        text: String,
        latex: String?,
        diagramAssetId: String?,
        sourceBookTitle: String,
        sourceLessonTitle: String?,
        sourcePage: Int?,
    ): String = sha256Hex(
        "$text|${latex.orEmpty()}|${diagramAssetId.orEmpty()}|$sourceBookTitle|" +
            "${sourceLessonTitle.orEmpty()}|${sourcePage ?: 0}",
    )

    /** Content-addressed diagram identity: kind + markup + theme. */
    fun diagramHash(kind: String, sourceMarkup: String, themeKey: String): String =
        sha256Hex("$kind$sourceMarkup$themeKey")
}
