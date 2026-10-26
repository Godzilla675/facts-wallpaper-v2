package com.factswallpaper.core.ai.client

import com.factswallpaper.core.ai.AiErrorKind
import com.factswallpaper.core.ai.AiException
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull

internal val ProviderJson: Json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    explicitNulls = false
    encodeDefaults = true
}

internal suspend fun HttpClient.postJson(
    url: String,
    headers: Map<String, String>,
    body: String,
): String = withNetworkErrors {
    val response = request(url) {
        method = HttpMethod.Post
        contentType(ContentType.Application.Json)
        headers.forEach { (key, value) -> header(key, value) }
        setBody(body)
    }
    response.readProviderResponse()
}

internal suspend fun HttpClient.getJson(
    url: String,
    headers: Map<String, String>,
): String = withNetworkErrors {
    val response = request(url) {
        method = HttpMethod.Get
        headers.forEach { (key, value) -> header(key, value) }
    }
    response.readProviderResponse()
}

internal suspend fun HttpResponse.readProviderResponse(): String {
    val text = bodyAsText()
    if (status.isSuccess()) return text
    val retryAfter = headers["Retry-After"]?.toLongOrNull()
    throw AiException.fromHttp(status.value, text, retryAfter)
}

internal suspend fun <T> withNetworkErrors(block: suspend () -> T): T = try {
    block()
} catch (exception: AiException) {
    throw exception
} catch (exception: CancellationException) {
    throw exception
} catch (exception: Throwable) {
    val name = exception::class.simpleName.orEmpty()
    val message = exception.message.orEmpty()
    val kind = if (
        name.contains("Timeout", ignoreCase = true) ||
        message.contains("timeout", ignoreCase = true) ||
        message.contains("timed out", ignoreCase = true)
    ) {
        AiErrorKind.TIMEOUT
    } else {
        AiErrorKind.NETWORK
    }
    throw AiException(
        kind = kind,
        message = "Provider request failed: ${exception.message ?: name}",
        cause = exception,
    )
}

internal fun JsonObject.string(key: String): String? =
    (this[key] as? JsonPrimitive)?.contentOrNull

internal fun JsonObject.int(key: String): Int? =
    (this[key] as? JsonPrimitive)?.intOrNull

internal fun JsonObject.double(key: String): Double? =
    (this[key] as? JsonPrimitive)?.doubleOrNull

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

internal fun JsonElement.asObject(): JsonObject? = this as? JsonObject

internal fun JsonElement.asArray(): JsonArray? = this as? JsonArray

internal fun JsonArray.objects(): List<JsonObject> = mapNotNull { it as? JsonObject }

internal fun String.normalizedBaseUrl(): String = trim().trimEnd('/')
