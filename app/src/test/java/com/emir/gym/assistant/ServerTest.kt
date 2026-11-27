package com.emir.gym.assistant

import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val KEY = "test-key-123"
private val DAY = LocalDate.of(2026, 11, 27)

private fun simpleReq(model: String, msgs: List<ChatMessage> = listOf(ChatMessage(Role.USER, listOf(Block.Text("selam")))), effort: String? = null) =
    LlmRequest(model, "SISTEM", msgs, ToolDefs.all, 1000, effort)

/** Motoru gercek saglayici + sahte sunucuyla, bellek ici depolarla calistirir. */
private class Rig(provider: (AssistantConfig, String) -> LlmProvider, cfg: AssistantConfig) {
    val chat = FakeChatStore()
    val store = FakeActionStore()
    val writer = RecordingWriter()
    val engine = AssistantEngine(
        chat, ToolExecutor(store, writer, { emptyData() }, { cfg.autoAdd }, { DAY }, { 1L }), MemoryUsageStore(),
        { KEY }, { cfg }, provider, { _, _ -> "CTX" }, today = { DAY }, allowLocalCleartext = true,
    )
}

class GeminiServerTest {
    private fun provider(url: String, readMs: Int = 5_000) = GeminiProvider(KEY, url, 2_000, readMs)

    @Test fun textReplyAndHeaders() = runBlocking {
        FakeServer { ex, _ -> FakeServer.reply(ex, 200, """{"candidates":[{"content":{"parts":[{"text":"Merhaba"}]},"finishReason":"STOP"}]}""") }.use { s ->
            val r = provider(s.url).complete(simpleReq("gemini-2.5-flash"))
            assertEquals("Merhaba", r.text)
            val rec = s.requests.single()
            assertEquals("/models/gemini-2.5-flash:generateContent", rec.path)
            assertEquals(KEY, rec.headers["x-goog-api-key"])
            assertFalse("anahtar URL'de olmamali", rec.path.contains(KEY))
            assertFalse("anahtar govdede olmamali", rec.body.contains(KEY))
        }
    }

    @Test fun toolLoopEndToEnd() = runBlocking {
        val call = """{"candidates":[{"content":{"role":"model","parts":[{"functionCall":{"name":"add_water","args":{"ml":250}}}]},"finishReason":"STOP"}]}"""
        val fin = """{"candidates":[{"content":{"role":"model","parts":[{"text":"Öneri sunuldu"}]},"finishReason":"STOP"}]}"""
        FakeServer(FakeServer.sequence(200 to call, 200 to fin)).use { s ->
            val rig = Rig({ _, k -> GeminiProvider(k, s.url, 2_000, 5_000) }, AssistantConfig(ProviderId.GEMINI, "gemini-2.5-flash", "medium", "", false, true, false, null))
            val o = rig.engine.send("250 ml su ekle")
            assertTrue(o.error?.message, o.ok)
            assertEquals("Öneri sunuldu", o.text)
            assertEquals(2, s.requests.size)
            val second = Js.parse(s.requests[1].body)!!
            val contents = second.get("contents")
            assertEquals("model", contents.get(contents.size() - 2).get("role").asText())
            val fr = contents.get(contents.size() - 1).get("parts").get(0).get("functionResponse")
            assertEquals("add_water", fr.get("name").asText())
            assertTrue(fr.get("response").get("result").asText().contains("onayına sunuldu"))
            assertEquals(1, rig.store.rows.size)
            assertTrue(rig.writer.written.isEmpty())
        }
    }

    @Test fun httpErrorsMapped() = runBlocking {
        val secret = """{"error":{"message":"API key not valid: $KEY"}}"""
        for ((code, kind) in listOf(
            401 to AssistantError.Kind.UNAUTHORIZED, 403 to AssistantError.Kind.UNAUTHORIZED, 404 to AssistantError.Kind.MODEL_NOT_FOUND,
            429 to AssistantError.Kind.RATE_LIMITED, 500 to AssistantError.Kind.SERVER, 503 to AssistantError.Kind.SERVER,
        )) {
            FakeServer { ex, _ -> FakeServer.reply(ex, code, secret, mapOf("Retry-After" to "12")) }.use { s ->
                val r = provider(s.url).complete(simpleReq("m"))
                assertEquals(Status.ERROR, r.status)
                assertEquals("kod $code", kind, r.error!!.kind)
                assertFalse(r.error!!.message.contains(KEY))
                assertFalse(r.error!!.message.contains("not valid"))
                if (code == 429) assertEquals(12, r.error!!.retryAfterSec)
            }
        }
    }

