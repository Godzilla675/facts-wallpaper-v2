package com.factswallpaper.feature.templates

import androidx.annotation.StringRes
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.common.AppResult
import com.factswallpaper.core.data.repository.TemplateRepository
import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.TemplateEngineKind
import com.factswallpaper.core.render.CardPreRenderScheduler
import com.factswallpaper.core.render.template.CardTemplateCodec
import com.factswallpaper.core.render.template.CardTemplateSeeder
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TemplateEditorState(
    val draft: TemplateDraft,
    val templateId: String?,
    val version: Int,
    val createdAtEpochMs: Long,
    val isDefault: Boolean,
    val saving: Boolean = false,
    @StringRes val errorRes: Int? = null,
)

data class AiTemplateUiState(
    val visible: Boolean = false,
    val prompt: String = "",
    val generating: Boolean = false,
    @StringRes val errorRes: Int? = null,
)

sealed interface TemplatesUiState {
    data object Loading : TemplatesUiState
    data class Error(@StringRes val messageRes: Int) : TemplatesUiState
    data class Content(
        val templates: List<CardTemplate>,
        val editor: TemplateEditorState? = null,
        val ai: AiTemplateUiState = AiTemplateUiState(),
    ) : TemplatesUiState
}

private data class TemplatesOverlay(
    val editor: TemplateEditorState? = null,
    val ai: AiTemplateUiState = AiTemplateUiState(),
)

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val templateRepository: TemplateRepository,
    private val templateSeeder: CardTemplateSeeder,
    private val aiGenerator: TemplateAiGenerator,
    private val preRenderScheduler: CardPreRenderScheduler,
) : FeatureViewModel() {

    private val overlay = MutableStateFlow(TemplatesOverlay())

    val uiState = combine(
        templateRepository.observeTemplates(),
        overlay,
    ) { templates, current ->
        TemplatesUiState.Content(
            templates = templates,
            editor = current.editor,
            ai = current.ai,
        )
    }
        .map<TemplatesUiState.Content, TemplatesUiState> { it }
        .catch { emit(TemplatesUiState.Error(R.string.templates_load_error)) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TemplatesUiState.Loading,
        )

    init {
        viewModelScope.launch {
            runCatching { templateSeeder.ensureSeeded() }
        }
    }

    fun startCreate() {
        overlay.update {
            it.copy(
                editor = TemplateEditorState(
                    draft = TemplateDraftMapper.newDraft().copy(name = ""),
                    templateId = null,
                    version = 1,
                    createdAtEpochMs = 0L,
                    isDefault = false,
                ),
            )
        }
    }

    fun startEdit(template: CardTemplate) {
        val draft = TemplateDraftMapper.draftFrom(template)
        if (draft == null) {
            showMessage(R.string.templates_invalid)
            return
        }
        // Built-ins are immutable: editing one starts a custom duplicate (F13).
        val duplicate = template.builtIn
        overlay.update {
            it.copy(
                editor = TemplateEditorState(
                    draft = if (duplicate) draft.copy(name = "${template.name} copy") else draft,
                    templateId = if (duplicate) null else template.id,
                    version = if (duplicate) 1 else template.version,
                    createdAtEpochMs = if (duplicate) 0L else template.createdAtEpochMs,
                    isDefault = if (duplicate) false else template.isDefault,
                ),
            )
        }
    }

    fun updateDraft(transform: (TemplateDraft) -> TemplateDraft) {
        overlay.update { current ->
            val editor = current.editor ?: return@update current
            current.copy(editor = editor.copy(draft = transform(editor.draft), errorRes = null))
        }
    }

    fun cancelEditor() {
        overlay.update { it.copy(editor = null) }
    }

    fun saveEditor() {
        val editor = overlay.value.editor ?: return
        val trimmedName = editor.draft.name.trim()
        if (trimmedName.isEmpty()) {
            overlay.update { it.copy(editor = it.editor?.copy(errorRes = R.string.templates_name_required)) }
            return
        }
        val templateId = editor.templateId ?: "custom_${UUID.randomUUID()}"
        val spec = TemplateDraftMapper.toSpec(editor.draft.copy(name = trimmedName), templateId)
        val validationErrors = CardTemplateCodec.validate(spec)
        if (validationErrors.isNotEmpty()) {
            overlay.update { it.copy(editor = it.editor?.copy(errorRes = R.string.templates_invalid)) }
            return
        }
        overlay.update { it.copy(editor = it.editor?.copy(saving = true)) }
        viewModelScope.launch {
            try {
                val existing = editor.templateId?.let { templateRepository.getTemplate(it) }
                if (existing?.builtIn == true) {
                    overlay.update {
                        it.copy(editor = it.editor?.copy(saving = false, errorRes = R.string.templates_builtin_locked))
                    }
                    return@launch
                }
                val now = System.currentTimeMillis()
                templateRepository.saveTemplate(
                    CardTemplate(
                        id = templateId,
                        name = spec.name,
                        engineKind = TemplateEngineKind.NATIVE_JSON,
                        schemaOrMarkup = CardTemplateCodec.encode(spec),
                        version = existing?.version?.plus(1) ?: 1,
                        builtIn = false,
                        isDefault = existing?.isDefault ?: false,
                        createdAtEpochMs = existing?.createdAtEpochMs ?: now,
                    ),
                )
                preRenderScheduler.enqueue()
                overlay.update { it.copy(editor = null) }
                showMessage(R.string.templates_saved)
            } catch (_: Throwable) {
                overlay.update {
                    it.copy(editor = it.editor?.copy(saving = false, errorRes = R.string.templates_save_failed))
                }
            }
        }
    }

    fun setDefault(templateId: String) {
        viewModelScope.launch {
            templateRepository.setDefaultTemplate(templateId)
            preRenderScheduler.enqueue()
            showMessage(R.string.templates_default_saved)
        }
    }

    fun deleteCustom(templateId: String) {
        viewModelScope.launch {
            when (templateRepository.deleteCustomTemplate(templateId)) {
                is AppResult.Success -> {
                    preRenderScheduler.enqueue()
                    showMessage(R.string.templates_deleted)
                }
                is AppResult.Failure -> showMessage(R.string.templates_delete_failed)
            }
        }
    }

    fun openAiDialog() {
        overlay.update { it.copy(ai = it.ai.copy(visible = true, errorRes = null)) }
    }

    fun closeAiDialog() {
        overlay.update { it.copy(ai = it.ai.copy(visible = false, errorRes = null)) }
    }

    fun updateAiPrompt(value: String) {
        overlay.update { it.copy(ai = it.ai.copy(prompt = value, errorRes = null)) }
    }

    fun generateTemplate() {
        val prompt = overlay.value.ai.prompt.trim()
        if (prompt.isEmpty()) {
            overlay.update { it.copy(ai = it.ai.copy(errorRes = R.string.templates_ai_prompt_required)) }
            return
        }
        overlay.update { it.copy(ai = it.ai.copy(generating = true, errorRes = null)) }
        viewModelScope.launch {
            try {
                val generated = aiGenerator.generate(prompt)
                val errors = CardTemplateCodec.validate(generated.spec)
                if (errors.isNotEmpty()) {
                    overlay.update {
                        it.copy(ai = it.ai.copy(generating = false, errorRes = R.string.templates_ai_failed))
                    }
                    return@launch
                }
                overlay.update {
                    it.copy(
                        ai = it.ai.copy(visible = false, generating = false),
                        editor = TemplateEditorState(
                            draft = TemplateDraftMapper.newDraft(generated.spec),
                            templateId = null,
                            version = 1,
                            createdAtEpochMs = 0L,
                            isDefault = false,
                        ),
                    )
                }
                showMessage(R.string.templates_ai_ready)
            } catch (_: Throwable) {
                overlay.update {
                    it.copy(ai = it.ai.copy(generating = false, errorRes = R.string.templates_ai_failed))
                }
            }
        }
    }
}
