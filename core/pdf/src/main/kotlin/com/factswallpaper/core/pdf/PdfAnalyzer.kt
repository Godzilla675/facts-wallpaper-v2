package com.factswallpaper.core.pdf

import com.factswallpaper.core.model.PdfProcessingRoute
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

interface PdfMetadataReader {
    suspend fun read(
        file: File,
        sourceUri: String,
        displayName: String?,
        fileHash: String,
    ): PdfMetadata
}

@Singleton
class PdfBoxMetadataReader @Inject constructor(
    private val reader: PdfBoxDocumentReader,
) : PdfMetadataReader {
    override suspend fun read(
        file: File,
        sourceUri: String,
        displayName: String?,
        fileHash: String,
    ): PdfMetadata = reader.readMetadata(file, sourceUri, displayName, fileHash)
}

interface PdfTextExtractor {
    suspend fun extract(file: File, range: PdfPageRange): PdfExtractionResult
}

@Singleton
class PdfBoxTextExtractor @Inject constructor(
    private val reader: PdfBoxDocumentReader,
) : PdfTextExtractor {
    override suspend fun extract(file: File, range: PdfPageRange): PdfExtractionResult =
        reader.extractRange(file, range)
}

/**
 * Facade used by the import screen and workers: inspection plus the
 * deterministic route classifier from spec F3.
 */
@Singleton
class PdfAnalyzer @Inject constructor(
    private val metadataReader: PdfMetadataReader,
    private val classifier: PdfRouteClassifier,
) {
    suspend fun inspect(
        file: File,
        sourceUri: String,
        displayName: String?,
        fileHash: String,
    ): PdfMetadata = metadataReader.read(file, sourceUri, displayName, fileHash)

    fun classify(
        metadata: PdfMetadata,
        options: PdfProcessingOptions,
        capabilities: PdfProviderCapabilities,
        ocrPageLimit: Int,
    ): PdfRouteDecision = classifier.classify(metadata, options, capabilities, ocrPageLimit)

    fun routeForStoredMetadata(
        metadata: PdfMetadata,
        options: PdfProcessingOptions,
        capabilities: PdfProviderCapabilities,
        ocrPageLimit: Int,
    ): PdfProcessingRoute = classify(metadata, options, capabilities, ocrPageLimit).route
}
