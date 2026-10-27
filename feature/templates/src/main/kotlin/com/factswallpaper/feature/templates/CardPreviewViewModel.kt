package com.factswallpaper.feature.templates

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.repository.FactRepository
import com.factswallpaper.core.data.repository.RotationPoolRepository
import com.factswallpaper.core.data.repository.TemplateRepository
import com.factswallpaper.core.data.repository.WallpaperRepository
import com.factswallpaper.core.model.CardTemplate
import com.factswallpaper.core.model.CardTemplateType
import com.factswallpaper.core.model.DiagramKind
import com.factswallpaper.core.model.Fact
import com.factswallpaper.core.model.SourceReferenceMode
import com.factswallpaper.core.model.ThemeMode
import com.factswallpaper.core.model.VerificationStatus
import com.factswallpaper.core.model.WallpaperState
import com.factswallpaper.core.render.CardPreRenderScheduler
import com.factswallpaper.core.render.CardRenderCoordinator
import com.factswallpaper.core.render.CardRenderRequest
import com.factswallpaper.core.render.RenderSizeStore
import com.factswallpaper.core.render.card.CardRenderOptions
import com.factswallpaper.core.render.template.BuiltInTemplates
import com.factswallpaper.core.render.template.CardTemplateCodec
import com.factswallpaper.core.render.template.CardTemplateSeeder
import com.factswallpaper.core.render.verify.FormulaVerificationOutcome
import com.factswallpaper.core.render.verify.FormulaVerificationService
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PreviewBitmapState {
    data object Idle : PreviewBitmapState
    data object Rendering : PreviewBitmapState
    data class Ready(
        val bitmap: Bitmap,
        val missingStage: Boolean,
        val needsReview: Boolean,
    ) : PreviewBitmapState

    data class Error(@StringRes val messageRes: Int) : PreviewBitmapState
}

sealed interface CardPreviewUiState {
    data object Loading : CardPreviewUiState
    data class Error(@StringRes val messageRes: Int) : CardPreviewUiState
    data class Content(
        val pool: List<Fact>,
        val templates: List<CardTemplate>,
        val selectedFact: Fact?,
        val selectedTemplateId: String,
        val wallpaper: WallpaperState,
        val bodyFontScale: Float = 1.0f,
        val topSafeMarginFraction: Float = 0.24f,
        val preview: PreviewBitmapState = PreviewBitmapState.Idle,
    ) : CardPreviewUiState {
        val selectedIndex: Int
            get() = pool.indexOfFirst { it.id == selectedFact?.id }.coerceAtLeast(0)
    }
}

