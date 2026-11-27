package com.emir.gym.assistant

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode

/** Ortak JSON yardimcilari (Jackson; Anthropic SDK ile gelir). */
object Js {
    val mapper: ObjectMapper = ObjectMapper()
    fun obj(): ObjectNode = mapper.createObjectNode()
    fun arr(): ArrayNode = mapper.createArrayNode()
    fun parse(s: String): JsonNode? = try { mapper.readTree(s) } catch (e: Exception) { null }
    fun str(n: JsonNode): String = mapper.writeValueAsString(n)
    fun text(n: JsonNode?, field: String): String? = n?.get(field)?.takeIf { it.isTextual }?.asText()
}

/** ChatMessage <-> contentJson. Anthropic ham icerigi "raw" alaninda, degistirilmeden saklanir. */
object ChatCodec {
    fun encode(m: ChatMessage): String {
        val root = Js.obj()
        val blocks = root.putArray("blocks")
        for (b in m.blocks) {
            val o = blocks.addObject()
            when (b) {
                is Block.Text -> o.put("t", "text").put("text", b.text)
                is Block.Image -> o.put("t", "image").put("path", b.path).put("mime", b.mime)
                is Block.ToolUse -> o.put("t", "tool_use").put("id", b.id).put("name", b.name).put("args", b.argsJson).also { if (b.meta != null) o.put("meta", b.meta) }
                is Block.ToolResult -> o.put("t", "tool_result").put("id", b.id).put("content", b.content).put("err", b.isError).also { if (b.actionId != null) o.put("action", b.actionId) }
                is Block.Context -> o.put("t", "context").put("text", b.text)
            }
        }
        if (m.rawAnthropic != null) {
            val raw = Js.parse(m.rawAnthropic)
            if (raw != null) root.set<JsonNode>("raw", raw)
        }
        return Js.str(root)
    }

    /** Bozuk/bilinmeyen JSON'da bos blok listesi doner (cokmez). */
    fun decode(json: String, role: Role, provider: String = "", model: String = "", id: Long = 0, createdAt: Long = 0): ChatMessage {
        val root = Js.parse(json)
        val blocks = ArrayList<Block>()
        root?.get("blocks")?.forEach { o ->
            when (Js.text(o, "t")) {
                "text" -> blocks += Block.Text(Js.text(o, "text").orEmpty())
                "image" -> blocks += Block.Image(Js.text(o, "path").orEmpty(), Js.text(o, "mime") ?: "image/jpeg")
                "tool_use" -> blocks += Block.ToolUse(Js.text(o, "id").orEmpty(), Js.text(o, "name").orEmpty(), Js.text(o, "args") ?: "{}", Js.text(o, "meta"))
                "tool_result" -> blocks += Block.ToolResult(
                    Js.text(o, "id").orEmpty(), Js.text(o, "content").orEmpty(), o.get("err")?.asBoolean() ?: false,
                    o.get("action")?.takeIf { it.isNumber }?.asLong(),
                )
                "context" -> blocks += Block.Context(Js.text(o, "text").orEmpty())
            }
        }
        val raw = root?.get("raw")?.takeIf { it.isArray }?.let { Js.str(it) }
        return ChatMessage(role, blocks, raw, provider, model, id, createdAt)
    }
}