    @Test fun readTimeout() = runBlocking {
        FakeServer { ex, _ -> Thread.sleep(1_500); runCatching { FakeServer.reply(ex, 200, "{}") } }.use { s ->
            val r = provider(s.url, readMs = 300).complete(simpleReq("m"))
            assertEquals(AssistantError.Kind.TIMEOUT, r.error!!.kind)
        }
    }

    @Test fun connectionRefused() = runBlocking {
        val s = FakeServer { ex, _ -> FakeServer.reply(ex, 200, "{}") }
        val url = s.url; s.close()
        val r = provider(url).complete(simpleReq("m"))
        assertTrue(r.error!!.kind == AssistantError.Kind.NETWORK || r.error!!.kind == AssistantError.Kind.TIMEOUT)
    }

    @Test fun safetyBlockedPrompt() = runBlocking {
        FakeServer { ex, _ -> FakeServer.reply(ex, 200, """{"promptFeedback":{"blockReason":"SAFETY"}}""") }.use { s ->
            assertEquals(Status.REFUSED, provider(s.url).complete(simpleReq("m")).status)
        }
    }
}

class OpenAiServerTest {
    private fun provider(url: String, tools: Boolean = true, images: Boolean = false, readMs: Int = 5_000) =
        OpenAiCompatProvider(KEY, url, images, tools, allowLocalCleartext = true, connectMs = 2_000, readMs = readMs)

    @Test fun textReplyAndBearerHeader() = runBlocking {
        FakeServer { ex, _ -> FakeServer.reply(ex, 200, """{"choices":[{"message":{"content":"Selam"},"finish_reason":"stop"}]}""") }.use { s ->
            val r = provider(s.url + "/v1/").complete(simpleReq("meta/llama"))
            assertEquals("Selam", r.text)
            val rec = s.requests.single()
            assertEquals("/v1/chat/completions", rec.path)
            assertEquals("Bearer $KEY", rec.headers["authorization"])
            assertFalse(rec.path.contains(KEY)); assertFalse(rec.body.contains(KEY))
        }
    }

    @Test fun toolLoopEndToEnd() = runBlocking {
        val call = """{"choices":[{"message":{"content":null,"tool_calls":[{"id":"call_1","type":"function","function":{"name":"log_body_weight","arguments":"{\"kg\":80.5}"}}]},"finish_reason":"tool_calls"}]}"""
        val fin = """{"choices":[{"message":{"content":"Kilo önerildi"},"finish_reason":"stop"}]}"""
        FakeServer(FakeServer.sequence(200 to call, 200 to fin)).use { s ->
            val cfg = AssistantConfig(ProviderId.OPENAI_COMPAT, "m", "medium", s.url, false, true, false, null)
            val rig = Rig({ c, k -> OpenAiCompatProvider(k, c.baseUrl, false, true, true, 2_000, 5_000) }, cfg)
            val o = rig.engine.send("kilom 80.5")
            assertTrue(o.error?.message, o.ok)
            assertEquals("Kilo önerildi", o.text)
            val msgs = Js.parse(s.requests[1].body)!!.get("messages")
            val tool = msgs.get(msgs.size() - 1)
            assertEquals("tool", tool.get("role").asText())
            assertEquals("call_1", tool.get("tool_call_id").asText())
            assertEquals(1, rig.store.rows.size)
            assertEquals(80.5, (rig.store.rows.values.single().payload as ActionPayload.LogWeight).kg, 0.0)
        }
    }

    @Test fun toolsDisabledSendsNoTools() = runBlocking {
        FakeServer { ex, _ -> FakeServer.reply(ex, 200, """{"choices":[{"message":{"content":"x"},"finish_reason":"stop"}]}""") }.use { s ->
            provider(s.url, tools = false).complete(simpleReq("m"))
            assertFalse(Js.parse(s.requests.single().body)!!.has("tools"))
        }
    }