private data class PreviewLayoutOverride(
    val bodyFontScale: Float? = null,
    val topSafeMarginFraction: Float? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CardPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    factRepository: FactRepository,
    private val templateRepository: TemplateRepository,
    private val wallpaperRepository: WallpaperRepository,
    rotationPoolRepository: RotationPoolRepository,
    private val coordinator: CardRenderCoordinator,
    private val verificationService: FormulaVerificationService,
    private val renderSizeStore: RenderSizeStore,
    private val preRenderScheduler: CardPreRenderScheduler,
    private val templateSeeder: CardTemplateSeeder,
    private val dispatchers: FactsDispatchers,
) : FeatureViewModel() {

    private val requestedFactId = MutableStateFlow(savedStateHandle.get<String>("factId"))
    private val previewTemplateId = MutableStateFlow(savedStateHandle.get<String>("templateId"))
    private val layoutOverride = MutableStateFlow(PreviewLayoutOverride())

    private val requestedFact = requestedFactId.flatMapLatest { factId ->
        if (factId == null) flowOf(null) else factRepository.observeFact(factId)
    }

    init {
        viewModelScope.launch { runCatching { templateSeeder.ensureSeeded() } }
    }

    private val coreState: Flow<CardPreviewUiState.Content> = combine(
        rotationPoolRepository.observePool(),
        templateRepository.observeTemplates(),
        wallpaperRepository.observeState(),
        previewTemplateId,
        requestedFact,
    ) { pool, templates, wallpaper, previewTemplate, explicitFact ->
        CardPreviewUiState.Content(
            pool = pool,
            templates = templates,
            selectedFact = explicitFact ?: pool.firstOrNull(),
            selectedTemplateId = previewTemplate ?: wallpaper.activeTemplateId,
            wallpaper = wallpaper,
        )
    }

    private val baseState: Flow<CardPreviewUiState.Content> = combine(
        coreState,
        layoutOverride,
    ) { content, override ->
        val activeTemplate = content.templates.firstOrNull { it.id == content.selectedTemplateId }
            ?: content.templates.firstOrNull { it.isDefault }
            ?: BuiltInTemplates.byId(content.selectedTemplateId)
            ?: fallbackTemplate()
        val baseSpec = CardTemplateCodec.fromCardTemplate(activeTemplate)
            ?: BuiltInTemplates.spec(BuiltInTemplates.DEFAULT_TEMPLATE_ID)
        val defaultFontScale = baseSpec?.bodyFontScale ?: 1.0f
        val defaultTopMargin = baseSpec?.topSafeMarginFraction ?: 0.24f
        content.copy(
            bodyFontScale = (override.bodyFontScale ?: defaultFontScale).coerceIn(0.8f, 1.4f),
            topSafeMarginFraction = (override.topSafeMarginFraction ?: defaultTopMargin)
                .coerceIn(0.18f, 0.32f),
        )
    }.distinctUntilChanged()

    private val retrySignal = MutableStateFlow(0)

    val uiState = retrySignal.flatMapLatest {
        baseState
            .flatMapLatest { content -> renderPreview(content) }
            .catch { emit(CardPreviewUiState.Error(R.string.preview_load_error)) }
    }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CardPreviewUiState.Loading,
        )

    fun retry() {
        retrySignal.update { it + 1 }
    }

    fun nextFact() {
        val content = uiState.value as? CardPreviewUiState.Content ?: return
        if (content.pool.size <= 1) return
        val next = content.pool[(content.selectedIndex + 1) % content.pool.size]
        requestedFactId.value = next.id
    }

    fun previousFact() {
        val content = uiState.value as? CardPreviewUiState.Content ?: return
        if (content.pool.size <= 1) return
        val index = (content.selectedIndex - 1 + content.pool.size) % content.pool.size
        requestedFactId.value = content.pool[index].id
    }

    fun selectTemplate(templateId: String) {
        previewTemplateId.value = null
        layoutOverride.value = PreviewLayoutOverride()
        viewModelScope.launch {
            wallpaperRepository.updateState { it.copy(activeTemplateId = templateId) }
            preRenderScheduler.enqueue()
            showMessage(R.string.preview_template_selected)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            wallpaperRepository.updateState { it.copy(wallpaperThemeMode = mode) }
            preRenderScheduler.enqueue()
        }
    }

    fun setSourceReferenceMode(mode: SourceReferenceMode) {
        viewModelScope.launch {
            wallpaperRepository.updateState { it.copy(sourceReferenceMode = mode) }
            preRenderScheduler.enqueue()
        }
    }

    fun setBodyFontScale(scale: Float) {
        val rounded = ((scale.coerceIn(0.8f, 1.4f) * 20f).roundToInt()) / 20f
        layoutOverride.update { it.copy(bodyFontScale = rounded) }
    }

    fun setTopSafeMarginFraction(fraction: Float) {
        val rounded = ((fraction.coerceIn(0.18f, 0.32f) * 100f).roundToInt()) / 100f
        layoutOverride.update { it.copy(topSafeMarginFraction = rounded) }
    }

    fun applyToWallpaper() {
        val content = uiState.value as? CardPreviewUiState.Content ?: return
        val templateId = content.selectedTemplateId
        val override = layoutOverride.value
        viewModelScope.launch {
            val existing = templateRepository.getTemplate(templateId)
                ?: content.templates.firstOrNull { it.id == templateId }
            if (existing != null && (override.bodyFontScale != null || override.topSafeMarginFraction != null)) {
                val baseSpec = CardTemplateCodec.fromCardTemplate(existing)
                if (baseSpec != null) {
                    val updatedSpec = baseSpec.copy(
                        bodyFontScale = content.bodyFontScale,
                        topSafeMarginFraction = content.topSafeMarginFraction,
                    )
                    if (CardTemplateCodec.isValid(updatedSpec)) {
                        templateRepository.saveTemplate(
                            existing.copy(
                                schemaOrMarkup = CardTemplateCodec.encode(updatedSpec),
                                version = existing.version + 1,
                            ),
                        )
                    }
                }
            }
            previewTemplateId.value = null
            layoutOverride.value = PreviewLayoutOverride()
            templateRepository.setDefaultTemplate(templateId)
            wallpaperRepository.updateState { it.copy(activeTemplateId = templateId) }
            preRenderScheduler.enqueue()
            showMessage(R.string.preview_applied_to_wallpaper)
        }
    }

    private fun renderPreview(
        content: CardPreviewUiState.Content,
    ): Flow<CardPreviewUiState> = flow {
        val fact = content.selectedFact
        if (fact == null) {
            emit(content.copy(preview = PreviewBitmapState.Idle))
            return@flow
        }
        emit(content.copy(preview = PreviewBitmapState.Rendering))

        val verification = verificationService.verify(fact)
        val needsReview = verification is FormulaVerificationOutcome.Failed ||
            fact.verificationStatus == VerificationStatus.FAILED
        val renderFact = if (needsReview) fact.asPlainTextFallback() else fact
        val baseTemplate = if (needsReview) {
            BuiltInTemplates.byId(BuiltInTemplates.MINIMAL_TEXT_ID) ?: fallbackTemplate()
        } else {
            content.templates.firstOrNull { it.id == content.selectedTemplateId }
                ?: content.templates.firstOrNull { it.isDefault }
                ?: fallbackTemplate()
        }
        val baseSpec = CardTemplateCodec.fromCardTemplate(baseTemplate)
        if (baseSpec == null) {
            emit(content.copy(preview = PreviewBitmapState.Error(R.string.preview_render_error)))
            return@flow
        }
        val spec = baseSpec.copy(
            bodyFontScale = content.bodyFontScale,
            topSafeMarginFraction = content.topSafeMarginFraction,
        )
        val renderTemplate = if (spec == baseSpec) {
            baseTemplate
        } else {
            val layoutFingerPrint =
                (content.bodyFontScale * 100f).roundToInt() * 100 +
                    (content.topSafeMarginFraction * 100f).roundToInt()
            baseTemplate.copy(
                schemaOrMarkup = CardTemplateCodec.encode(spec),
                version = baseTemplate.version * 100_000 + layoutFingerPrint,
            )
        }

        val size = renderSizeStore.current()
        val outcome = try {
            coordinator.render(
                CardRenderRequest(
                    fact = renderFact,
                    template = renderTemplate,
                    spec = spec,
                    options = CardRenderOptions(
                        width = size.widthPx,
                        height = size.heightPx,
                        targetSurface = content.wallpaper.target,
                        themeMode = content.wallpaper.wallpaperThemeMode,
                        sourceReferenceMode = content.wallpaper.sourceReferenceMode,
                    ),
                    allowWebViewStage = true,
                ),
            )
        } catch (_: Throwable) {
            null
        }
        if (outcome == null) {
            emit(content.copy(preview = PreviewBitmapState.Error(R.string.preview_render_error)))
            return@flow
        }
        val bitmap = withContext(dispatchers.io) { decode(outcome.card.pngFile) }
        if (bitmap == null) {
            emit(content.copy(preview = PreviewBitmapState.Error(R.string.preview_render_error)))
            return@flow
        }
        emit(
            content.copy(
                selectedFact = renderFact,
                selectedTemplateId = if (needsReview) baseTemplate.id else content.selectedTemplateId,
                preview = PreviewBitmapState.Ready(
                    bitmap = bitmap,
                    missingStage = outcome.missingStage,
                    needsReview = needsReview,
                ),
            ),
        )
    }

    private fun decode(file: File): Bitmap? =
        if (file.isFile) BitmapFactory.decodeFile(file.absolutePath) else null

    private fun fallbackTemplate(): CardTemplate =
        BuiltInTemplates.byId(BuiltInTemplates.DEFAULT_TEMPLATE_ID)
            ?: requireNotNull(BuiltInTemplates.all().firstOrNull())

    private fun Fact.asPlainTextFallback(): Fact = copy(
        latex = null,
        requiresLatex = false,
        templateType = CardTemplateType.TEXT,
        diagramType = DiagramKind.NONE,
        diagramSource = null,
    )
}