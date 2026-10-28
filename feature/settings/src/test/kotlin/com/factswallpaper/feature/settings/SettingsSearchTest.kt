package com.factswallpaper.feature.settings

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SettingsSearchTest {

    private val entries = listOf(
        SettingsSearchEntry(
            id = "theme",
            titleRes = 1,
            summaryRes = 2,
            keywords = "dark amoled appearance",
            destination = SettingsDestination.ADVANCED,
            advancedSection = AdvancedSection.THEME,
        ),
        SettingsSearchEntry(
            id = "backup",
            titleRes = 3,
            summaryRes = 4,
            keywords = "restore archive encrypted",
            destination = SettingsDestination.ADVANCED,
            advancedSection = AdvancedSection.BACKUP,
        ),
        SettingsSearchEntry(
            id = "models",
            titleRes = 5,
            summaryRes = 6,
            keywords = "openai gemini model catalog",
            destination = SettingsDestination.PROVIDER,
            providerSection = ProviderSection.PROFILES,
        ),
    )

    private val titles = mapOf(
        1 to "Theme & Appearance",
        3 to "Backup & Restore",
        5 to "Provider Profiles",
    )

    private val summaries = mapOf(
        2 to "Choose light, dark or AMOLED.",
        4 to "Export a backup or restore one.",
        6 to "Add endpoints and API keys.",
    )

    private fun search(query: String) = filterSettingsEntries(
        entries = entries,
        query = query,
        titleOf = { titles.getValue(it.titleRes) },
        summaryOf = { summaries.getValue(it.summaryRes) },
    )

    @Test
    fun `blank query returns every entry`() {
        assertThat(search("")).containsExactlyElementsIn(entries).inOrder()
        assertThat(search("   ")).containsExactlyElementsIn(entries).inOrder()
    }

    @Test
    fun `matches titles summaries and keywords case insensitively`() {
        assertThat(search("AMOLED").map { it.id }).containsExactly("theme")
        assertThat(search("endpoints").map { it.id }).containsExactly("models")
        assertThat(search("gemini").map { it.id }).containsExactly("models")
    }

    @Test
    fun `every token must match so extra words narrow the result`() {
        assertThat(search("backup restore").map { it.id }).containsExactly("backup")
        assertThat(search("backup theme")).isEmpty()
    }

    @Test
    fun `unknown query returns no results`() {
        assertThat(search("quantum flux capacitor")).isEmpty()
    }

    @Test
    fun `catalog ids are unique and every entry has a destination section`() {
        val catalog = SettingsSearchCatalog.entries
        assertThat(catalog.map { it.id }.distinct()).hasSize(catalog.size)
        catalog.forEach { entry ->
            when (entry.destination) {
                SettingsDestination.PROVIDER -> assertThat(entry.providerSection).isNotNull()
                SettingsDestination.ADVANCED -> assertThat(entry.advancedSection).isNotNull()
            }
        }
    }
}
