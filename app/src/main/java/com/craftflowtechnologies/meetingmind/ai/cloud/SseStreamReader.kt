package com.craftflowtechnologies.meetingmind.ai.cloud

import okio.BufferedSource
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared Server-Sent Events frame parser and JSON delta extractors for Gemini & DeepSeek streams.
 *
 * Isolates SSE chunk framing so [GeminiHttpTransport] and [DeepSeek] don't duplicate line
 * buffering or JSON extraction logic.
 */
internal object SseStreamReader {

    /**
     * Reads SSE `data:` events from [source] until EOF or `[DONE]` and invokes [onPayload]
     * for each complete JSON event payload. Supports multi-line `data:` blocks per WHATWG SSE spec.
     */
    inline fun readEvents(source: BufferedSource, onPayload: (String) -> Unit) {
        val eventData = StringBuilder()
        while (true) {
            val line = source.readUtf8Line() ?: break
            when {
                line.isEmpty() -> {
                    if (eventData.isNotEmpty()) {
                        val payload = eventData.toString().trim()
                        eventData.setLength(0)
                        if (payload.isNotEmpty() && payload != "[DONE]") {
                            onPayload(payload)
                        }
                    }
                }
                line.startsWith(":") -> Unit // SSE keepalive comment
                line.startsWith("data:") -> {
                    if (eventData.isNotEmpty()) eventData.append('\n')
                    eventData.append(line.removePrefix("data:").trimStart())
                }
            }
        }
        if (eventData.isNotEmpty()) {
            val payload = eventData.toString().trim()
            if (payload.isNotEmpty() && payload != "[DONE]") {
                onPayload(payload)
            }
        }
    }

    /** Extracts one Gemini SSE JSON chunk into thought delta, text delta, function calls, and usage. */
    data class GeminiChunk(
        val thoughtDelta: String = "",
        val textDelta: String = "",
        val functionCalls: List<GeminiFunctionCall> = emptyList(),
        val usage: GeminiUsage? = null,
        val finishReason: String? = null
    )

    fun parseGeminiChunk(jsonText: String): GeminiChunk? {
        val root = runCatching { JSONObject(jsonText) }.getOrNull() ?: return null
        val candidate = root.optJSONArray("candidates")?.optJSONObject(0)
        val finishReason = candidate?.optString("finishReason")?.takeIf { it.isNotBlank() }
        val parts = candidate?.optJSONObject("content")?.optJSONArray("parts")
        val thought = StringBuilder()
        val text = StringBuilder()
        val calls = mutableListOf<GeminiFunctionCall>()
        for (i in 0 until (parts?.length() ?: 0)) {
            val part = parts!!.optJSONObject(i) ?: continue
            val fc = part.optJSONObject("functionCall")
            if (fc != null) {
                val name = fc.optString("name").trim()
                if (name.isNotEmpty()) {
                    val args = fc.optJSONObject("args")?.toString() ?: "{}"
                    val id = fc.optString("id").takeIf { it.isNotBlank() }
                    calls += GeminiFunctionCall(name = name, argsJson = args, id = id)
                }
                continue
            }
            val t = part.optString("text")
            if (t.isNotEmpty()) {
                if (part.optBoolean("thought")) thought.append(t) else text.append(t)
            }
        }
        val usage = root.optJSONObject("usageMetadata")?.let { u ->
            GeminiUsage(
                promptTokens = u.optInt("promptTokenCount"),
                outputTokens = u.optInt("candidatesTokenCount"),
                cachedTokens = u.optInt("cachedContentTokenCount"),
                thoughtTokens = u.optInt("thoughtsTokenCount"),
                totalTokens = u.optInt("totalTokenCount")
            )
        }
        return GeminiChunk(thought.toString(), text.toString(), calls, usage, finishReason)
    }

    /** Accumulates OpenAI-compatible DeepSeek streaming deltas, including incremental tool_calls. */
    class OpenAiToolCallAccumulator {
        private data class Entry(var id: String = "", var name: String = "", val args: StringBuilder = StringBuilder())
        private val byIndex = LinkedHashMap<Int, Entry>()