    @Test fun httpErrorsMapped() = runBlocking {
        for ((code, kind) in listOf(
            401 to AssistantError.Kind.UNAUTHORIZED, 404 to AssistantError.Kind.MODEL_NOT_FOUND,
            429 to AssistantError.Kind.RATE_LIMITED, 502 to AssistantError.Kind.SERVER,
        )) {
            FakeServer { ex, _ -> FakeServer.reply(ex, code, """{"error":"secret detail $KEY"}""") }.use { s ->
                val r = provider(s.url).complete(simpleReq("m"))
                assertEquals("kod $code", kind, r.error!!.kind)
                assertFalse(r.error!!.message.contains("secret"))
            }
        }
    }

    @Test fun readTimeout() = runBlocking {
        FakeServer { ex, _ -> Thread.sleep(1_500); runCatching { FakeServer.reply(ex, 200, "{}") } }.use { s ->
            assertEquals(AssistantError.Kind.TIMEOUT, provider(s.url, readMs = 300).complete(simpleReq("m")).error!!.kind)
        }
    }

    @Test fun cleartextToRemoteHostRefusedWithoutAnyRequest() = runBlocking {
        val p = OpenAiCompatProvider(KEY, "http://example.com/v1", false, true, allowLocalCleartext = false)
        val r = p.complete(simpleReq("m"))
        assertEquals(AssistantError.Kind.BAD_CONFIG, r.error!!.kind)
    }
}

class AnthropicSdkServerTest {
    private val textReply = """{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","content":[{"type":"thinking","thinking":"","signature":"SIG_ONE"},{"type":"text","text":"Merhaba"}],"stop_reason":"end_turn","stop_sequence":null,"usage":{"input_tokens":10,"output_tokens":5}}"""
    private val toolReply = """{"id":"msg_2","type":"message","role":"assistant","model":"claude-opus-5-5","content":[{"type":"thinking","thinking":"","signature":"SIG_TOOL"},{"type":"tool_use","id":"toolu_1","name":"add_water","input":{"ml":250}}],"stop_reason":"tool_use","stop_sequence":null,"usage":{"input_tokens":10,"output_tokens":5}}"""

    @Test fun sdkSingleTurnSendsOpus55Parameters() = runBlocking {
        FakeServer { ex, _ -> FakeServer.reply(ex, 200, textReply) }.use { s ->
            val r = AnthropicProvider(KEY, s.url).complete(simpleReq("claude-opus-5-5", effort = "medium"))
            assertEquals(r.error?.message, Status.COMPLETED, r.status)
            assertEquals("Merhaba", r.text)
            assertNotNull(r.rawAnthropic)
            assertTrue("thinking imzasi ham icerikte korunur", r.rawAnthropic!!.contains("SIG_ONE"))
            val rec = s.requests.single()
            assertEquals("/v1/messages", rec.path)
            // SDK 3.3.0 anahtari "Authorization: Bearer" olarak gonderir (gozlemlendi); x-api-key da kabul.
            assertTrue(rec.headers["x-api-key"] == KEY || rec.headers["authorization"] == "Bearer $KEY")
            val b = Js.parse(rec.body)!!
            assertEquals("claude-opus-5-5", b.get("model").asText())
            assertEquals("adaptive", b.get("thinking").get("type").asText())
            assertEquals("medium", b.get("output_config").get("effort").asText())
            assertFalse(b.has("temperature")); assertFalse(b.has("top_p"))
            assertEquals("auto", b.get("tool_choice").get("type").asText())
            assertFalse(rec.body.contains(KEY))
        }
    }

