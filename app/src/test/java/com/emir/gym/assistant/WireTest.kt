package com.emir.gym.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun req(
    messages: List<ChatMessage>,
    tools: List<ToolSpec> = ToolDefs.all,
    model: String = "m",
    effort: String? = null,
    images: (String) -> String? = { "QUJD" },
) = LlmRequest(model, "SISTEM", messages, tools, 1000, effort, images)

private fun user(vararg b: Block) = ChatMessage(Role.USER, b.toList())
private fun asst(vararg b: Block, raw: String? = null) = ChatMessage(Role.ASSISTANT, b.toList(), raw)

class AnthropicWireTest {
    private fun body(r: LlmRequest) = Js.parse(AnthropicWire.buildBody(r))!!

    @Test fun requestShapeFollowsOpus55Rules() {
        val b = body(req(listOf(user(Block.Text("selam"))), model = "claude-opus-5-5", effort = "medium"))
        assertEquals("claude-opus-5-5", b.get("model").asText())
        assertEquals("adaptive", b.get("thinking").get("type").asText())
        assertEquals("medium", b.get("output_config").get("effort").asText())
        assertEquals("SISTEM", b.get("system").asText())
        assertEquals("auto", b.get("tool_choice").get("type").asText())
        assertFalse(b.has("temperature"))
        assertFalse(b.has("top_p"))
        assertFalse(b.has("top_k"))
        assertFalse(b.has("budget_tokens"))
        assertFalse(b.get("thinking").has("budget_tokens"))
        // prefill yok: son mesaj kullanici
        assertEquals("user", b.get("messages").last().get("role").asText())
        assertEquals(ToolDefs.all.size, b.get("tools").size())
        assertEquals("object", b.get("tools").get(0).get("input_schema").get("type").asText())
    }

    @Test fun effortDefaultsToMediumWhenInvalidAndStaysConfigurable() {
        assertEquals("medium", body(req(listOf(user(Block.Text("a"))), effort = "bogus")).get("output_config").get("effort").asText())
        assertEquals("medium", body(req(listOf(user(Block.Text("a"))), effort = null)).get("output_config").get("effort").asText())
        assertEquals("high", body(req(listOf(user(Block.Text("a"))), effort = "high")).get("output_config").get("effort").asText())
    }

    @Test fun noToolsMeansNoToolChoice() {
        val b = body(req(listOf(user(Block.Text("a"))), tools = emptyList()))
        assertFalse(b.has("tools")); assertFalse(b.has("tool_choice"))
    }

    @Test fun imageBecomesBase64Block() {
        val b = body(req(listOf(user(Block.Context("ctx"), Block.Text("bu ne"), Block.Image("/p.jpg", "image/jpeg")))))
        val c = b.get("messages").get(0).get("content")
        assertEquals("text", c.get(0).get("type").asText())
        assertEquals("ctx", c.get(0).get("text").asText())
        val img = c.get(2)
        assertEquals("image", img.get("type").asText())
        assertEquals("base64", img.get("source").get("type").asText())
        assertEquals("image/jpeg", img.get("source").get("media_type").asText())
        assertEquals("QUJD", img.get("source").get("data").asText())
    }

    @Test fun missingImageFileBecomesPlaceholderNotCrash() {
        val b = body(req(listOf(user(Block.Image("/yok.jpg", "image/jpeg"))), images = { null }))
        assertTrue(b.get("messages").get(0).get("content").get(0).get("text").asText().contains("Görsel"))
    }

    @Test fun toolResultsGoInOneUserMessageAndRawAssistantIsReplayedVerbatim() {
        val raw = """[{"type":"thinking","thinking":"","signature":"SIG_ABC=="},{"type":"tool_use","id":"tu_1","name":"add_water","input":{"ml":250}},{"type":"tool_use","id":"tu_2","name":"get_today_plan","input":{}}]"""
        val msgs = listOf(
            user(Block.Text("su ekle")),
            asst(Block.ToolUse("tu_1", "add_water", """{"ml":250}"""), Block.ToolUse("tu_2", "get_today_plan", "{}"), raw = raw),
            user(Block.ToolResult("tu_1", "Eklendi", false), Block.ToolResult("tu_2", "Hata: x", true)),
        )
        val b = body(req(msgs))
        val m = b.get("messages")
        assertEquals(3, m.size())
        val a = m.get(1).get("content")
        assertEquals("thinking", a.get(0).get("type").asText())
        assertEquals("SIG_ABC==", a.get(0).get("signature").asText())
        assertEquals(raw.let { Js.parse(it) }, a)
        val results = m.get(2).get("content")
        assertEquals(2, results.size())
        assertEquals("tool_result", results.get(0).get("type").asText())
        assertEquals("tu_1", results.get(0).get("tool_use_id").asText())
        assertFalse(results.get(0).has("is_error"))
        assertTrue(results.get(1).get("is_error").asBoolean())
    }

