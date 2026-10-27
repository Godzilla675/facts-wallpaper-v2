package com.factswallpaper.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.data.repository.ProviderProfileRepository
import com.factswallpaper.core.datastore.SettingsRepository
import com.factswallpaper.core.model.Document
import com.factswallpaper.core.model.DocumentSettings
import com.factswallpaper.core.model.OcrExecutionMode
import com.factswallpaper.core.ocr.OcrLanguageCatalog
import com.factswallpaper.core.ocr.OcrModelManager
import com.factswallpaper.core.ocr.OcrModelState
import com.factswallpaper.core.pdf.DocumentProcessingScheduler
import com.factswallpaper.core.pdf.DuplicatePolicy
import com.factswallpaper.core.pdf.PageRangeInput
import com.factswallpaper.core.pdf.PdfCommitOutcome
import com.factswallpaper.core.pdf.PdfCostEstimate
import com.factswallpaper.core.pdf.PdfCostEstimator
import com.factswallpaper.core.pdf.PdfImportError
import com.factswallpaper.core.pdf.PdfImportErrorCode
import com.factswallpaper.core.pdf.PdfImportRepository
import com.factswallpaper.core.pdf.PdfImportResult
import com.factswallpaper.core.pdf.PdfPageRange
import com.factswallpaper.core.pdf.PdfPricing
import com.factswallpaper.core.pdf.PdfProcessingOptions
import com.factswallpaper.core.pdf.PdfProviderCapabilities
import com.factswallpaper.core.pdf.PdfRouteClassifier
import com.factswallpaper.core.pdf.PdfRouteDecision
import com.factswallpaper.core.pdf.StagedPdf
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Duplicate confirmation data shown as a modal before an import can continue. */
data class ImportDuplicatePrompt(
    val stagedId: String,
    val existingDocumentId: String,
    val existingTitle: String,
)

data class ImportFailure(
    val code: PdfImportErrorCode,
    val detail: String? = null,
)

data class ImportPdfUiState(
    val url: String = "",
    val isWorking: Boolean = false,
    val isCopyingLocal: Boolean = false,
    val downloadProgress: Float? = null,
    val staged: StagedPdf? = null,
    val duplicatePrompt: ImportDuplicatePrompt? = null,
    val pageStart: String = "1",
    val pageEnd: String = "",
    val pageRange: PdfPageRange? = null,
    val forceLocalOcr: Boolean = false,
    val preferDirectPdf: Boolean = false,
    val overrideOcrLimit: Boolean = false,
    val languageCode: String = "eng",
    val ocrPageLimit: Int = 20,
    val modelStates: List<OcrModelState> = emptyList(),
    val decision: PdfRouteDecision? = null,
    val cost: PdfCostEstimate? = null,
    val pricing: PdfPricing = PdfPricing.BASELINE,
    val providerName: String? = null,
    val providerProfileId: String? = null,
    val supportsDirectPdf: Boolean = false,
    val isCommitting: Boolean = false,
    val failure: ImportFailure? = null,
    val committedDocumentId: String? = null,
    val activeDocument: Document? = null,
    val activeSettings: DocumentSettings? = null,
    val openDocumentId: String? = null,
) {
    val selectedLanguage get() = OcrLanguageCatalog.forCode(languageCode)

    val selectedLanguageState: OcrModelState?
        get() = modelStates.firstOrNull { it.language.code == selectedLanguage.code }

    val selectedLanguageInstalled: Boolean
        get() = !selectedLanguage.requiresDownload || selectedLanguageState?.isInstalled == true

    val pageRangeInvalid: Boolean
        get() = staged != null && pageRange == null

    val requiresOcrOverride: Boolean
        get() = decision?.requiresOcrOverride == true

    val canStart: Boolean
        get() = staged != null &&
            duplicatePrompt == null &&
            decision != null &&
            pageRange != null &&
            !isWorking &&
            !isCommitting &&
            !requiresOcrOverride &&
            (!decision.usesOcr || selectedLanguageInstalled)

    val hasCommittedSession: Boolean
        get() = committedDocumentId != null
}

