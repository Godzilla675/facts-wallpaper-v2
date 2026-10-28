package com.factswallpaper.feature.settings

import androidx.annotation.StringRes

/** A settings destination that can be reached from the global settings search. */
enum class SettingsDestination {
    PROVIDER,
    ADVANCED,
}

/** Sections of the provider settings screen, in on-screen order. */
enum class ProviderSection {
    ROUTING,
    TASK_ROUTING,
    USAGE,
    PROFILES,
}

/** Sections of the advanced settings screen, in on-screen order. */
enum class AdvancedSection {
    THEME,
    PIPELINE,
    OCR,
    PROMPTS,
    DIAGNOSTICS,
    LEDGER,
    STORAGE,
    BACKUP,
}

/**
 * One searchable settings row. Titles and summaries are resource ids so the
 * list is localised; [keywords] adds synonyms that are not visible on screen.
 */
data class SettingsSearchEntry(
    val id: String,
    @StringRes val titleRes: Int,
    @StringRes val summaryRes: Int,
    val keywords: String,
    val destination: SettingsDestination,
    val providerSection: ProviderSection? = null,
    val advancedSection: AdvancedSection? = null,
) {
    init {
        require(
            (destination == SettingsDestination.PROVIDER && providerSection != null) ||
                (destination == SettingsDestination.ADVANCED && advancedSection != null),
        ) { "A search result must point at a section on its destination screen." }
    }
}

object SettingsSearchCatalog {

    val entries: List<SettingsSearchEntry> = listOf(
        SettingsSearchEntry(
            id = "provider_profiles",
            titleRes = R.string.provider_profiles_title,
            summaryRes = R.string.provider_empty_body,
            keywords = "provider profile add edit endpoint model api key openai gemini anthropic azure openrouter deepseek qwen ollama nvidia nim custom",
            destination = SettingsDestination.PROVIDER,
            providerSection = ProviderSection.PROFILES,
        ),
        SettingsSearchEntry(
            id = "routing_strategy",
            titleRes = R.string.provider_routing_title,
            summaryRes = R.string.provider_routing_helper,
            keywords = "routing strategy cheapest balanced quality manual automatic router model selection",
            destination = SettingsDestination.PROVIDER,
            providerSection = ProviderSection.ROUTING,
        ),
        SettingsSearchEntry(
            id = "task_routing",
            titleRes = R.string.provider_overrides_title,
            summaryRes = R.string.provider_overrides_helper,
            keywords = "task override outline facts diagram template verification prompt pin profile",
            destination = SettingsDestination.PROVIDER,
            providerSection = ProviderSection.TASK_ROUTING,
        ),
        SettingsSearchEntry(
            id = "usage",
            titleRes = R.string.provider_usage_title,
            summaryRes = R.string.provider_usage_helper,
            keywords = "usage tokens cost spending ledger today 30 days budget price",
            destination = SettingsDestination.PROVIDER,
            providerSection = ProviderSection.USAGE,
        ),
        SettingsSearchEntry(
            id = "theme",
            titleRes = R.string.advanced_theme_title,
            summaryRes = R.string.advanced_theme_helper,
            keywords = "theme appearance dark light amoled system dynamic color material you oled",
            destination = SettingsDestination.ADVANCED,
            advancedSection = AdvancedSection.THEME,
        ),
        SettingsSearchEntry(
            id = "haptics",
            titleRes = R.string.advanced_haptics,
            summaryRes = R.string.advanced_haptics_helper,
            keywords = "haptics vibration feedback touch favorite study wallpaper accessibility",
            destination = SettingsDestination.ADVANCED,
            advancedSection = AdvancedSection.THEME,
        ),
        SettingsSearchEntry(
            id = "pipeline",
            titleRes = R.string.advanced_pipeline_title,
            summaryRes = R.string.advanced_pipeline_helper,
            keywords = "pipeline background processing jobs activity whats happening pause resume retry page tools get_pages tool calling",
            destination = SettingsDestination.ADVANCED,
            advancedSection = AdvancedSection.PIPELINE,
        ),
        SettingsSearchEntry(
            id = "ocr",
            titleRes = R.string.advanced_ocr_title,
            summaryRes = R.string.advanced_ocr_helper,
            keywords = "ocr scanned pages language tesseract ml kit page limit engine queue pre-render",
            destination = SettingsDestination.ADVANCED,
            advancedSection = AdvancedSection.OCR,
        ),
        SettingsSearchEntry(
            id = "prompts",
            titleRes = R.string.advanced_suffixes_title,
            summaryRes = R.string.advanced_suffixes_helper,
            keywords = "prompt instructions custom suffix system prompt per task",
            destination = SettingsDestination.ADVANCED,
            advancedSection = AdvancedSection.PROMPTS,
        ),
        SettingsSearchEntry(
            id = "diagnostics",
            titleRes = R.string.advanced_diagnostics_title,
            summaryRes = R.string.advanced_diagnostics_helper,
            keywords = "diagnostics logs inspector prompt response double tap wallpaper debug",
            destination = SettingsDestination.ADVANCED,
            advancedSection = AdvancedSection.DIAGNOSTICS,
        ),
        SettingsSearchEntry(
            id = "ledger",
            titleRes = R.string.advanced_ledger_title,
            summaryRes = R.string.advanced_ledger_helper,
            keywords = "usage ledger token cost history requests",
            destination = SettingsDestination.ADVANCED,
            advancedSection = AdvancedSection.LEDGER,
        ),
        SettingsSearchEntry(
            id = "storage",
            titleRes = R.string.advanced_storage_title,
            summaryRes = R.string.advanced_storage_helper,
            keywords = "storage cache clear rebuild diagrams database size compact",
            destination = SettingsDestination.ADVANCED,
            advancedSection = AdvancedSection.STORAGE,
        ),
        SettingsSearchEntry(
            id = "backup",
            titleRes = R.string.advanced_data_title,
            summaryRes = R.string.advanced_data_helper,
            keywords = "backup restore export import data archive automatic schedule encrypted",
            destination = SettingsDestination.ADVANCED,
            advancedSection = AdvancedSection.BACKUP,
        ),
    )
}

/**
 * Case-insensitive filter over a result's localised title, summary and keyword
 * list. Every whitespace-separated token must match, so "backup restore"
 * narrows rather than widens the list. A blank query returns every entry.
 */
fun filterSettingsEntries(
    entries: List<SettingsSearchEntry>,
    query: String,
    titleOf: (SettingsSearchEntry) -> String,
    summaryOf: (SettingsSearchEntry) -> String,
): List<SettingsSearchEntry> {
    val tokens = query.trim().lowercase().split(WHITESPACE).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return entries
    return entries.filter { entry ->
        val haystack = buildString {
            append(titleOf(entry))
            append(' ')
            append(summaryOf(entry))
            append(' ')
            append(entry.keywords)
        }.lowercase()
        tokens.all { haystack.contains(it) }
    }
}

private val WHITESPACE = Regex("\\s+")

/**
 * The advanced settings list renders one card per [AdvancedSection] in enum
 * declaration order, so the ordinal doubles as the LazyColumn item index.
 */
fun AdvancedSection.lazyListIndex(): Int = ordinal