    @Test fun assistantWithoutRawIsRebuiltFromBlocks() {
        val m = body(req(listOf(user(Block.Text("a")), asst(Block.Text("b"), Block.ToolUse("t", "add_water", """{"ml":1}""")), user(Block.ToolResult("t", "ok"))))).get("messages")
        val c = m.get(1).get("content")
        assertEquals("text", c.get(0).get("type").asText())
        assertEquals(1, c.get(1).get("input").get("ml").asInt())
    }

    @Test fun parseToolUseAndTextWithRaw() {
        val json = """{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","content":[
          {"type":"thinking","thinking":"","signature":"S"},
          {"type":"text","text":"Ekliyorum"},
          {"type":"tool_use","id":"tu_9","name":"add_water","input":{"ml":300}}],
          "stop_reason":"tool_use","usage":{"input_tokens":120,"output_tokens":40}}"""
        val r = AnthropicWire.parseResponse(json, "[RAW]")
        assertEquals(Status.COMPLETED, r.status)
        assertEquals("Ekliyorum", r.text)
        assertEquals(1, r.toolCalls.size)
        assertEquals("tu_9", r.toolCalls[0].id)
        assertEquals(300, Js.parse(r.toolCalls[0].argsJson)!!.get("ml").asInt())
        assertEquals("[RAW]", r.rawAnthropic)
        assertEquals(120, r.usage.inputTokens)
    }

    @Test fun parseRefusalAndMaxTokens() {
        val refusal = AnthropicWire.parseResponse("""{"content":[],"stop_reason":"refusal","stop_details":{"category":"cyber"}}""", null)
        assertEquals(Status.REFUSED, refusal.status)
        assertEquals(AssistantError.Kind.REFUSED, refusal.error!!.kind)
        val cut = AnthropicWire.parseResponse("""{"content":[{"type":"text","text":"yarim"},{"type":"tool_use","id":"x","name":"add_water","input":{}}],"stop_reason":"max_tokens"}""", "[]")
        assertEquals(Status.MAX_TOKENS, cut.status)
        assertEquals("yarim", cut.text)
        assertTrue("kesik yanitta arac calistirilmaz", cut.toolCalls.isEmpty())
        assertEquals(Status.COMPLETED, AnthropicWire.parseResponse("""{"content":[{"type":"text","text":"a"}],"stop_reason":"end_turn"}""", null).status)
        assertEquals(AssistantError.Kind.INVALID_RESPONSE, AnthropicWire.parseResponse("not json", null).error!!.kind)
    }
}

class GeminiWireTest {
    private fun body(r: LlmRequest) = Js.parse(GeminiWire.buildBody(r))!!

    @Test fun requestShape() {
        val b = body(req(listOf(user(Block.Context("ctx"), Block.Text("selam"), Block.Image("/p", "image/jpeg")))))
        assertEquals("SISTEM", b.get("systemInstruction").get("parts").get(0).get("text").asText())
        val c = b.get("contents").get(0)
        assertEquals("user", c.get("role").asText())
        assertEquals("selam", c.get("parts").get(1).get("text").asText())
        assertEquals("QUJD", c.get("parts").get(2).get("inlineData").get("data").asText())
        assertEquals("image/jpeg", c.get("parts").get(2).get("inlineData").get("mimeType").asText())
        assertEquals(1000, b.get("generationConfig").get("maxOutputTokens").asInt())
        assertFalse("anahtar govdede olmamali", b.toString().contains("key"))
    }

    @Test fun functionDeclarationsUseOpenApiSubset() {
        val b = body(req(listOf(user(Block.Text("a")))))
        val decls = b.get("tools").get(0).get("functionDeclarations")
        assertEquals(ToolDefs.all.size, decls.size())
        val meal = decls.first { it.get("name").asText() == "add_meal" }
        assertEquals("OBJECT", meal.get("parameters").get("type").asText())
        assertEquals("STRING", meal.get("parameters").get("properties").get("meal_type").get("type").asText())
        assertEquals("INTEGER", meal.get("parameters").get("properties").get("calories_kcal").get("type").asText())
        assertFalse(b.toString().contains("additionalProperties"))
        val plan = decls.first { it.get("name").asText() == "get_today_plan" }
        assertFalse("parametresiz fonksiyonda bos OBJECT yazilmaz", plan.has("parameters"))
    }

    @Test fun convertSchemaDropsUnsupportedFields() {
        val s = GeminiWire.convertSchema(Js.parse("""{"type":"object","additionalProperties":false,"title":"x","properties":{"a":{"type":"string","default":"z","enum":["x"]}}}""")!!)
        assertFalse(s.has("additionalProperties")); assertFalse(s.has("title"))
        assertFalse(s.get("properties").get("a").has("default"))
        assertEquals("x", s.get("properties").get("a").get("enum").get(0).asText())
    }