@HiltViewModel
class ImportPdfViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val importRepository: PdfImportRepository,
    private val documentRepository: DocumentRepository,
    private val settingsRepository: SettingsRepository,
    private val providerProfileRepository: ProviderProfileRepository,
    private val classifier: PdfRouteClassifier,
    private val ocrModelManager: OcrModelManager,
    private val scheduler: DocumentProcessingScheduler,
) : FeatureViewModel() {

    private val _uiState = MutableStateFlow(ImportPdfUiState())
    val uiState: StateFlow<ImportPdfUiState> = _uiState.asStateFlow()

    private var activeDocumentJob: Job? = null

    init {
        savedStateHandle.get<String>(KEY_SHARED_URI)
            ?.takeIf { it.isNotBlank() }
            ?.let(::stageLocalUri)
        savedStateHandle.get<String>(KEY_COMMITTED_DOCUMENT_ID)
            ?.takeIf { it.isNotBlank() }
            ?.let { documentId ->
                _uiState.update { it.copy(committedDocumentId = documentId) }
                observeDocument(documentId)
            }

        viewModelScope.launch {
            settingsRepository.observePreferences().collect { preferences ->
                _uiState.update { state ->
                    val updated = state.copy(ocrPageLimit = preferences.defaultOcrPageLimit)
                    recompute(updated)
                }
            }
        }
        viewModelScope.launch {
            providerProfileRepository.observeProfiles().collect { profiles ->
                val profile = profiles.firstOrNull { it.isDefault && it.enabled }
                    ?: profiles.firstOrNull { it.enabled }
                    ?: profiles.firstOrNull()
                val pricing = if (profile == null || !profile.enabled) {
                    PdfPricing.BASELINE
                } else {
                    PdfPricing(
                        inputUsdPerMillion = profile.inputUsdPerMillion,
                        outputUsdPerMillion = profile.outputUsdPerMillion,
                        sourceLabel = profile.name,
                        isBaseline = false,
                    )
                }
                _uiState.update { state ->
                    recompute(
                        state.copy(
                            pricing = pricing,
                            providerName = profile?.name,
                            providerProfileId = profile?.id,
                            supportsDirectPdf = profile?.supportsPdfInput == true,
                            preferDirectPdf = state.preferDirectPdf &&
                                profile?.supportsPdfInput == true,
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            ocrModelManager.states.collect { states ->
                _uiState.update { it.copy(modelStates = states) }
            }
        }
    }

    fun updateUrl(value: String) {
        _uiState.update { it.copy(url = value) }
    }

    fun selectLocalPdf(uri: String, displayName: String?) {
        stageLocalUri(uri, displayName)
    }

    fun downloadUrl() {
        val url = _uiState.value.url.trim()
        if (url.isEmpty()) {
            showMessage(R.string.import_url_invalid)
            return
        }
        val previous = _uiState.value.staged
        _uiState.update {
            recompute(
                it.copy(
                    staged = null,
                    duplicatePrompt = null,
                    failure = null,
                    isWorking = true,
                    isCopyingLocal = false,
                    downloadProgress = 0f,
                    decision = null,
                    cost = null,
                    pageRange = null,
                ),
            )
        }
        clearActiveSession()
        viewModelScope.launch {
            previous?.let { importRepository.discard(it.id) }
            val result = importRepository.stageUrl(
                url = url,
                policy = DuplicatePolicy.ASK,
                onProgress = { downloaded, total ->
                    val progress = if (total != null && total > 0L) {
                        (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    _uiState.update { state ->
                        if (state.isWorking) state.copy(downloadProgress = progress) else state
                    }
                },
            )
            applyStageResult(result)
        }
    }

    fun resolveDuplicate(policy: DuplicatePolicy) {
        val prompt = _uiState.value.duplicatePrompt ?: return
        _uiState.update { it.copy(isWorking = true, failure = null) }
        viewModelScope.launch {
            when (val result = importRepository.resolveDuplicate(prompt.stagedId, policy)) {
                is PdfImportResult.Ready -> {
                    _uiState.update { state ->
                        recompute(
                            state.copy(
                                staged = result.staged,
                                duplicatePrompt = null,
                                isWorking = false,
                                downloadProgress = null,
                                pageStart = "1",
                                pageEnd = result.staged.pageCount.toString(),
                            ),
                        )
                    }
                    showMessage(
                        if (policy == DuplicatePolicy.REPLACE_EXISTING) {
                            R.string.import_duplicate_replace_ready
                        } else {
                            R.string.import_ready
                        },
                    )
                }

                is PdfImportResult.Existing -> {
                    _uiState.update {
                        it.copy(
                            staged = null,
                            duplicatePrompt = null,
                            isWorking = false,
                            downloadProgress = null,
                            openDocumentId = result.documentId,
                        )
                    }
                    showMessage(R.string.import_duplicate_opened)
                }

                is PdfImportResult.Duplicate -> {
                    _uiState.update {
                        it.copy(
                            duplicatePrompt = prompt,
                            isWorking = false,
                            downloadProgress = null,
                        )
                    }
                }

                is PdfImportResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            duplicatePrompt = null,
                            isWorking = false,
                            downloadProgress = null,
                            failure = result.error.toFailure(),
                        )
                    }
                }
            }
        }
    }

    fun dismissDuplicate() {
        val prompt = _uiState.value.duplicatePrompt ?: return
        _uiState.update {
            it.copy(
                duplicatePrompt = null,
                staged = null,
                pageRange = null,
                decision = null,
                cost = null,
            )
        }
        viewModelScope.launch { importRepository.discard(prompt.stagedId) }
    }

    fun updatePageStart(value: String) {
        _uiState.update { state ->
            recompute(state.copy(pageStart = value.filter(Char::isDigit)))
        }
    }

    fun updatePageEnd(value: String) {
        _uiState.update { state ->
            recompute(state.copy(pageEnd = value.filter(Char::isDigit)))
        }
    }

    fun setForceLocalOcr(enabled: Boolean) {
        _uiState.update { state -> recompute(state.copy(forceLocalOcr = enabled)) }
    }

    fun setPreferDirectPdf(enabled: Boolean) {
        val state = _uiState.value
        if (enabled && !state.supportsDirectPdf) {
            showMessage(R.string.import_direct_unsupported)
            return
        }
        _uiState.update { current -> recompute(current.copy(preferDirectPdf = enabled)) }
    }

    fun setOverrideOcrLimit(enabled: Boolean) {
        _uiState.update { state -> recompute(state.copy(overrideOcrLimit = enabled)) }
    }

    fun setLanguage(code: String) {
        _uiState.update { state -> recompute(state.copy(languageCode = code)) }
    }

    fun downloadLanguage(code: String) {
        viewModelScope.launch {
            val result = ocrModelManager.download(code)
            if (result.isSuccess) {
                showMessage(R.string.import_language_ready)
            } else {
                showMessage(R.string.import_language_download_failed)
            }
        }
    }

    fun startImport() {
        val state = _uiState.value
        val staged = state.staged ?: return
        val decision = state.decision ?: return
        val range = state.pageRange ?: return
        if (state.duplicatePrompt != null) return
        if (decision.requiresOcrOverride) {
            showMessage(R.string.import_override_required)
            return
        }
        if (decision.usesOcr && !state.selectedLanguageInstalled) {
            showMessage(R.string.import_language_required)
            return
        }

        val language = state.selectedLanguage
        val options = PdfProcessingOptions(
            pageRange = range,
            forceFullLocalOcr = state.forceLocalOcr,
            preferDirectPdfUpload = state.preferDirectPdf,
            overrideOcrPageLimit = state.overrideOcrLimit,
            ocrEngine = language.engineType,
            ocrLanguageCode = language.code,
            ocrExecutionMode = OcrExecutionMode.BACKGROUND,
            preferredProviderProfileId = state.providerProfileId,
        )
        _uiState.update { it.copy(isCommitting = true, failure = null) }
        viewModelScope.launch {
            when (val outcome = importRepository.commit(staged.id, options, decision.route)) {
                is PdfCommitOutcome.Success -> {
                    savedStateHandle[KEY_COMMITTED_DOCUMENT_ID] = outcome.document.documentId
                    _uiState.update {
                        recompute(
                            it.copy(
                                isCommitting = false,
                                staged = null,
                                duplicatePrompt = null,
                                pageRange = null,
                                decision = null,
                                cost = null,
                                committedDocumentId = outcome.document.documentId,
                            ),
                        )
                    }
                    observeDocument(outcome.document.documentId)
                    scheduler.resume(outcome.document.documentId)
                    showMessage(R.string.import_commit_started)
                }

                is PdfCommitOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            isCommitting = false,
                            failure = outcome.error.toFailure(),
                        )
                    }
                }
            }
        }
    }

    fun pauseProcessing() {
        val documentId = _uiState.value.committedDocumentId ?: return
        viewModelScope.launch {
            scheduler.pause(documentId)
            showMessage(R.string.import_ocr_paused)
        }
    }

    fun resumeProcessing() {
        val documentId = _uiState.value.committedDocumentId ?: return
        scheduler.resume(documentId)
        showMessage(R.string.import_ocr_resumed)
    }

    fun retryProcessing() {
        val documentId = _uiState.value.committedDocumentId ?: return
        scheduler.resume(documentId)
        showMessage(R.string.import_processing_retried)
    }

    fun openCommittedDocument() {
        val documentId = _uiState.value.committedDocumentId ?: return
        _uiState.update { it.copy(openDocumentId = documentId) }
    }

    fun startAnotherImport() {
        clearActiveSession()
        _uiState.update {
            recompute(
                it.copy(
                    url = "",
                    failure = null,
                    staged = null,
                    duplicatePrompt = null,
                    pageStart = "1",
                    pageEnd = "",
                    pageRange = null,
                    decision = null,
                    cost = null,
                    forceLocalOcr = false,
                    preferDirectPdf = false,
                    overrideOcrLimit = false,
                    openDocumentId = null,
                ),
            )
        }
    }

    fun clearOpenDocumentNavigation() {
        _uiState.update { it.copy(openDocumentId = null) }
    }

    fun clearFailure() {
        _uiState.update { it.copy(failure = null) }
    }

    private fun stageLocalUri(uri: String, displayName: String? = null) {
        val previous = _uiState.value.staged
        clearActiveSession()
        _uiState.update {
            recompute(
                it.copy(
                    isWorking = true,
                    isCopyingLocal = true,
                    failure = null,
                    staged = null,
                    duplicatePrompt = null,
                    downloadProgress = null,
                    decision = null,
                    cost = null,
                    pageRange = null,
                ),
            )
        }
        viewModelScope.launch {
            previous?.let { importRepository.discard(it.id) }
            val result = importRepository.stageLocal(
                uri = uri,
                displayName = displayName,
                policy = DuplicatePolicy.ASK,
                onProgress = { copied, total ->
                    val progress = if (total != null && total > 0L) {
                        (copied.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                    } else {
                        null
                    }
                    _uiState.update { current ->
                        if (current.isWorking) current.copy(downloadProgress = progress) else current
                    }
                },
            )
            _uiState.update { it.copy(isCopyingLocal = false) }
            applyStageResult(result)
        }
    }

    private fun clearActiveSession() {
        savedStateHandle.remove<String>(KEY_COMMITTED_DOCUMENT_ID)
        activeDocumentJob?.cancel()
        activeDocumentJob = null
        _uiState.update {
            it.copy(
                committedDocumentId = null,
                activeDocument = null,
                activeSettings = null,
            )
        }
    }

    private fun applyStageResult(result: PdfImportResult) {
        when (result) {
            is PdfImportResult.Ready -> {
                _uiState.update { state ->
                    recompute(
                        state.copy(
                            isWorking = false,
                            downloadProgress = null,
                            staged = result.staged,
                            duplicatePrompt = null,
                            pageStart = "1",
                            pageEnd = result.staged.pageCount.toString(),
                        ),
                    )
                }
                showMessage(R.string.import_ready)
            }

            is PdfImportResult.Duplicate -> {
                _uiState.update { state ->
                    recompute(
                        state.copy(
                            isWorking = false,
                            downloadProgress = null,
                            staged = result.staged,
                            duplicatePrompt = ImportDuplicatePrompt(
                                stagedId = result.staged.id,
                                existingDocumentId = result.existingDocumentId,
                                existingTitle = result.existingTitle,
                            ),
                            pageStart = "1",
                            pageEnd = result.staged.pageCount.toString(),
                        ),
                    )
                }
            }

            is PdfImportResult.Existing -> {
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        downloadProgress = null,
                        staged = null,
                        openDocumentId = result.documentId,
                    )
                }
                showMessage(R.string.import_duplicate_opened)
            }

            is PdfImportResult.Failure -> {
                _uiState.update {
                    it.copy(
                        isWorking = false,
                        downloadProgress = null,
                        staged = null,
                        duplicatePrompt = null,
                        failure = result.error.toFailure(),
                    )
                }
            }
        }
    }

    private fun observeDocument(documentId: String) {
        activeDocumentJob?.cancel()
        activeDocumentJob = viewModelScope.launch {
            combine(
                documentRepository.observeDocument(documentId),
                documentRepository.observeSettings(documentId),
            ) { document, settings -> document to settings }
                .collect { (document, settings) ->
                    _uiState.update {
                        it.copy(activeDocument = document, activeSettings = settings)
                    }
                }
        }
    }

    private fun recompute(state: ImportPdfUiState): ImportPdfUiState {
        val staged = state.staged ?: return state.copy(
            pageRange = null,
            decision = null,
            cost = null,
        )
        val range = PageRangeInput.resolve(
            startText = state.pageStart,
            endText = state.pageEnd,
            pageCount = staged.pageCount,
        ) ?: return state.copy(
            pageRange = null,
            decision = null,
            cost = null,
        )
        val language = OcrLanguageCatalog.forCode(state.languageCode)
        val options = PdfProcessingOptions(
            pageRange = range,
            forceFullLocalOcr = state.forceLocalOcr,
            preferDirectPdfUpload = state.preferDirectPdf,
            overrideOcrPageLimit = state.overrideOcrLimit,
            ocrEngine = language.engineType,
            ocrLanguageCode = language.code,
            ocrExecutionMode = OcrExecutionMode.BACKGROUND,
            preferredProviderProfileId = state.providerProfileId,
        )
        val decision = classifier.classify(
            metadata = staged.metadata,
            options = options,
            capabilities = PdfProviderCapabilities(
                supportsDirectPdfInput = state.supportsDirectPdf,
            ),
            ocrPageLimit = state.ocrPageLimit,
        )
        val cost = PdfCostEstimator.estimate(
            metadata = staged.metadata,
            range = decision.pageRange,
            route = decision.route,
            pricing = state.pricing,
        )
        return state.copy(
            pageRange = decision.pageRange,
            decision = decision,
            cost = cost,
        )
    }

    private fun PdfImportError.toFailure() = ImportFailure(code, detail)

    companion object {
        private const val KEY_SHARED_URI = "sharedUri"
        private const val KEY_COMMITTED_DOCUMENT_ID = "committedDocumentId"
    }
}
