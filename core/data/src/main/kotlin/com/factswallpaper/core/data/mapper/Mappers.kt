package com.factswallpaper.core.data.mapper

import com.factswallpaper.core.database.entity.AiRequestLogEntity
import com.factswallpaper.core.database.entity.CollectionEntity
import com.factswallpaper.core.database.entity.DocumentSettingsEntity
import com.factswallpaper.core.database.entity.FactItemEntity
import com.factswallpaper.core.database.entity.LessonEntity
import com.factswallpaper.core.database.entity.PdfDocumentEntity
import com.factswallpaper.core.database.entity.ProviderProfileEntity
import com.factswallpaper.core.database.entity.TemplateEntity
import com.factswallpaper.core.database.entity.UsageLedgerEntity
import com.factswallpaper.core.database.entity.UserFolderEntity
import com.factswallpaper.core.database.entity.WallpaperStateEntity
import com.factswallpaper.core.model.AiRequestLogEntry
import com.factswallpaper.core.model.AiTask
import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.Collection
import com.factswallpaper.core.model.CollectionSource
import com.factswallpaper.core.model.DiagramKind
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.ExtractionStatus
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.FactDensity
import com.factswallpaper.core.model.Lesson
import com.factswallpaper.core.model.OcrEngineType
import com.factswallpaper.core.model.OcrExecutionMode
import com.factswallpaper.core.model.PdfProcessingRoute
import com.factswallpaper.core.model.ProviderKeyStatus
import com.factswallpaper.core.model.ProviderProfile
import com.factswallpaper.core.model.ProviderType
import com.factswallpaper.core.model.RotationOrder
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.TemplateEngineKind
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.model.UsageEntry
import com.factswallpaper.core.model.UserFolder
import com.factswallpaper.core.model.VerificationStatus
import com.factswallpaper.core.model.WallpaperScheduleMode
import com.factswallpaper.core.model.WallpaperState
import com.factswallpaper.core.model.WallpaperTarget

internal inline fun <reified T : Enum<T>> enumOrDefault(value: String?, default: T): T =
    value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default

internal inline fun <reified T : Enum<T>> enumOrNull(value: String?): T? =
    value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }

fun parseTags(csv: String): List<String> =
    csv.split(',')
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()

fun List<String>.toTagsCsv(): String =
    map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .distinct()
        .joinToString(",")

fun PdfDocumentEntity.toDomain(lessonCount: Int = 0, factCount: Int = 0): Document = Document(
    id = id,
    title = title,
    sourceUri = sourceUri,
    localFilePath = localFilePath,
    compiledOcrTextPath = compiledOcrTextPath,
    fileHash = fileHash,
    fileSizeBytes = fileSizeBytes,
    pageCount = pageCount,
    pagesWithText = pagesWithText,
    processingRoute = enumOrDefault(processingRoute, PdfProcessingRoute.EXTRACT_TEXT),
    ocrEngine = enumOrNull<OcrEngineType>(ocrEngine),
    ocrLanguageCode = ocrLanguageCode,
    ocrLastStartedPage = ocrLastStartedPage,
    ocrLastCompletedPage = ocrLastCompletedPage,
    extractionStatus = ExtractionStatus.fromStorage(extractionStatus),
    lastErrorMessage = lastErrorMessage,
    includedInRotation = includedInRotation,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
    lessonCount = lessonCount,
    factCount = factCount,
)

