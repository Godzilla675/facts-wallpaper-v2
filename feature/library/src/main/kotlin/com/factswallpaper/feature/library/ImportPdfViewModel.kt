package com.factswallpaper.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.model.OcrEngineType
import com.factswallpaper.core.model.OcrExecutionMode
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportPdfUiState(
    val sourceUri: String? = null,
    val sourceName: String? = null,
    val url: String = "",
    val pageStart: String = "1",
    val pageEnd: String = "",
    val preferDirectPdf: Boolean = false,
    val overrideOcrLimit: Boolean = false,
    val ocrEngine: OcrEngineType = OcrEngineType.ML_KIT,
    val executionMode: OcrExecutionMode = OcrExecutionMode.BACKGROUND,
) {
    val hasSource: Boolean get() = !sourceUri.isNullOrBlank() || url.isNotBlank()
    val hasPageRangeError: Boolean
        get() {
            val start = pageStart.toIntOrNull() ?: return false
            val end = pageEnd.toIntOrNull() ?: return false
            return end < start || start < 1
        }
}

@HiltViewModel
class ImportPdfViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : FeatureViewModel() {

    private val _uiState = MutableStateFlow(ImportPdfUiState())
    val uiState: StateFlow<ImportPdfUiState> = _uiState.asStateFlow()

    init {
        val sharedUri = savedStateHandle.get<String>("sharedUri")
        if (!sharedUri.isNullOrBlank()) {
            _uiState.update { it.copy(sourceUri = sharedUri) }
            viewModelScope.launch {
                showMessage(R.string.import_selected_message)
            }
        }
    }

    fun onLocalPdfSelected(uri: String, displayName: String?) {
        _uiState.update { it.copy(sourceUri = uri, sourceName = displayName) }
        showMessage(R.string.import_selected_message)
    }

    fun updateUrl(value: String) {
        _uiState.update { it.copy(url = value) }
    }

    fun updatePageStart(value: String) {
        _uiState.update { it.copy(pageStart = value.filter(Char::isDigit)) }
    }

    fun updatePageEnd(value: String) {
        _uiState.update { it.copy(pageEnd = value.filter(Char::isDigit)) }
    }

    fun setPreferDirectPdf(enabled: Boolean) {
        _uiState.update { it.copy(preferDirectPdf = enabled) }
    }

    fun setOverrideOcrLimit(enabled: Boolean) {
        _uiState.update { it.copy(overrideOcrLimit = enabled) }
    }

    fun setOcrEngine(engine: OcrEngineType) {
        _uiState.update { it.copy(ocrEngine = engine) }
    }

    fun setExecutionMode(mode: OcrExecutionMode) {
        _uiState.update { it.copy(executionMode = mode) }
    }

    fun download() {
        val url = _uiState.value.url.trim()
        if (!url.startsWith("https://")) {
            showMessage(R.string.import_url_invalid)
            return
        }
        showMessage(R.string.import_download_not_yet)
    }

    fun analyze() {
        val state = _uiState.value
        if (!state.hasSource) {
            showMessage(R.string.import_source_required)
            return
        }
        showMessage(R.string.import_analyze_not_yet)
    }
}
