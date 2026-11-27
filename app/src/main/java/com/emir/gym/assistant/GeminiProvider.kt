package com.emir.gym.assistant

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Gemini generateContent: saf istek kurucu ve yanit ayristirici.
 * Sema: Google'in OpenAPI alt kumesi (type buyuk harf; additionalProperties vb. yok).
 */
object GeminiWire {
    const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    const val DEFAULT_MODEL = "gemini-2.5-flash"
    const val MAX_TOKENS = 4096

    private val ALLOWED = setOf("type", "description", "enum", "properties", "required", "items", "minimum", "maximum", "format", "nullable")

    /** Tarafsiz JSON-Schema -> Gemini Schema: desteklenmeyen alanlar atilir, tur adlari buyuk harf olur. */
    fun convertSchema(n: JsonNode): ObjectNode {
        val out = Js.obj()
        n.fields().forEach { (k, v) ->
            if (k !in ALLOWED) return@forEach
            when (k) {
                "type" -> out.put("type", v.asText().uppercase())
                "properties" -> { val p = out.putObject("properties"); v.fields().forEach { (pk, pv) -> p.set<JsonNode>(pk, convertSchema(pv)) } }
                "items" -> out.set<JsonNode>("items", convertSchema(v))
                else -> out.set<JsonNode>(k, v)
            }
        }
        return out
    }

    fun buildBody(req: LlmRequest): String {
        val root = Js.obj()
        root.putObject("systemInstruction").putArray("parts").addObject().put("text", req.system)
        val idToName = HashMap<String, String>()
        val idToGid = HashMap<String, String?>()
        val contents = root.putArray("contents")
        for (m in req.messages) {
            val parts = Js.arr()
            for (b in m.blocks) when (b) {
                is Block.Text -> if (b.text.isNotEmpty()) parts.addObject().put("text", b.text)
                is Block.Context -> parts.addObject().put("text", b.text)
                is Block.Image -> {
                    val data = req.imageBase64(b.path)
                    if (data != null) parts.addObject().putObject("inlineData").put("mimeType", b.mime).put("data", data)
                    else parts.addObject().put("text", "[Görsel artık yok]")
                }
                is Block.ToolUse -> {
                    val meta = b.meta?.let { Js.parse(it) }
                    idToName[b.id] = b.name
                    idToGid[b.id] = Js.text(meta, "gid")
                    val part = parts.addObject()
                    val fc = part.putObject("functionCall")
                    fc.put("name", b.name)
                    Js.text(meta, "gid")?.let { fc.put("id", it) }
                    fc.set<JsonNode>("args", Js.parse(b.argsJson)?.takeIf { it.isObject } ?: Js.obj())
                    Js.text(meta, "sig")?.let { part.put("thoughtSignature", it) }
                }
                is Block.ToolResult -> {
                    val fr = parts.addObject().putObject("functionResponse")
                    fr.put("name", idToName[b.id] ?: "unknown")
                    idToGid[b.id]?.let { fr.put("id", it) }
                    fr.putObject("response").put(if (b.isError) "error" else "result", b.content)
                }
            }
            if (parts.isEmpty) parts.addObject().put("text", "(boş)")
            val c = contents.addObject()
            c.put("role", if (m.role == Role.USER) "user" else "model")
            c.set<JsonNode>("parts", parts)
        }
        if (req.tools.isNotEmpty()) {
            val decls = root.putArray("tools").addObject().putArray("functionDeclarations")
            for (t in req.tools) {
                val d = decls.addObject().put("name", t.name).put("description", t.description)
                val schema = convertSchema(Js.parse(t.parametersJson) ?: Js.obj())
                // Bos parametreli OBJECT bazi surumlerde reddedilir: parametresiz fonksiyonda alan hic yazilmaz.
                if ((schema.get("properties")?.size() ?: 0) > 0) d.set<JsonNode>("parameters", schema)
            }
        }
        root.putObject("generationConfig").put("maxOutputTokens", req.maxTokens)
        return Js.str(root)
    }

