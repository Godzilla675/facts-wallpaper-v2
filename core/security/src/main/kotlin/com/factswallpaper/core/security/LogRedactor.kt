package com.factswallpaper.core.security

import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scrubs credentials from anything that can leave the process (Logcat, the local
 * request log, crash reports). Keys can be registered as soon as they are loaded
 * so exact values are redacted even when they do not match a known prefix.
 */
@Singleton
class LogRedactor @Inject constructor() {

    private val registeredKeys = ConcurrentHashMap.newKeySet<String>()

    private val patterns = listOf(
        Regex("""sk-[A-Za-z0-9_\-]{8,}"""),
        Regex("""AIza[0-9A-Za-z_\-]{16,}"""),
        Regex("""(?i)Bearer\s+[A-Za-z0-9._\-]{8,}"""),
        Regex("""(?i)(x-api-key|x-goog-api-key)\s*[:=]\s*[^\s,;"'}]+"""),
        Regex("""(?i)(api[_-]?key|access[_-]?token)\s*[:=]\s*[^\s,;"'}]+"""),
    )

    fun register(key: String?) {
        val trimmed = key?.trim().orEmpty()
        if (trimmed.length >= 6) registeredKeys.add(trimmed)
    }

    fun unregister(key: String?) {
        key?.let(registeredKeys::remove)
    }

    fun redact(input: String): String {
        if (input.isEmpty()) return input
        var result = input
        // Replace exact registered secrets first, longest first to avoid partial leaks.
        registeredKeys.sortedByDescending { it.length }.forEach { secret ->
            if (result.contains(secret)) result = result.replace(secret, REDACTED)
        }
        result = result.replace(patterns[0], REDACTED)
        result = result.replace(patterns[1], REDACTED)
        result = result.replace(patterns[2]) { match -> "Bearer $REDACTED" }
        result = result.replace(patterns[3]) { match ->
            val separator = match.value.indexOfFirst { it == ':' || it == '=' }
            if (separator >= 0) {
                "${match.value.take(separator + 1)} $REDACTED"
            } else {
                REDACTED
            }
        }
        result = result.replace(patterns[4]) { match ->
            val separator = match.value.indexOfFirst { it == ':' || it == '=' }
            if (separator >= 0) {
                "${match.value.take(separator + 1)} $REDACTED"
            } else {
                REDACTED
            }
        }
        return result
    }

    fun redactSnippet(input: String?, maxChars: Int = 240): String {
        val redacted = redact(input.orEmpty())
        return if (redacted.length <= maxChars) redacted else redacted.take(maxChars) + "…"
    }

    companion object {
        const val REDACTED = "[REDACTED]"
    }
}
