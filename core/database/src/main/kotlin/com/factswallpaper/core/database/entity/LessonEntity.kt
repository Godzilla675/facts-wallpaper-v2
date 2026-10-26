package com.factswallpaper.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 4. AI-detected or user-edited outline lesson. */
@Entity(
    tableName = "lessons",
    foreignKeys = [
        ForeignKey(
            entity = PdfDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId"), Index(value = ["documentId", "sortOrder"])]
)
data class LessonEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val title: String,
    val summary: String,
    val startPage: Int,
    val endPage: Int,
    val selected: Boolean = true,
    val sortOrder: Int,
    val factCount: Int = 0,
)