    @Test fun toolRoundTripUsesFunctionCallAndResponseWithSignature() {
        val meta = """{"gid":"gc1","sig":"SIGX"}"""
        val msgs = listOf(
            user(Block.Text("su")),
            asst(Block.ToolUse("gc1", "add_water", """{"ml":200}""", meta)),
            user(Block.ToolResult("gc1", "Eklendi")),
        )
        val c = body(req(msgs)).get("contents")
        assertEquals("model", c.get(1).get("role").asText())
        val part = c.get(1).get("parts").get(0)
        assertEquals("add_water", part.get("functionCall").get("name").asText())
        assertEquals(200, part.get("functionCall").get("args").get("ml").asInt())
        assertEquals("gc1", part.get("functionCall").get("id").asText())
        assertEquals("SIGX", part.get("thoughtSignature").asText())
        val fr = c.get(2).get("parts").get(0).get("functionResponse")
        assertEquals("add_water", fr.get("name").asText())
        assertEquals("Eklendi", fr.get("response").get("result").asText())
        assertEquals("gc1", fr.get("id").asText())
        // hata sonucu "error" anahtariyla
        val e = body(req(msgs.dropLast(1) + user(Block.ToolResult("gc1", "Hata: x", true)))).get("contents").get(2)
        assertEquals("Hata: x", e.get("parts").get(0).get("functionResponse").get("response").get("error").asText())
    }

    @Test fun parseText() {
        val r = GeminiWire.parseResponse("""{"candidates":[{"content":{"role":"model","parts":[{"text":"Merhaba"}]},"finishReason":"STOP"}],"usageMetadata":{"promptTokenCount":7,"candidatesTokenCount":3}}""")
        assertEquals(Status.COMPLETED, r.status)
        assertEquals("Merhaba", r.text)
        assertEquals(7, r.usage.inputTokens)
    }

    @Test fun parseFunctionCallKeepsSignatureAndGeneratesId() {
        val r = GeminiWire.parseResponse(
            """{"candidates":[{"content":{"parts":[{"functionCall":{"name":"add_water","args":{"ml":250}},"thoughtSignature":"SIG1"}]},"finishReason":"STOP"}]}""",
            newId = { "gen1" },
        )
        assertEquals(1, r.toolCalls.size)
        assertEquals("gen1", r.toolCalls[0].id)
        assertEquals(250, Js.parse(r.toolCalls[0].argsJson)!!.get("ml").asInt())
        assertEquals("SIG1", Js.parse(r.toolCalls[0].meta!!)!!.get("sig").asText())
    }

    @Test fun parseSafetyAndMaxTokensAndEmpty() {
        assertEquals(Status.REFUSED, GeminiWire.parseResponse("""{"promptFeedback":{"blockReason":"SAFETY"}}""").status)
        assertEquals(Status.REFUSED, GeminiWire.parseResponse("""{"candidates":[{"finishReason":"SAFETY"}]}""").status)
        assertEquals(AssistantError.Kind.REFUSED, GeminiWire.parseResponse("""{"candidates":[{"finishReason":"PROHIBITED_CONTENT"}]}""").error!!.kind)
        val cut = GeminiWire.parseResponse("""{"candidates":[{"content":{"parts":[{"text":"yar"}]},"finishReason":"MAX_TOKENS"}]}""")
        assertEquals(Status.MAX_TOKENS, cut.status); assertEquals("yar", cut.text)
        assertEquals(AssistantError.Kind.TOOL_SCHEMA, GeminiWire.parseResponse("""{"candidates":[{"finishReason":"MALFORMED_FUNCTION_CALL"}]}""").error!!.kind)
        assertEquals(AssistantError.Kind.INVALID_RESPONSE, GeminiWire.parseResponse("""{"candidates":[]}""").error!!.kind)
        assertEquals(AssistantError.Kind.INVALID_RESPONSE, GeminiWire.parseResponse("""{"candidates":[{"content":{"parts":[]},"finishReason":"STOP"}]}""").error!!.kind)
    }

    @Test fun thoughtPartsAreHidden() {
        val r = GeminiWire.parseResponse("""{"candidates":[{"content":{"parts":[{"text":"dusunuyorum","thought":true},{"text":"Cevap"}]},"finishReason":"STOP"}]}""")
        assertEquals("Cevap", r.text)
    }
}

class OpenAiWireTest {
    private fun body(r: LlmRequest, images: Boolean = true) = Js.parse(OpenAiWire.buildBody(r, images))!!

