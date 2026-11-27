package com.emir.gym.assistant

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.Timeout
import com.anthropic.core.jsonMapper
import com.anthropic.errors.AnthropicServiceException
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.fasterxml.jackson.databind.JsonNode
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Anthropic istek govdesi (saf) ve yanit ayristirma (saf). Govde Messages API'nin tel bicimidir;
 * saglayici bunu resmi SDK tiplerine cevirir (HTTP'yi SDK yapar).
 *
 * Kurallar: thinking adaptive, output_config.effort acik, temperature/top_p yok, prefill yok,
 * tool_choice auto, thinking bloklari aynen geri gonderilir.
 */
object AnthropicWire {
    const val DEFAULT_MODEL = "claude-opus-5-5"
    val MODELS = listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5")
    val EFFORTS = listOf("low", "medium", "high", "xhigh", "max")
    const val DEFAULT_EFFORT = "medium"
    /** Thinking de bu sinira sayildigi icin 4096'dan yuksek. */
    const val MAX_TOKENS = 8192

    fun buildBody(req: LlmRequest): String {
        val root = Js.obj()
        root.put("model", req.model)
        root.put("max_tokens", req.maxTokens)
        root.put("system", req.system)
        root.putObject("thinking").put("type", "adaptive")
        root.putObject("output_config").put("effort", req.effort?.takeIf { it in EFFORTS } ?: DEFAULT_EFFORT)
        if (req.tools.isNotEmpty()) {
            val tools = root.putArray("tools")
            for (t in req.tools) {
                tools.addObject().put("name", t.name).put("description", t.description)
                    .set<JsonNode>("input_schema", Js.parse(t.parametersJson))
            }
            root.putObject("tool_choice").put("type", "auto")
        }
        val msgs = root.putArray("messages")
        for (m in req.messages) {
            val o = msgs.addObject()
            o.put("role", if (m.role == Role.USER) "user" else "assistant")
            val content = o.putArray("content")
            val raw = if (m.role == Role.ASSISTANT) m.rawAnthropic?.let { Js.parse(it) }?.takeIf { it.isArray } else null
            if (raw != null) {
                raw.forEach { content.add(it) }
                continue
            }
            for (b in m.blocks) when (b) {
                is Block.Text -> if (b.text.isNotEmpty()) content.addObject().put("type", "text").put("text", b.text)
                is Block.Context -> content.addObject().put("type", "text").put("text", b.text)
                is Block.Image -> {
                    val data = req.imageBase64(b.path)
                    if (data != null) {
                        val src = content.addObject().put("type", "image").putObject("source")
                        src.put("type", "base64").put("media_type", b.mime).put("data", data)
                    } else content.addObject().put("type", "text").put("text", "[Görsel artık yok]")
                }
                is Block.ToolUse -> content.addObject().put("type", "tool_use").put("id", b.id).put("name", b.name)
                    .set<JsonNode>("input", Js.parse(b.argsJson)?.takeIf { it.isObject } ?: Js.obj())
                is Block.ToolResult -> content.addObject().put("type", "tool_result").put("tool_use_id", b.id)
                    .put("content", b.content).also { if (b.isError) it.put("is_error", true) }
            }
            if (content.isEmpty) content.addObject().put("type", "text").put("text", "(boş)")
        }
        return Js.str(root)
    }

    /**
     * Yanit govdesinden (Messages API JSON'u) sonuc cikarir. [rawContent]: SDK'nin toParam() ile
     * urettigi, geri gonderilebilir icerik dizisi (thinking dahil).
     */
    fun parseResponse(json: String, rawContent: String?): LlmResult {
        val root = Js.parse(json) ?: return LlmResult(Status.ERROR, error = ErrorMapper.invalid())
        val stop = Js.text(root, "stop_reason")
        val usage = root.get("usage")
        val u = Usage(usage?.get("input_tokens")?.asInt(0) ?: 0, usage?.get("output_tokens")?.asInt(0) ?: 0)
        val text = StringBuilder()
        val calls = ArrayList<ToolCall>()
        root.get("content")?.forEach { b ->
            when (Js.text(b, "type")) {
                "text" -> Js.text(b, "text")?.let { if (text.isNotEmpty()) text.append("\n"); text.append(it) }
                "tool_use" -> calls += ToolCall(Js.text(b, "id").orEmpty(), Js.text(b, "name").orEmpty(), Js.str(b.get("input") ?: Js.obj()))
            }
        }
        val status = when (stop) {
            "refusal" -> return LlmResult(Status.REFUSED, error = ErrorMapper.refused(), usage = u)
            "max_tokens" -> Status.MAX_TOKENS
            "model_context_window_exceeded" -> return LlmResult(
                Status.ERROR, usage = u,
                error = AssistantError(AssistantError.Kind.CONTEXT_TOO_LONG, "Sohbet çok uzadı. Sohbeti temizleyip tekrar dene."),
            )
            else -> Status.COMPLETED // end_turn, tool_use, stop_sequence, pause_turn
        }
        // Kesilmis (max_tokens) yanitta arac cagrisi eksik olabilir: calistirilmaz.
        // Kesilmis yanitta yarim tool_use ham icerikte de birakilmaz (sonucsuz tool_use sonraki istegi 400 yapar).
        val raw = if (status == Status.MAX_TOKENS) stripToolUse(rawContent) else rawContent
        return LlmResult(status, text.toString(), if (status == Status.MAX_TOKENS) emptyList() else calls, u, rawAnthropic = raw)
    }

    /** Ham icerik dizisinden tool_use bloklarini atar; bos kalirsa null. */
    fun stripToolUse(raw: String?): String? {
        val arr = raw?.let { Js.parse(it) }?.takeIf { it.isArray } ?: return null
        val out = Js.arr()
        arr.forEach { if (Js.text(it, "type") != "tool_use") out.add(it) }
        return if (out.size() == 0) null else Js.str(out)
    }

    /** Ham icerikteki tool_use kimlikleri. */
    fun toolUseIds(raw: String?): List<String> =
        raw?.let { Js.parse(it) }?.takeIf { it.isArray }?.filter { Js.text(it, "type") == "tool_use" }?.map { Js.text(it, "id").orEmpty() }.orEmpty()
}

class AnthropicProvider(
    private val apiKey: String,
    private val baseUrl: String? = null,
    private val connectTimeout: Duration = Duration.ofSeconds(20),
    private val readTimeout: Duration = Duration.ofSeconds(90),
) : LlmProvider {
    override val id = ProviderId.ANTHROPIC
    override val capabilities = Capabilities(supportsImages = true, supportsTools = true)

    private val client: AnthropicClient by lazy {
        AnthropicOkHttpClient.builder()
            .apiKey(apiKey)
            .apply { if (baseUrl != null) baseUrl(baseUrl) }
            .timeout(Timeout.builder().connect(connectTimeout).read(readTimeout).write(Duration.ofSeconds(60)).request(Duration.ofSeconds(150)).build())
            // Her HTTP istegi gunluk sayaca yazilir; SDK sessizce yeniden denemesin.
            .maxRetries(0)
            .build()
    }

    override suspend fun complete(req: LlmRequest): LlmResult = withContext(Dispatchers.IO) {
        try {
            val mapper = jsonMapper()
            val body = mapper.readValue(AnthropicWire.buildBody(req), MessageCreateParams.Body::class.java)
            val message: Message = client.messages().create(MessageCreateParams.builder().body(body).build())
            val raw = mapper.writeValueAsString(message.content().map { it.toParam() })
            AnthropicWire.parseResponse(mapper.writeValueAsString(message), raw)
        } catch (e: AnthropicServiceException) {
            val retry = e.headers().values("retry-after").firstOrNull()
            LlmResult(Status.ERROR, error = ErrorMapper.fromHttp(e.statusCode(), retry, runCatching { e.body().toString() }.getOrNull(), ProviderId.ANTHROPIC))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            // Istisna metni (anahtar/govde icerebilir) kullaniciya gosterilmez.
            LlmResult(Status.ERROR, error = ErrorMapper.fromThrowable(e))
        }
    }
}
