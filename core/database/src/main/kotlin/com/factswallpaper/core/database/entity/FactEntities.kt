package com.factswallpaper.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 5. Extracted or user-created study fact card. */
@Entity(
    tableName = "facts",
    foreignKeys = [
        ForeignKey(
            entity = PdfDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = LessonEntity::class,
            parentColumns = ["id"],
            childColumns = ["lessonId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index("documentId"),
        Index("lessonId"),
        Index("active"),
        Index("favorite"),
        Index("templateType")
    ]
)
data class FactItemEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val lessonId: String?,
    val text: String,
    val latex: String? = null,
    val requiresLatex: Boolean = false,
    val templateType: String = "TEXT",
    val preferredTemplateId: String? = null,
    val diagramType: String = "NONE",
    val diagramSource: String? = null,
    val diagramAssetId: String? = null,
    val sourceReferenceModeOverride: String? = null,
    val regenerationInstruction: String? = null,
    val previousText: String? = null,
    val sourceBookTitle: String,
    val sourceLessonTitle: String?,
    val sourcePage: Int?,
    val tagsCsv: String = "",
    val confidence: Float = 1.0f,
    val verificationStatus: String = "UNVERIFIED",
    val active: Boolean = true,
    val favorite: Boolean = false,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs,
    val reviewGotItCount: Int = 0,
    val reviewAgainCount: Int = 0,
)

/** 6. Normalized fact tag cross-reference for multi-tag filtering. */
@Entity(
    tableName = "fact_tags",
    primaryKeys = ["factId", "tag"],
    foreignKeys = [
        ForeignKey(
            entity = FactItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["factId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("tag"), Index("factId")]
)
data class FactTagEntity(
    val factId: String,
    val tag: String,
)
