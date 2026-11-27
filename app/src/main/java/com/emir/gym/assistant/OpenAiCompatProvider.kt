package com.emir.gym.assistant

import com.fasterxml.jackson.databind.JsonNode
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** OpenAI uyumlu /chat/completions: saf istek kurucu ve yanit ayristirici. */
object OpenAiWire {
    const val EXAMPLE_BASE_URL = "https://openrouter.ai/api/v1"
    const val MAX_TOKENS = 4096

    fun buildBody(req: LlmRequest, supportsImages: Boolean): String {
        val root = Js.obj()
        root.put("model", req.model)
        val msgs = root.putArray("messages")
        msgs.addObject().put("role", "system").put("content", req.system)
        for (m in req.messages) {
            if (m.role == Role.ASSISTANT) {
                val o = msgs.addObject().put("role", "assistant")
                val text = m.blocks.filterIsInstance<Block.Text>().joinToString("\n") { it.text }
                if (text.isEmpty()) o.putNull("content") else o.put("content", text)
                val uses = m.blocks.filterIsInstance<Block.ToolUse>()
                if (uses.isNotEmpty()) {
                    val arr = o.putArray("tool_calls")
                    for (u in uses) {
                        val tc = arr.addObject().put("id", u.id).put("type", "function")
                        // arguments: JSON NESNESI degil, JSON metni.
                        tc.putObject("function").put("name", u.name).put("arguments", Js.parse(u.argsJson)?.takeIf { it.isObject }?.let(Js::str) ?: "{}")
                    }
                }
                continue
            }
            // Arac sonuclari assistant'in tool_calls mesajindan hemen sonra, her biri ayri 'tool' mesaji.
            for (b in m.blocks.filterIsInstance<Block.ToolResult>()) {
                msgs.addObject().put("role", "tool").put("tool_call_id", b.id).put("content", b.content)
            }
            val rest = m.blocks.filter { it !is Block.ToolResult }
            if (rest.isEmpty()) continue
            val hasImage = rest.any { it is Block.Image }
            val o = msgs.addObject().put("role", "user")
            if (!hasImage) {
                o.put("content", rest.mapNotNull { (it as? Block.Text)?.text ?: (it as? Block.Context)?.text }.filter { it.isNotEmpty() }.joinToString("\n\n"))
            } else {
                val parts = o.putArray("content")
                for (b in rest) when (b) {
                    is Block.Text -> if (b.text.isNotEmpty()) parts.addObject().put("type", "text").put("text", b.text)
                    is Block.Context -> parts.addObject().put("type", "text").put("text", b.text)
                    is Block.Image -> {
                        val data = if (supportsImages) req.imageBase64(b.path) else null
                        if (data != null) parts.addObject().put("type", "image_url").putObject("image_url").put("url", "data:${b.mime};base64,$data")
                        else parts.addObject().put("type", "text").put("text", "[Görsel gönderilmedi]")
                    }
                    else -> {}
                }
            }
        }
        if (req.tools.isNotEmpty()) {
            val tools = root.putArray("tools")
            for (t in req.tools) {
                val f = tools.addObject().put("type", "function").putObject("function")
                f.put("name", t.name).put("description", t.description)
                f.set<JsonNode>("parameters", Js.parse(t.parametersJson))
            }
        }
        root.put("max_tokens", req.maxTokens)
        return Js.str(root)
    }

    fun parseResponse(json: String, newId: () -> String = { "call_" + UUID.randomUUID() }): LlmResult {
        val root = Js.parse(json) ?: return LlmResult(Status.ERROR, error = ErrorMapper.invalid())
        val u = root.get("usage")
        val usage = Usage(u?.get("prompt_tokens")?.asInt(0) ?: 0, u?.get("completion_tokens")?.asInt(0) ?: 0)
        val choice = root.get("choices")?.takeIf { it.isArray && it.size() > 0 }?.get(0)
            ?: return LlmResult(Status.ERROR, usage = usage, error = ErrorMapper.invalid())
        val msg = choice.get("message")
        val finish = Js.text(choice, "finish_reason")
        if (msg?.get("refusal")?.isTextual == true && msg.get("refusal").asText().isNotBlank() || finish == "content_filter")
            return LlmResult(Status.REFUSED, usage = usage, error = ErrorMapper.refused())
        val content = msg?.get("content")
        val text = when {
            content == null || content.isNull -> ""
            content.isTextual -> content.asText()
            content.isArray -> content.mapNotNull { Js.text(it, "text") }.joinToString("\n")
            else -> ""
        }
        val calls = ArrayList<ToolCall>()
        msg?.get("tool_calls")?.forEach { tc ->
            val f = tc.get("function") ?: return@forEach
            val args = f.get("arguments")
            val argsJson = when {
                args == null || args.isNull -> "{}"
                args.isTextual -> args.asText().ifBlank { "{}" }
                else -> Js.str(args)
            }
            calls += ToolCall(Js.text(tc, "id")?.takeIf { it.isNotBlank() } ?: newId(), Js.text(f, "name").orEmpty(), argsJson)
        }
        return when {
            finish == "length" -> LlmResult(Status.MAX_TOKENS, text, emptyList(), usage)
            text.isBlank() && calls.isEmpty() -> LlmResult(Status.ERROR, usage = usage, error = ErrorMapper.invalid())
            else -> LlmResult(Status.COMPLETED, text, calls, usage)
        }
    }
}

class OpenAiCompatProvider(
    private val apiKey: String,
    baseUrl: String,
    private val supportsImages: Boolean,
    private val supportsTools: Boolean,
    private val allowLocalCleartext: Boolean = false,
    private val connectMs: Int = Http.CONNECT_MS,
    private val readMs: Int = Http.READ_MS,
) : LlmProvider {
    private val base = UrlPolicy.normalize(baseUrl)
    override val id = ProviderId.OPENAI_COMPAT
    override val capabilities = Capabilities(supportsImages, supportsTools)

    override suspend fun complete(req: LlmRequest): LlmResult = withContext(Dispatchers.IO) {
        // HTTP reddi: anahtar asla acik metin olarak ag'a cikmaz.
        UrlPolicy.check(base, allowLocalCleartext)?.let { return@withContext LlmResult(Status.ERROR, error = ErrorMapper.badConfig(it)) }
        try {
            val r = Http.postJson(
                "$base/chat/completions", mapOf("Authorization" to "Bearer $apiKey"),
                OpenAiWire.buildBody(req.copy(tools = if (supportsTools) req.tools else emptyList()), supportsImages), connectMs, readMs,
            )
            if (r.status in 200..299) OpenAiWire.parseResponse(r.body)
            else LlmResult(Status.ERROR, error = ErrorMapper.fromHttp(r.status, r.retryAfter, r.body, ProviderId.OPENAI_COMPAT))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            LlmResult(Status.ERROR, error = ErrorMapper.fromThrowable(e))
        }
    }
}
