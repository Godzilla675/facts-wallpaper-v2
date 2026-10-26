package com.factswallpaper.core.database.dao

import com.factswallpaper.core.model.PdfProcessingRoute
import com.factswallpaper.core.model.VerificationStatus

data class ProcessingRouteCount(
    val processingRoute: PdfProcessingRoute,
    val count: Int,
)

data class VerificationStatusCount(
    val verificationStatus: VerificationStatus,
    val count: Int,
)

data class DocumentLessonCount(
    val documentId: String,
    val count: Int,
)

data class DocumentFactCount(
    val documentId: String,
    val count: Int,
)

data class CollectionFactCount(
    val collectionId: String,
    val count: Int,
)

data class FolderFactCount(
    val folderId: String,
    val count: Int,
)

data class FactTagCount(
    val tag: String,
    val count: Int,
)
