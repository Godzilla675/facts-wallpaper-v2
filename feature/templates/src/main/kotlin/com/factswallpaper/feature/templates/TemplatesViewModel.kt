package com.factswallpaper.feature.templates

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.repository.TemplateRepository
import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface TemplatesUiState {
    data object Loading : TemplatesUiState
    data class Error(@StringRes val messageRes: Int) : TemplatesUiState
    data class Content(val templates: List<CardTemplate>) : TemplatesUiState
}

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
) : FeatureViewModel() {

    val uiState = templateRepository.observeTemplates()
        .map<List<CardTemplate>, TemplatesUiState> { TemplatesUiState.Content(it) }
        .catch { emit(TemplatesUiState.Error(R.string.templates_load_error)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TemplatesUiState.Loading,
        )

    fun setDefault(templateId: String) {
        viewModelScope.launch {
            templateRepository.setDefaultTemplate(templateId)
            showMessage(R.string.templates_default_saved)
        }
    }

    fun deleteCustom(templateId: String) {
        viewModelScope.launch {
            when (templateRepository.deleteCustomTemplate(templateId)) {
                is AppResult.Success -> showMessage(R.string.templates_deleted)
                is AppResult.Failure -> showMessage(R.string.templates_delete_failed)
            }
        }
    }

    fun createTemplateNotYet() {
        showMessage(R.string.templates_create_not_yet)
    }

    fun generateTemplateNotYet() {
        showMessage(R.string.templates_generate_not_yet)
    }
}
