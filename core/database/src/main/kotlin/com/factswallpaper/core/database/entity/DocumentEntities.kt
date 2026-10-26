package com.factswallpaper.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** 1. Imported PDF document. */
@Entity(
    tableName = "pdf_documents",
    indices = [Index(value = ["fileHash"]), Index(value = ["createdAtEpochMs"])]
)
data class PdfDocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val sourceUri: String,
    val localFilePath: String,
    val compiledOcrTextPath: String? = null,
    val fileHash: String,
    val fileSizeBytes: Long,
    val pageCount: Int,
    val pagesWithText: Int,
    val processingRoute: String,
    val ocrEngine: String?,
    val ocrLanguageCode: String = "eng",
    val ocrLastStartedPage: Int = 0,
    val ocrLastCompletedPage: Int = 0,
    val extractionStatus: String,
    val lastErrorMessage: String? = null,
    val includedInRotation: Boolean = true,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long = createdAtEpochMs,
)

/** 2. Per-document extraction and generation settings. */
@Entity(
    tableName = "document_settings",
    foreignKeys = [
        ForeignKey(
            entity = PdfDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class DocumentSettingsEntity(
    @PrimaryKey val documentId: String,
    val pageRangeStart: Int = 1,
    val pageRangeEnd: Int,
    val forceFullLocalOcr: Boolean = false,
    val preferDirectPdfUpload: Boolean = false,
    val overrideOcrPageLimit: Boolean = false,
    val ocrEngine: String = "ML_KIT",
    val ocrLanguageCode: String = "eng",
    val ocrExecutionMode: String = "BACKGROUND",
    val factDensity: String = "BALANCED",
    val includeFormulas: Boolean = true,
    val includeDiagrams: Boolean = true,
    val autoVerifyFormulas: Boolean = true,
    val preferredProviderProfileId: String? = null,
)

/** 3. Per-page extracted / OCR text chunks (checkpointed after each page). */
@Entity(
    tableName = "document_chunks",
    primaryKeys = ["documentId", "pageNumber"],
    foreignKeys = [
        ForeignKey(
            entity = PdfDocumentEntity::class,
            parentColumns = ["id"],
            childColumns = ["documentId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("documentId")]
)
data class DocumentChunkEntity(
    val documentId: String,
    val pageNumber: Int,
    val extractedText: String,
    val status: String = "COMPLETED",
    val isFromOcr: Boolean,
    val ocrConfidence: Float? = null,
    val updatedAtEpochMs: Long = System.currentTimeMillis(),
)