    private val BLOCKED = setOf("SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST", "SPII", "IMAGE_SAFETY", "IMAGE_PROHIBITED_CONTENT", "RECITATION", "PUP_LIMITED_DISABLED", "ESCALATION")

    fun parseResponse(json: String, newId: () -> String = { "g_" + UUID.randomUUID() }): LlmResult {
        val root = Js.parse(json) ?: return LlmResult(Status.ERROR, error = ErrorMapper.invalid())
        val um = root.get("usageMetadata")
        val usage = Usage(um?.get("promptTokenCount")?.asInt(0) ?: 0, um?.get("candidatesTokenCount")?.asInt(0) ?: 0)
        val block = Js.text(root.get("promptFeedback"), "blockReason")
        if (block != null && block != "BLOCK_REASON_UNSPECIFIED") return LlmResult(Status.REFUSED, usage = usage, error = ErrorMapper.refused())
        val cand = root.get("candidates")?.takeIf { it.isArray && it.size() > 0 }?.get(0)
            ?: return LlmResult(Status.ERROR, usage = usage, error = ErrorMapper.invalid())
        val finish = Js.text(cand, "finishReason")
        val text = StringBuilder()
        val calls = ArrayList<ToolCall>()
        cand.get("content")?.get("parts")?.forEach { p ->
            if (p.get("thought")?.asBoolean() == true) return@forEach // dusunce ozeti gosterilmez
            p.get("text")?.takeIf { it.isTextual }?.let { if (text.isNotEmpty()) text.append("\n"); text.append(it.asText()) }
            val fc = p.get("functionCall")
            if (fc != null && fc.isObject) {
                val gid = Js.text(fc, "id")
                val sig = Js.text(p, "thoughtSignature")
                val meta = if (gid != null || sig != null) Js.str(Js.obj().also { o -> gid?.let { o.put("gid", it) }; sig?.let { o.put("sig", it) } }) else null
                calls += ToolCall(gid ?: newId(), Js.text(fc, "name").orEmpty(), Js.str(fc.get("args")?.takeIf { it.isObject } ?: Js.obj()), meta)
            }
        }
        return when {
            finish in BLOCKED -> LlmResult(Status.REFUSED, usage = usage, error = ErrorMapper.refused())
            finish == "MALFORMED_FUNCTION_CALL" || finish == "UNEXPECTED_TOOL_CALL" || finish == "MISSING_THOUGHT_SIGNATURE" ->
                LlmResult(Status.ERROR, usage = usage, error = ErrorMapper.toolSchema())
            finish == "MAX_TOKENS" -> LlmResult(Status.MAX_TOKENS, text.toString(), emptyList(), usage)
            text.isEmpty() && calls.isEmpty() -> LlmResult(Status.ERROR, usage = usage, error = ErrorMapper.invalid())
            else -> LlmResult(Status.COMPLETED, text.toString(), calls, usage)
        }
    }
}

class GeminiProvider(
    private val apiKey: String,
    private val baseUrl: String = GeminiWire.BASE_URL,
    private val connectMs: Int = Http.CONNECT_MS,
    private val readMs: Int = Http.READ_MS,
) : LlmProvider {
    override val id = ProviderId.GEMINI
    override val capabilities = Capabilities(supportsImages = true, supportsTools = true)

    override suspend fun complete(req: LlmRequest): LlmResult = withContext(Dispatchers.IO) {
        try {
            val url = "${UrlPolicy.normalize(baseUrl)}/models/${java.net.URLEncoder.encode(req.model, "UTF-8")}:generateContent"
            val r = Http.postJson(url, mapOf("x-goog-api-key" to apiKey), GeminiWire.buildBody(req), connectMs, readMs)
            if (r.status in 200..299) GeminiWire.parseResponse(r.body)
            else LlmResult(Status.ERROR, error = ErrorMapper.fromHttp(r.status, r.retryAfter, r.body, ProviderId.GEMINI))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            LlmResult(Status.ERROR, error = ErrorMapper.fromThrowable(e))
        }
    }
}
