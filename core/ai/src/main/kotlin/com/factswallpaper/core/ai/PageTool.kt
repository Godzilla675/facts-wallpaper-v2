package com.factswallpaper.core.ai

import com.factswallpaper.core.model.ProviderType
import kotlinx.serialization.Serializable

/** A 1-based, inclusive page range requested by the model through `get_pages`. */
@Serializable
data class PageRange(
    val startPage: Int,
    val endPage: Int,
)

/**
 * Opt-in page-tool context for a request. When set, the provider client sends a
 * native `get_pages` function declaration and parses a returned tool call into
 * [AiResponse.requestedPageRange] instead of treating it as content.
 */
data class PageToolRequest(
    val documentPageCount: Int,
    val availablePageNumbers: List<Int> = emptyList(),
    val maxPagesPerCall: Int = DEFAULT_PAGE_TOOL_PAGE_LIMIT,
)

/**
 * Outcome of a previous `get_pages` call, fed back to the model so it can ask
 * for a different range or finish with the pages it already has.
 */
data class PageToolFeedback(
    val startPage: Int,
    val endPage: Int,
    val pages: List<PageText> = emptyList(),
    val error: String? = null,
)

/** Providers whose native clients implement the `get_pages` function call. */
val ProviderType.supportsNativePageTool: Boolean
    get() = when (this) {
        ProviderType.OPENAI,
        ProviderType.AZURE_OPENAI,
        ProviderType.OPENAI_COMPATIBLE,
        ProviderType.OPENROUTER,
        ProviderType.DEEPSEEK,
        ProviderType.QWEN,
        ProviderType.GEMINI,
        -> true
        ProviderType.ANTHROPIC -> false
    }

const val PAGE_TOOL_NAME: String = "get_pages"
const val PAGE_TOOL_DESCRIPTION: String =
    "Fetch additional pages from the study document when the current lesson pages are not enough. " +
        "Pass start_page and end_page as 1-based inclusive page numbers."
const val DEFAULT_PAGE_TOOL_PAGE_LIMIT: Int = 5
