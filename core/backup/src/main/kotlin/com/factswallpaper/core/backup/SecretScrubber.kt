package com.factswallpaper.core.backup

object SecretScrubber {

    private val EXEMPT_TOKEN_COUNT_KEYS = setOf(
        "inputtokens",
        "outputtokens",
        "maxcontexttokens",
    )

    fun containsSecretKeyMarker(key: String): Boolean {
        val lower = key.lowercase()
        if (lower in EXEMPT_TOKEN_COUNT_KEYS) {
            return false
        }
        val stripped = lower.replace("_", "").replace("-", "")
        if (stripped.contains("apikey")) {
            return true
        }
        return lower.contains("secret") ||
            lower.contains("token") ||
            lower.contains("credential") ||
            lower.contains("passphrase")
    }

    fun isSuspiciousPath(path: String): Boolean {
        if (path.contains("..")) return true
        if (path.startsWith("/") || path.startsWith("\\")) return true
        if (path.contains(":\\")) return true
        return false
    }
}