    @Test fun requestShape() {
        val b = body(req(listOf(user(Block.Context("ctx"), Block.Text("selam")))))
        assertEquals("m", b.get("model").asText())
        assertEquals(1000, b.get("max_tokens").asInt())
        assertEquals("system", b.get("messages").get(0).get("role").asText())
        val u = b.get("messages").get(1)
        assertEquals("user", u.get("role").asText())
        assertEquals("ctx\n\nselam", u.get("content").asText())
        val t = b.get("tools").get(0)
        assertEquals("function", t.get("type").asText())
        assertEquals("add_meal", t.get("function").get("name").asText())
        assertEquals("object", t.get("function").get("parameters").get("type").asText())
    }

    @Test fun imageUsesDataUrlOnlyWhenSupported() {
        val m = listOf(user(Block.Text("bak"), Block.Image("/p", "image/jpeg")))
        val on = body(req(m), images = true).get("messages").get(1).get("content")
        assertEquals("image_url", on.get(1).get("type").asText())
        assertEquals("data:image/jpeg;base64,QUJD", on.get(1).get("image_url").get("url").asText())
        val off = body(req(m), images = false).get("messages").get(1).get("content")
        assertEquals("text", off.get(1).get("type").asText())
        assertFalse(off.toString().contains("QUJD"))
    }

    @Test fun toolCallsAndSeparateToolMessages() {
        val msgs = listOf(
            user(Block.Text("su")),
            asst(Block.Text("tamam"), Block.ToolUse("c1", "add_water", """{"ml":250}"""), Block.ToolUse("c2", "get_today_plan", "{}")),
            user(Block.ToolResult("c1", "ok"), Block.ToolResult("c2", "plan", false), Block.Text("devam")),
        )
        val m = body(req(msgs)).get("messages")
        // system, user, assistant, tool, tool, user
        assertEquals(6, m.size())
        val a = m.get(2)
        assertEquals("tamam", a.get("content").asText())
        val tc = a.get("tool_calls").get(0)
        assertEquals("c1", tc.get("id").asText())
        assertEquals("function", tc.get("type").asText())
        assertTrue("arguments JSON metni olmali", tc.get("function").get("arguments").isTextual)
        assertEquals(250, Js.parse(tc.get("function").get("arguments").asText())!!.get("ml").asInt())
        assertEquals("tool", m.get(3).get("role").asText())
        assertEquals("c1", m.get(3).get("tool_call_id").asText())
        assertEquals("c2", m.get(4).get("tool_call_id").asText())
        assertEquals("user", m.get(5).get("role").asText())
    }

    @Test fun assistantToolCallOnlyHasNullContent() {
        val m = body(req(listOf(user(Block.Text("a")), asst(Block.ToolUse("c1", "add_water", "{}")), user(Block.ToolResult("c1", "ok"))))).get("messages")
        assertTrue(m.get(2).get("content").isNull)
    }

    @Test fun parseTextAndToolCalls() {
        val r = OpenAiWire.parseResponse("""{"choices":[{"message":{"role":"assistant","content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"add_water","arguments":"{\"ml\":250}"}}]},"finish_reason":"tool_calls"}],"usage":{"prompt_tokens":11,"completion_tokens":4}}""")
        assertEquals(Status.COMPLETED, r.status)
        assertEquals("call_1", r.toolCalls[0].id)
        assertEquals("""{"ml":250}""", r.toolCalls[0].argsJson)
        assertEquals(11, r.usage.inputTokens)
        val t = OpenAiWire.parseResponse("""{"choices":[{"message":{"content":"Selam"},"finish_reason":"stop"}]}""")
        assertEquals("Selam", t.text)
    }

    @Test fun parseLengthRefusalAndInvalid() {
        assertEquals(Status.MAX_TOKENS, OpenAiWire.parseResponse("""{"choices":[{"message":{"content":"kes"},"finish_reason":"length"}]}""").status)
        assertEquals(Status.REFUSED, OpenAiWire.parseResponse("""{"choices":[{"message":{"content":null,"refusal":"no"},"finish_reason":"stop"}]}""").status)
        assertEquals(Status.REFUSED, OpenAiWire.parseResponse("""{"choices":[{"message":{"content":""},"finish_reason":"content_filter"}]}""").status)
        assertEquals(AssistantError.Kind.INVALID_RESPONSE, OpenAiWire.parseResponse("""{"choices":[]}""").error!!.kind)
        assertEquals(AssistantError.Kind.INVALID_RESPONSE, OpenAiWire.parseResponse("<html>").error!!.kind)
    }

    @Test fun brokenToolArgumentsPassThroughForExecutorToReject() {
        val r = OpenAiWire.parseResponse("""{"choices":[{"message":{"content":null,"tool_calls":[{"id":"c","function":{"name":"add_water","arguments":"{bozuk"}}]},"finish_reason":"tool_calls"}]}""")
        assertEquals("{bozuk", r.toolCalls[0].argsJson)
        assertNull(ToolValidator.parseArgs(r.toolCalls[0].argsJson))
        assertNotNull(r.toolCalls[0].id)
    }
}
