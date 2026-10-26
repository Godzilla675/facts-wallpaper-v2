package com.factswallpaper.core.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LogRedactorTest {

    private val redactor = LogRedactor()

    @Test
    fun `redacts openai and google key prefixes`() {
        val text = "key=sk-abcdefghijklmnop another=AIzaSyA1234567890abcdefghijklmnop"

        val redacted = redactor.redact(text)

        assertThat(redacted).doesNotContain("sk-abcdefghijklmnop")
        assertThat(redacted).doesNotContain("AIzaSyA1234567890abcdefghijklmnop")
        assertThat(redacted).contains(LogRedactor.REDACTED)
    }

    @Test
    fun `redacts bearer and api key headers`() {
        val text = "Authorization: Bearer abcdefghijklmnop\nx-api-key: my-secret-key\nx-goog-api-key=goog-key-123456"

        val redacted = redactor.redact(text)

        assertThat(redacted).doesNotContain("abcdefghijklmnop")
        assertThat(redacted).doesNotContain("my-secret-key")
        assertThat(redacted).doesNotContain("goog-key-123456")
    }

    @Test
    fun `redacts registered key even without a known prefix`() {
        redactor.register("local-ollama-key-42")

        val redacted = redactor.redact("request used local-ollama-key-42 for auth")

        assertThat(redacted).doesNotContain("local-ollama-key-42")
        assertThat(redacted).contains(LogRedactor.REDACTED)
    }

    @Test
    fun `short snippets are truncated after redaction`() {
        val snippet = redactor.redactSnippet("prefix " + "x".repeat(400), maxChars = 20)

        assertThat(snippet.length).isEqualTo(21)
        assertThat(snippet.endsWith("…")).isTrue()
    }

    @Test
    fun `plain text is unchanged`() {
        assertThat(redactor.redact("Photosynthesis converts light into chemical energy."))
            .isEqualTo("Photosynthesis converts light into chemical energy.")
    }
}
