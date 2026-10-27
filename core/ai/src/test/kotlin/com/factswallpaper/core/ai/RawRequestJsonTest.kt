package com.factswallpaper.core.ai

import com.factswallpaper.core.ai.client.ProviderHeaderValue
import com.factswallpaper.core.ai.client.ProviderHeadersJson
import com.factswallpaper.core.ai.client.RawRequestJson
import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

class RawRequestJsonTest {

    @Test
    fun `raw parameters require an object and report malformed JSON position`() {
        assertThat(RawRequestJson.validate("""{"stream":false}""").isValid).isTrue()
        val malformed = RawRequestJson.validate("""{"stream":}""")
        val array = RawRequestJson.validate("""["not","an","object"]""")

        assertThat(malformed.isValid).isFalse()
        assertThat(malformed.position).isNotNull()
        assertThat(array.isValid).isFalse()
        assertThat(array.position).isEqualTo(0)
    }

    @Test
    fun `preset insertion deep merges nested objects and overrides scalar values`() {
        val merged = Json.parseToJsonElement(
            RawRequestJson.mergePreset(
                """{"reasoning":{"effort":"high","summary":"auto"},"stream":true}""",
                """{"reasoning":{"effort":"low"},"stream":false}""",
            ),
        ).jsonObject

        assertThat(merged["reasoning"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
            .isEqualTo("low")
        assertThat(merged["reasoning"]!!.jsonObject["summary"]!!.jsonPrimitive.content)
            .isEqualTo("auto")
        assertThat(merged["stream"]!!.jsonPrimitive.content).isEqualTo("false")
    }

    @Test
    fun `raw parameter validation warns for credential-shaped entries`() {
        assertThat(RawRequestJson.looksLikeSecret("""{"api_key":"sk-examplecredential123456"}"""))
            .isTrue()
        assertThat(RawRequestJson.looksLikeSecret("""{"max_output_tokens":8192}"""))
            .isFalse()
    }

    @Test
    fun `extra headers round trip as editable key value rows`() {
        val encoded = ProviderHeadersJson.serialize(
            listOf(
                ProviderHeaderValue(" X-Tenant ", " research "),
                ProviderHeaderValue("", "discard"),
            ),
        )
        val parsed = ProviderHeadersJson.parse(encoded)

        assertThat(parsed.error).isNull()
        assertThat(parsed.entries).containsExactly(ProviderHeaderValue("X-Tenant", "research"))
    }
}
