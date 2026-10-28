package com.factswallpaper.core.ai.client

import com.factswallpaper.core.ai.PageRange
import com.factswallpaper.core.ai.PAGE_TOOL_DESCRIPTION
import com.factswallpaper.core.ai.PAGE_TOOL_NAME
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/** Shared JSON wire format for the native `get_pages` tool. */
internal object PageToolSupport {

    fun parametersJson(): JsonObject = buildJsonObject {
        put("type", "object")
        put(
            "properties",
            buildJsonObject {
                put(
                    "start_page",
                    buildJsonObject {
                        put("type", "integer")
                        put("description", "First page to fetch (1-based, inclusive).")
                    },
                )
                put(
                    "end_page",
                    buildJsonObject {
                        put("type", "integer")
                        put("description", "Last page to fetch (1-based, inclusive).")
                    },
                )
            },
        )
        put("required", buildJsonArray {
            add(JsonPrimitive("start_page"))
            add(JsonPrimitive("end_page"))
        })
    }

    /** OpenAI Chat Completions `tools` entry. */
    fun openAiToolJson(): JsonObject = buildJsonObject {
        put("type", "function")
        put(
            "function",
            buildJsonObject {
                put("name", PAGE_TOOL_NAME)
                put("description", PAGE_TOOL_DESCRIPTION)
                put("parameters", parametersJson())
            },
        )
    }

    /** Gemini `tools[].functionDeclarations[]` entry. */
    fun geminiToolJson(): JsonObject = buildJsonObject {
        put(
            "functionDeclarations",
            buildJsonArray {
                add(
                    buildJsonObject {
                        put("name", PAGE_TOOL_NAME)
                        put("description", PAGE_TOOL_DESCRIPTION)
                        put("parameters", parametersJson())
                    },
                )
            },
        )
    }

    fun parseRange(payload: JsonObject?): PageRange? {
        if (payload == null) return null
        val start = payload["start_page"]?.asInt()
            ?: payload["startPage"]?.asInt()
            ?: return null
        val end = payload["end_page"]?.asInt()
            ?: payload["endPage"]?.asInt()
            ?: return null
        return PageRange(startPage = start, endPage = end)
    }

    private fun JsonElement.asInt(): Int? =
        (this as? JsonPrimitive)?.intOrNull
}
