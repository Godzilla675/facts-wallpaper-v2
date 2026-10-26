package com.factswallpaper.core.ai

/** Error taxonomy from spec §11.5. */
enum class AiErrorKind {
    AUTHENTICATION,
    NOT_FOUND,
    RATE_LIMITED,
    SERVER,
    NETWORK,
    TIMEOUT,
    INVALID_REQUEST,
    CONTENT,
    CANCELLED,
    UNKNOWN,
}

class AiException(
    val kind: AiErrorKind,
    override val message: String,
    val statusCode: Int? = null,
    val retryAfterSeconds: Long? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {

    val retryable: Boolean
        get() = kind == AiErrorKind.RATE_LIMITED ||
            kind == AiErrorKind.SERVER ||
            kind == AiErrorKind.NETWORK ||
            kind == AiErrorKind.TIMEOUT

    val invalidatesKey: Boolean get() = kind == AiErrorKind.AUTHENTICATION

    val userMessage: String
        get() = when (kind) {
            AiErrorKind.AUTHENTICATION -> "API key rejected. Check it in Provider Settings."
            AiErrorKind.NOT_FOUND -> "Model or endpoint not found."
            AiErrorKind.RATE_LIMITED -> "Rate limited or quota exhausted. Try again shortly."
            AiErrorKind.SERVER -> "The provider is temporarily unavailable."
            AiErrorKind.NETWORK -> "Cannot reach the provider endpoint."
            AiErrorKind.TIMEOUT -> "The provider took too long to respond."
            AiErrorKind.INVALID_REQUEST -> "The provider rejected the request."
            AiErrorKind.CONTENT -> "The provider returned unusable content."
            AiErrorKind.CANCELLED -> "The request was cancelled."
            AiErrorKind.UNKNOWN -> "The AI request failed."
        }

    companion object {
        fun fromHttp(status: Int, body: String, retryAfterSeconds: Long? = null): AiException {
            val kind = when (status) {
                401, 403 -> AiErrorKind.AUTHENTICATION
                404 -> AiErrorKind.NOT_FOUND
                408 -> AiErrorKind.TIMEOUT
                429 -> AiErrorKind.RATE_LIMITED
                in 500..599 -> AiErrorKind.SERVER
                in 400..499 -> AiErrorKind.INVALID_REQUEST
                else -> AiErrorKind.UNKNOWN
            }
            return AiException(
                kind = kind,
                message = "HTTP $status: ${body.take(400)}",
                statusCode = status,
                retryAfterSeconds = retryAfterSeconds,
            )
        }
    }
}