    @Test fun sdkToolLoopReplaysThinkingBlockUnchanged() = runBlocking {
        FakeServer(FakeServer.sequence(200 to toolReply, 200 to textReply)).use { s ->
            val cfg = AssistantConfig(ProviderId.ANTHROPIC, "claude-opus-5-5", "medium", "", false, true, false, null)
            val rig = Rig({ _, k -> AnthropicProvider(k, s.url) }, cfg)
            val o = rig.engine.send("250 ml su")
            assertTrue(o.error?.message, o.ok)
            assertEquals(2, s.requests.size)
            val m = Js.parse(s.requests[1].body)!!.get("messages")
            assertEquals(3, m.size())
            val a = m.get(1).get("content")
            assertEquals("thinking", a.get(0).get("type").asText())
            assertEquals("SIG_TOOL", a.get(0).get("signature").asText())
            assertEquals("tool_use", a.get(1).get("type").asText())
            val results = m.get(2).get("content")
            assertEquals("tool_result", results.get(0).get("type").asText())
            assertEquals("toolu_1", results.get(0).get("tool_use_id").asText())
            assertEquals(1, rig.store.rows.size)
            // Sonraki kullanici turunda da thinking aynen geri gider.
            assertTrue(rig.engine.send("tesekkurler").ok)
            val third = s.requests[2].body
            assertTrue("onceki turlarin thinking imzalari aynen geri gonderilir", third.contains("SIG_TOOL") && third.contains("SIG_ONE"))
        }
    }

    @Test fun sdkErrorsMapped() = runBlocking {
        val err = """{"type":"error","error":{"type":"authentication_error","message":"invalid x-api-key $KEY"}}"""
        for ((code, kind) in listOf(
            401 to AssistantError.Kind.UNAUTHORIZED, 403 to AssistantError.Kind.UNAUTHORIZED, 404 to AssistantError.Kind.MODEL_NOT_FOUND,
            429 to AssistantError.Kind.RATE_LIMITED, 500 to AssistantError.Kind.SERVER, 529 to AssistantError.Kind.SERVER,
        )) {
            FakeServer { ex, _ -> FakeServer.reply(ex, code, err, mapOf("retry-after" to "7")) }.use { s ->
                val r = AnthropicProvider(KEY, s.url).complete(simpleReq("claude-opus-5-5"))
                assertEquals("kod $code", kind, r.error?.kind)
                assertFalse(r.error!!.message.contains(KEY))
                assertFalse(r.error!!.message.contains("invalid x-api-key"))
                if (code == 429) assertEquals(7, r.error!!.retryAfterSec)
                assertEquals("SDK yeniden denememeli (sayac dogrulugu)", 1, s.requests.size)
            }
        }
    }

    @Test fun sdkRefusalAndMaxTokens() = runBlocking {
        FakeServer { ex, _ -> FakeServer.reply(ex, 200, """{"id":"m","type":"message","role":"assistant","model":"claude-opus-5-5","content":[],"stop_reason":"refusal","stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":0}}""") }.use { s ->
            assertEquals(Status.REFUSED, AnthropicProvider(KEY, s.url).complete(simpleReq("claude-opus-5-5")).status)
        }
        FakeServer { ex, _ -> FakeServer.reply(ex, 200, """{"id":"m","type":"message","role":"assistant","model":"claude-opus-5-5","content":[{"type":"text","text":"yar"}],"stop_reason":"max_tokens","stop_sequence":null,"usage":{"input_tokens":1,"output_tokens":9}}""") }.use { s ->
            assertEquals(Status.MAX_TOKENS, AnthropicProvider(KEY, s.url).complete(simpleReq("claude-opus-5-5")).status)
        }
    }

    @Test fun sdkReadTimeout() = runBlocking {
        FakeServer { ex, _ -> Thread.sleep(2_000); runCatching { FakeServer.reply(ex, 200, textReply) } }.use { s ->
            val p = AnthropicProvider(KEY, s.url, java.time.Duration.ofSeconds(2), java.time.Duration.ofMillis(400))
            assertEquals(AssistantError.Kind.TIMEOUT, p.complete(simpleReq("claude-opus-5-5")).error!!.kind)
        }
    }

    @Test fun sdkImageBlockSentAsBase64() = runBlocking {
        FakeServer { ex, _ -> FakeServer.reply(ex, 200, textReply) }.use { s ->
            val m = ChatMessage(Role.USER, listOf(Block.Text("bu ne"), Block.Image("/x.jpg", "image/jpeg")))
            AnthropicProvider(KEY, s.url).complete(simpleReq("claude-opus-5-5", listOf(m)).copy(imageBase64 = { "QUJD" }))
            val img = Js.parse(s.requests.single().body)!!.get("messages").get(0).get("content").get(1)
            assertEquals("image", img.get("type").asText())
            assertEquals("QUJD", img.get("source").get("data").asText())
        }
    }
}
