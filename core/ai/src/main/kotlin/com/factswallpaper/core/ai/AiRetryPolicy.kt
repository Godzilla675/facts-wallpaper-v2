package com.factswallpaper.core.ai

import javax.inject.Inject
import kotlinx.coroutines.delay

/** Injectable sleeper so retry timing can be tested without real waits. */
fun interface RetrySleeper {
    suspend fun sleep(millis: Long)
}

class DelayRetrySleeper @Inject constructor() : RetrySleeper {
    override suspend fun sleep(millis: Long) = delay(millis)
}

/**
 * Retry policy from spec §11.5: exponential backoff 2s, 4s, 8s for 429/5xx and
 * network/timeout failures, honouring Retry-After. 400/401/403 are never retried
 * here; capability-downgrade retries live inside the provider clients.
 */
class AiRetryPolicy(
    private val sleeper: RetrySleeper,
    private val maxRetries: Int = 3,
) {

    @Inject
    constructor(sleeper: RetrySleeper) : this(sleeper, 3)

    suspend fun <T> run(block: suspend () -> T): T {
        var attempt = 0
        var last: AiException? = null
        while (true) {
            try {
                return block()
            } catch (exception: AiException) {
                last = exception
                if (!exception.retryable || attempt >= maxRetries) throw exception
                val backoff = exception.retryAfterSeconds?.let { it.coerceIn(1, 120) * 1_000L }
                    ?: (2_000L shl attempt)
                sleeper.sleep(backoff)
                attempt++
            }
        }
    }

    companion object {
        fun backoffMillis(attempt: Int): Long = 2_000L shl attempt
    }
}