        fun accept(toolCalls: JSONArray?) {
            if (toolCalls == null) return
            for (i in 0 until toolCalls.length()) {
                val tc = toolCalls.optJSONObject(i) ?: continue
                val idx = tc.optInt("index", i)
                val entry = byIndex.getOrPut(idx) { Entry() }
                tc.optString("id").takeIf { it.isNotBlank() }?.let { entry.id = it }
                val fn = tc.optJSONObject("function") ?: continue
                fn.optString("name").takeIf { it.isNotBlank() }?.let { entry.name += it }
                fn.optString("arguments").takeIf { it.isNotEmpty() }?.let { entry.args.append(it) }
            }
        }

        fun build(): List<GeminiFunctionCall> = byIndex.values.mapNotNull { entry ->
            val name = entry.name.trim()
            if (name.isEmpty()) null
            else GeminiFunctionCall(name = name, argsJson = entry.args.toString().ifBlank { "{}" }, id = entry.id.ifBlank { null })
        }
    }

    /** Converts Gemini `tools: [{ functionDeclarations: [...] }]` into OpenAI/DeepSeek `tools` array. */
    fun geminiToolsToOpenAiTools(toolsJson: String?): String? {
        val raw = toolsJson?.takeIf { it.isNotBlank() } ?: return null
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        val out = JSONArray()
        for (i in 0 until arr.length()) {
            val decls = arr.optJSONObject(i)?.optJSONArray("functionDeclarations") ?: continue
            for (j in 0 until decls.length()) {
                val d = decls.optJSONObject(j) ?: continue
                val fn = JSONObject()
                    .put("name", d.optString("name"))
                    .put("description", d.optString("description"))
                d.optJSONObject("parameters")?.let { fn.put("parameters", it) }
                out.put(JSONObject().put("type", "function").put("function", fn))
            }
        }
        return if (out.length() > 0) out.toString() else null
    }

    /** Converts Gemini multi-turn `contents` JSON into OpenAI/DeepSeek `messages` array. */
    fun geminiContentsToOpenAiMessages(systemInstruction: String, contentsJson: String?): String? {
        val raw = contentsJson?.takeIf { it.isNotBlank() } ?: return null
        val contents = runCatching { JSONArray(raw) }.getOrNull() ?: return null
        val out = JSONArray()
        if (systemInstruction.isNotBlank()) {
            out.put(JSONObject().put("role", "system").put("content", systemInstruction))
        }
        val syntheticIds = ArrayDeque<Pair<String, String>>()
        var counter = 0
        for (i in 0 until contents.length()) {
            val turn = contents.optJSONObject(i) ?: continue
            val role = turn.optString("role")
            val parts = turn.optJSONArray("parts") ?: continue
            val textBuf = StringBuilder()
            val toolCalls = JSONArray()
            val toolResults = mutableListOf<JSONObject>()
            for (j in 0 until parts.length()) {
                val p = parts.optJSONObject(j) ?: continue
                p.optString("text").takeIf { it.isNotEmpty() }?.let { textBuf.append(it) }
                p.optJSONObject("functionCall")?.let { fc ->
                    val name = fc.optString("name")
                    val id = fc.optString("id").takeIf { it.isNotBlank() } ?: "call_${++counter}"
                    syntheticIds.addLast(name to id)
                    toolCalls.put(
                        JSONObject()
                            .put("id", id)
                            .put("type", "function")
                            .put("function", JSONObject().put("name", name).put("arguments", (fc.optJSONObject("args") ?: JSONObject()).toString()))
                    )
                }
                p.optJSONObject("functionResponse")?.let { fr ->
                    val name = fr.optString("name")
                    val id = fr.optString("id").takeIf { it.isNotBlank() }
                        ?: syntheticIds.firstOrNull { it.first == name }?.also { syntheticIds.remove(it) }?.second
                        ?: "call_${++counter}"
                    val contentStr = fr.optJSONObject("response")?.toString() ?: "{}"
                    toolResults += JSONObject().put("role", "tool").put("tool_call_id", id).put("content", contentStr)
                }
            }
            if (toolResults.isNotEmpty()) {
                toolResults.forEach { out.put(it) }
            } else if (role == "model") {
                val msg = JSONObject().put("role", "assistant")
                msg.put("content", if (textBuf.isNotEmpty()) textBuf.toString() else JSONObject.NULL)
                if (toolCalls.length() > 0) msg.put("tool_calls", toolCalls)
                out.put(msg)
            } else {
                out.put(JSONObject().put("role", "user").put("content", textBuf.toString()))
            }
        }
        return if (out.length() > 0) out.toString() else null
    }
}
