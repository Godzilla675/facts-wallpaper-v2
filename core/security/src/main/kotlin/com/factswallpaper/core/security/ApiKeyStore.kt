package com.factswallpaper.core.security

/**
 * Storage for provider API keys. Implementations must never expose the raw key
 * through logs, preferences dumps or the database; only the owning feature may
 * read it immediately before a network call.
 */
interface ApiKeyStore {
    suspend fun getApiKey(profileId: String): String?
    suspend fun setApiKey(profileId: String, apiKey: String)
    suspend fun deleteApiKey(profileId: String)
    suspend fun hasApiKey(profileId: String): Boolean

    /**
     * Returns the subset of [candidateProfileIds] that currently have a key.
     * The router uses this instead of reading every key.
     */
    suspend fun configuredProfileIds(candidateProfileIds: Collection<String>): Set<String> =
        candidateProfileIds.filterTo(LinkedHashSet()) { hasApiKey(it) }
}
