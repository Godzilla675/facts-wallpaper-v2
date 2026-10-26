package com.factswallpaper.core.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test

class AiRetryPolicyTest {

    @Test
    fun `retries retryable failures with exponential backoff`() = runTest {
        val sleeps = mutableListOf<Long>()
        var attempts = 0
        val policy = AiRetryPolicy(
            sleeper = RetrySleeper { sleeps += it },
            maxRetries = 3,
        )

        val result = policy.run {
            attempts++
            if (attempts < 3) {
                throw AiException(AiErrorKind.SERVER, "temporary", statusCode = 503)
            }
            "ok"
        }

        assertThat(result).isEqualTo("ok")
        assertThat(attempts).isEqualTo(3)
        assertThat(sleeps).containsExactly(2_000L, 4_000L).inOrder()
    }

    @Test
    fun `honors retry after seconds`() = runTest {
        val sleeps = mutableListOf<Long>()
        var attempts = 0
        val policy = AiRetryPolicy(sleeper = RetrySleeper { sleeps += it })

        policy.run {
            attempts++
            if (attempts == 1) {
                throw AiException(
                    AiErrorKind.RATE_LIMITED,
                    "slow down",
                    statusCode = 429,
                    retryAfterSeconds = 7,
                )
            }
            "ok"
        }

        assertThat(sleeps).containsExactly(7_000L)
    }

    @Test
    fun `does not retry authentication or invalid request failures`() = runTest {
        val sleeps = mutableListOf<Long>()
        val policy = AiRetryPolicy(sleeper = RetrySleeper { sleeps += it })

        val auth = runCatching {
            policy.run { throw AiException(AiErrorKind.AUTHENTICATION, "bad key", statusCode = 401) }
        }.exceptionOrNull()
        val invalid = runCatching {
            policy.run { throw AiException(AiErrorKind.INVALID_REQUEST, "bad", statusCode = 400) }
        }.exceptionOrNull()

        assertThat(auth).isInstanceOf(AiException::class.java)
        assertThat(invalid).isInstanceOf(AiException::class.java)
        assertThat(sleeps).isEmpty()
    }

    @Test
    fun `stops after max retries`() = runTest {
        val sleeps = mutableListOf<Long>()
        var attempts = 0
        val policy = AiRetryPolicy(
            sleeper = RetrySleeper { sleeps += it },
            maxRetries = 3,
        )

        val error = runCatching {
            policy.run {
                attempts++
                throw AiException(AiErrorKind.NETWORK, "offline")
            }
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(AiException::class.java)
        assertThat(attempts).isEqualTo(4)
        assertThat(sleeps).containsExactly(2_000L, 4_000L, 8_000L).inOrder()
    }

    @Test
    fun `backoff helper matches the documented sequence`() {
        assertThat(AiRetryPolicy.backoffMillis(0)).isEqualTo(2_000L)
        assertThat(AiRetryPolicy.backoffMillis(1)).isEqualTo(4_000L)
        assertThat(AiRetryPolicy.backoffMillis(2)).isEqualTo(8_000L)
    }
}
