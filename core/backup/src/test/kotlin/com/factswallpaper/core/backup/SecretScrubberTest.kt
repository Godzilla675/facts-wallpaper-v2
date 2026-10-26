package com.factswallpaper.core.backup

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SecretScrubberTest {

    @Test
    fun `containsSecretKeyMarker detects sensitive key substrings`() {
        assertThat(SecretScrubber.containsSecretKeyMarker("apiKey")).isTrue()
        assertThat(SecretScrubber.containsSecretKeyMarker("api_key")).isTrue()
        assertThat(SecretScrubber.containsSecretKeyMarker("x-api-key")).isTrue()
        assertThat(SecretScrubber.containsSecretKeyMarker("client_secret")).isTrue()
        assertThat(SecretScrubber.containsSecretKeyMarker("auth_token")).isTrue()
        assertThat(SecretScrubber.containsSecretKeyMarker("userCredentials")).isTrue()
        assertThat(SecretScrubber.containsSecretKeyMarker("userPassphrase")).isTrue()
    }

    @Test
    fun `containsSecretKeyMarker allows safe database columns`() {
        assertThat(SecretScrubber.containsSecretKeyMarker("id")).isFalse()
        assertThat(SecretScrubber.containsSecretKeyMarker("title")).isFalse()
        assertThat(SecretScrubber.containsSecretKeyMarker("contentHash")).isFalse()
        assertThat(SecretScrubber.containsSecretKeyMarker("modelName")).isFalse()
        assertThat(SecretScrubber.containsSecretKeyMarker("createdAtEpochMs")).isFalse()
        assertThat(SecretScrubber.containsSecretKeyMarker("inputTokens")).isFalse()
    }

    @Test
    fun `isSuspiciousPath detects path traversal attacks`() {
        assertThat(SecretScrubber.isSuspiciousPath("../outside.txt")).isTrue()
        assertThat(SecretScrubber.isSuspiciousPath("foo/../../bar")).isTrue()
        assertThat(SecretScrubber.isSuspiciousPath("/absolute/path")).isTrue()
        assertThat(SecretScrubber.isSuspiciousPath("\\windows\\system32")).isTrue()
        assertThat(SecretScrubber.isSuspiciousPath("C:\\secret.txt")).isTrue()
    }

    @Test
    fun `isSuspiciousPath allows clean relative paths`() {
        assertThat(SecretScrubber.isSuspiciousPath("manifest.json")).isFalse()
        assertThat(SecretScrubber.isSuspiciousPath("tables/facts.json")).isFalse()
        assertThat(SecretScrubber.isSuspiciousPath("pdfs/doc_123.pdf")).isFalse()
        assertThat(SecretScrubber.isSuspiciousPath("assets/cards/card_1.png")).isFalse()
    }
}
