package com.factswallpaper.feature.library

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.factswallpaper.core.common.FactsDispatchers
import com.factswallpaper.core.data.repository.DocumentRepository
import com.factswallpaper.core.pdf.PdfFileStore
import com.factswallpaper.core.ui.FeatureViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import javax.inject.Inject
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PdfViewerUiState(
    val title: String = "",
    val currentPage: Int = 1,
    val pageCount: Int = 1,
    val pageBitmap: Bitmap? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class PdfViewerViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val fileStore: PdfFileStore,
    private val dispatchers: FactsDispatchers,
) : FeatureViewModel() {

    private val documentId: String = checkNotNull(savedStateHandle["documentId"])
    private val initialPage: Int = (savedStateHandle.get<Int>("initialPage") ?: 1).coerceAtLeast(1)

    private val _uiState = MutableStateFlow(PdfViewerUiState(currentPage = initialPage))
    val uiState: StateFlow<PdfViewerUiState> = _uiState.asStateFlow()

    private var localFile: File? = null

    init {
        loadDocument()
    }

    private fun loadDocument() {
        viewModelScope.launch {
            val doc = documentRepository.getDocument(documentId)
            if (doc == null) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "Document not found") }
                return@launch
            }
            val file = File(doc.localFilePath).takeIf { it.isFile }
                ?: fileStore.documentFile(documentId).takeIf { it.isFile }

            if (file == null || !file.exists()) {
                _uiState.update { it.copy(isLoading = false, errorMessage = "PDF file not found on disk") }
                return@launch
            }
            localFile = file

            withContext(dispatchers.io) {
                runCatching {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            val total = renderer.pageCount
                            val page = initialPage.coerceIn(1, total)
                            _uiState.update {
                                it.copy(
                                    title = doc.title,
                                    pageCount = total,
                                    currentPage = page,
                                )
                            }
                        }
                    }
                }
            }
            renderPage(_uiState.value.currentPage)
        }
    }

    fun goToPage(page: Int) {
        val target = page.coerceIn(1, _uiState.value.pageCount)
        if (target != _uiState.value.currentPage) {
            _uiState.update { it.copy(currentPage = target) }
            renderPage(target)
        }
    }

    fun nextPage() = goToPage(_uiState.value.currentPage + 1)
    fun previousPage() = goToPage(_uiState.value.currentPage - 1)

    private fun renderPage(pageNumber: Int) {
        val file = localFile ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val bitmap = withContext(dispatchers.io) {
                runCatching {
                    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                        PdfRenderer(descriptor).use { renderer ->
                            renderer.openPage(pageNumber - 1).use { page ->
                                val targetWidth = 1440
                                val scale = targetWidth.toFloat() / page.width.toFloat()
                                val widthPx = (page.width * scale).roundToInt().coerceAtLeast(1)
                                val heightPx = (page.height * scale).roundToInt().coerceAtLeast(1)

                                val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
                                bmp.eraseColor(Color.WHITE)
                                page.render(bmp, null, Matrix(), PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bmp
                            }
                        }
                    }
                }.getOrNull()
            }
            _uiState.update {
                it.copy(
                    pageBitmap = bitmap,
                    isLoading = false,
                )
            }
        }
    }
}