fun Document.toEntity(): PdfDocumentEntity = PdfDocumentEntity(
    id = id,
    title = title,
    sourceUri = sourceUri,
    localFilePath = localFilePath,
    compiledOcrTextPath = compiledOcrTextPath,
    fileHash = fileHash,
    fileSizeBytes = fileSizeBytes,
    pageCount = pageCount,
    pagesWithText = pagesWithText,
    processingRoute = processingRoute.name,
    ocrEngine = ocrEngine?.name,
    ocrLanguageCode = ocrLanguageCode,
    ocrLastStartedPage = ocrLastStartedPage,
    ocrLastCompletedPage = ocrLastCompletedPage,
    extractionStatus = extractionStatus.name,
    lastErrorMessage = lastErrorMessage,
    includedInRotation = includedInRotation,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun DocumentSettingsEntity.toDomain(): DocumentSettings = DocumentSettings(
    documentId = documentId,
    pageRangeStart = pageRangeStart,
    pageRangeEnd = pageRangeEnd,
    forceFullLocalOcr = forceFullLocalOcr,
    preferDirectPdfUpload = preferDirectPdfUpload,
    overrideOcrPageLimit = overrideOcrPageLimit,
    ocrEngine = enumOrDefault(ocrEngine, OcrEngineType.ML_KIT),
    ocrLanguageCode = ocrLanguageCode,
    ocrExecutionMode = enumOrDefault(ocrExecutionMode, OcrExecutionMode.BACKGROUND),
    factDensity = enumOrDefault(factDensity, FactDensity.BALANCED),
    includeFormulas = includeFormulas,
    includeDiagrams = includeDiagrams,
    autoVerifyFormulas = autoVerifyFormulas,
    preferredProviderProfileId = preferredProviderProfileId,
)

fun DocumentSettings.toEntity(): DocumentSettingsEntity = DocumentSettingsEntity(
    documentId = documentId,
    pageRangeStart = pageRangeStart,
    pageRangeEnd = pageRangeEnd,
    forceFullLocalOcr = forceFullLocalOcr,
    preferDirectPdfUpload = preferDirectPdfUpload,
    overrideOcrPageLimit = overrideOcrPageLimit,
    ocrEngine = ocrEngine.name,
    ocrLanguageCode = ocrLanguageCode,
    ocrExecutionMode = ocrExecutionMode.name,
    factDensity = factDensity.name,
    includeFormulas = includeFormulas,
    includeDiagrams = includeDiagrams,
    autoVerifyFormulas = autoVerifyFormulas,
    preferredProviderProfileId = preferredProviderProfileId,
)

fun LessonEntity.toDomain(): Lesson = Lesson(
    id = id,
    documentId = documentId,
    title = title,
    summary = summary,
    startPage = startPage,
    endPage = endPage,
    selected = selected,
    sortOrder = sortOrder,
    factCount = factCount,
)

fun Lesson.toEntity(): LessonEntity = LessonEntity(
    id = id,
    documentId = documentId,
    title = title,
    summary = summary,
    startPage = startPage,
    endPage = endPage,
    selected = selected,
    sortOrder = sortOrder,
    factCount = factCount,
)

fun FactItemEntity.toDomain(): Fact = Fact(
    id = id,
    documentId = documentId,
    lessonId = lessonId,
    text = text,
    latex = latex,
    requiresLatex = requiresLatex,
    templateType = enumOrDefault(templateType, CardTemplateType.TEXT),
    preferredTemplateId = preferredTemplateId,
    diagramType = enumOrDefault(diagramType, DiagramKind.NONE),
    diagramSource = diagramSource,
    diagramAssetId = diagramAssetId,
    sourceReferenceModeOverride = enumOrNull<SourceReferenceMode>(sourceReferenceModeOverride),
    regenerationInstruction = regenerationInstruction,
    previousText = previousText,
    sourceBookTitle = sourceBookTitle,
    sourceLessonTitle = sourceLessonTitle,
    sourcePage = sourcePage,
    tags = parseTags(tagsCsv),
    confidence = confidence,
    verificationStatus = enumOrDefault(verificationStatus, VerificationStatus.UNVERIFIED),
    active = active,
    favorite = favorite,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun Fact.toEntity(): FactItemEntity = FactItemEntity(
    id = id,
    documentId = documentId,
    lessonId = lessonId,
    text = text,
    latex = latex,
    requiresLatex = requiresLatex,
    templateType = templateType.name,
    preferredTemplateId = preferredTemplateId,
    diagramType = diagramType.name,
    diagramSource = diagramSource,
    diagramAssetId = diagramAssetId,
    sourceReferenceModeOverride = sourceReferenceModeOverride?.name,
    regenerationInstruction = regenerationInstruction,
    previousText = previousText,
    sourceBookTitle = sourceBookTitle,
    sourceLessonTitle = sourceLessonTitle,
    sourcePage = sourcePage,
    tagsCsv = tags.toTagsCsv(),
    confidence = confidence,
    verificationStatus = verificationStatus.name,
    active = active,
    favorite = favorite,
    createdAtEpochMs = createdAtEpochMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun CollectionEntity.toDomain(factCount: Int = 0): Collection = Collection(
    id = id,
    name = name,
    description = description,
    source = enumOrDefault(source, CollectionSource.USER),
    accepted = accepted,
    includeInRotation = includeInRotation,
    factCount = factCount,
    createdAtEpochMs = createdAtEpochMs,
)

fun UserFolderEntity.toDomain(factCount: Int = 0): UserFolder = UserFolder(
    id = id,
    name = name,
    iconName = iconName,
    colorHex = colorHex,
    includeInRotation = includeInRotation,
    factCount = factCount,
    createdAtEpochMs = createdAtEpochMs,
)

fun TemplateEntity.toDomain(): CardTemplate = CardTemplate(
    id = id,
    name = name,
    engineKind = enumOrDefault(engineKind, TemplateEngineKind.NATIVE_JSON),
    schemaOrMarkup = schemaOrMarkup,
    version = version,
    builtIn = builtIn,
    isDefault = isDefault,
    createdAtEpochMs = createdAtEpochMs,
)

fun CardTemplate.toEntity(): TemplateEntity = TemplateEntity(
    id = id,
    name = name,
    engineKind = engineKind.name,
    schemaOrMarkup = schemaOrMarkup,
    version = version,
    builtIn = builtIn,
    isDefault = isDefault,
    createdAtEpochMs = createdAtEpochMs,
)

fun ProviderProfileEntity.toDomain(): ProviderProfile = ProviderProfile(
    id = id,
    name = name,
    providerType = enumOrDefault(providerType, ProviderType.OPENAI_COMPATIBLE),
    baseUrl = baseUrl,
    modelName = modelName,
    apiVersion = apiVersion,
    deploymentName = deploymentName,
    customHeadersJson = customHeadersJson,
    supportsPdfInput = supportsPdfInput,
    supportsImageInput = supportsImageInput,
    supportsJsonMode = supportsJsonMode,
    supportsJsonSchema = supportsJsonSchema,
    maxContextTokens = maxContextTokens,
    inputUsdPerMillion = inputUsdPerMillion,
    outputUsdPerMillion = outputUsdPerMillion,
    speedTier = speedTier,
    qualityTier = qualityTier,
    enabled = enabled,
    isDefault = isDefault,
    supportsTemperature = supportsTemperature,
    maxInlinePdfBytes = maxInlinePdfBytes,
    maxDirectPdfPages = maxDirectPdfPages,
    keyStatus = enumOrDefault(keyStatus, ProviderKeyStatus.UNKNOWN),
)

fun ProviderProfile.toEntity(): ProviderProfileEntity = ProviderProfileEntity(
    id = id,
    name = name,
    providerType = providerType.name,
    baseUrl = baseUrl,
    modelName = modelName,
    apiVersion = apiVersion,
    deploymentName = deploymentName,
    customHeadersJson = customHeadersJson,
    supportsPdfInput = supportsPdfInput,
    supportsImageInput = supportsImageInput,
    supportsJsonMode = supportsJsonMode,
    supportsJsonSchema = supportsJsonSchema,
    maxContextTokens = maxContextTokens,
    inputUsdPerMillion = inputUsdPerMillion,
    outputUsdPerMillion = outputUsdPerMillion,
    speedTier = speedTier,
    qualityTier = qualityTier,
    enabled = enabled,
    isDefault = isDefault,
    supportsTemperature = supportsTemperature,
    maxInlinePdfBytes = maxInlinePdfBytes,
    maxDirectPdfPages = maxDirectPdfPages,
    keyStatus = keyStatus.name,
)

fun WallpaperStateEntity.toDomain(): WallpaperState = WallpaperState(
    id = id,
    currentFactId = currentFactId,
    currentCacheKey = currentCacheKey,
    currentIndex = currentIndex,
    target = enumOrDefault(target, WallpaperTarget.LIVE),
    scheduleMode = enumOrDefault(scheduleMode, WallpaperScheduleMode.ON_UNLOCK),
    intervalMinutes = intervalMinutes,
    rotationOrder = enumOrDefault(rotationOrder, RotationOrder.SEQUENTIAL),
    activeTemplateId = activeTemplateId,
    wallpaperThemeMode = enumOrDefault(wallpaperThemeMode, ThemeMode.AMOLED),
    sourceReferenceMode = enumOrDefault(sourceReferenceMode, SourceReferenceMode.COMPACT),
    topClockSafeInsetFraction = topClockSafeInsetFraction,
    onlyFavoritesInPool = onlyFavoritesInPool,
    lastRotatedAtEpochMs = lastRotatedAtEpochMs,
)

fun WallpaperState.toEntity(): WallpaperStateEntity = WallpaperStateEntity(
    id = id,
    currentFactId = currentFactId,
    currentCacheKey = currentCacheKey,
    currentIndex = currentIndex,
    target = target.name,
    scheduleMode = scheduleMode.name,
    intervalMinutes = intervalMinutes,
    rotationOrder = rotationOrder.name,
    activeTemplateId = activeTemplateId,
    wallpaperThemeMode = wallpaperThemeMode.name,
    sourceReferenceMode = sourceReferenceMode.name,
    topClockSafeInsetFraction = topClockSafeInsetFraction,
    onlyFavoritesInPool = onlyFavoritesInPool,
    lastRotatedAtEpochMs = lastRotatedAtEpochMs,
)

fun UsageLedgerEntity.toDomain(): UsageEntry = UsageEntry(
    id = id,
    providerProfileId = providerProfileId,
    providerType = enumOrDefault(providerType, ProviderType.OPENAI_COMPATIBLE),
    modelName = modelName,
    documentId = documentId,
    taskType = enumOrDefault(taskType, AiTask.FACTS),
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    estimatedCostUsd = estimatedCostUsd,
    timestampEpochMs = timestampEpochMs,
)

fun AiRequestLogEntity.toDomain(): AiRequestLogEntry = AiRequestLogEntry(
    id = id,
    taskType = enumOrDefault(taskType, AiTask.FACTS),
    providerProfileId = providerProfileId,
    modelName = modelName,
    httpStatusCode = httpStatusCode,
    durationMs = durationMs,
    inputTokens = inputTokens,
    outputTokens = outputTokens,
    repairTriggered = repairTriggered,
    success = success,
    redactedRequestSummary = redactedRequestSummary,
    redactedResponseSnippet = redactedResponseSnippet,
    errorMessage = errorMessage,
    timestampEpochMs = timestampEpochMs,
)
