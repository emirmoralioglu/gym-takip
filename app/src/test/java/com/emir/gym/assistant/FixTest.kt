package com.emir.gym.assistant

import java.time.LocalDate
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val DAY = LocalDate.of(2026, 11, 27)

class TruncatedToolUseTest {
    private val rawWithTool = """[{"type":"thinking","thinking":"","signature":"S1"},{"type":"text","text":"yar"},{"type":"tool_use","id":"toolu_half","name":"add_meal","input":{}}]"""

    private fun unmatchedToolUse(bodyJson: String): List<String> {
        val msgs = Js.parse(bodyJson)!!.get("messages")
        val uses = HashSet<String>(); val results = HashSet<String>()
        msgs.forEach { m -> m.get("content").forEach { b ->
            when (b.get("type").asText()) { "tool_use" -> uses += b.get("id").asText(); "tool_result" -> results += b.get("tool_use_id").asText() }
        } }
        return (uses - results).toList()
    }

    @Test fun maxTokensResponseDropsToolUseFromRawContent() {
        val json = """{"content":[{"type":"text","text":"yar"},{"type":"tool_use","id":"toolu_half","name":"add_meal","input":{}}],"stop_reason":"max_tokens"}"""
        val r = AnthropicWire.parseResponse(json, rawWithTool)
        assertEquals(Status.MAX_TOKENS, r.status)
        assertTrue(r.toolCalls.isEmpty())
        assertFalse(r.rawAnthropic!!.contains("tool_use"))
        assertTrue("thinking korunur", r.rawAnthropic!!.contains("S1"))
        assertNull(AnthropicWire.stripToolUse("""[{"type":"tool_use","id":"x","name":"n","input":{}}]"""))
    }

    @Test fun endToEndNextRequestHasNoUnmatchedToolUse() = runBlocking {
        val chat = FakeChatStore()
        val cfg = AssistantConfig(ProviderId.ANTHROPIC, "claude-opus-5-5", "medium", "", false, true, false, null)
        val parsed = AnthropicWire.parseResponse(
            """{"content":[{"type":"text","text":"yar"},{"type":"tool_use","id":"toolu_half","name":"add_meal","input":{}}],"stop_reason":"max_tokens"}""", rawWithTool,
        )
        val p = ScriptedProvider(listOf(parsed, LlmResult(Status.COMPLETED, "ok")), id = ProviderId.ANTHROPIC)
        val engine = AssistantEngine(
            chat, ToolExecutor(FakeActionStore(), RecordingWriter(), { emptyData() }, { false }, { DAY }, { 1L }), MemoryUsageStore(),
            { "k-12345678" }, { cfg }, { _, _ -> p }, { _, _ -> "CTX" }, today = { DAY },
        )
        assertEquals(AssistantError.Kind.TRUNCATED, engine.send("ogun ekle").error!!.kind)
        assertTrue(engine.send("tekrar").ok)
        val body = AnthropicWire.buildBody(p.requests[1])
        assertEquals(emptyList<String>(), unmatchedToolUse(body))
    }

    @Test fun legacyStoredRawWithUnmatchedToolUseIsNotReplayed() {
        // Eski/bozuk kayit: bloklarda tool_use yok ama ham icerikte var.
        val bad = ChatMessage(Role.ASSISTANT, listOf(Block.Text("yar")), rawWithTool, "ANTHROPIC", "claude-opus-5-5")
        val msgs = listOf(ChatMessage(Role.USER, listOf(Block.Text("a"))), bad, ChatMessage(Role.USER, listOf(Block.Text("b"))))
        val prepared = History.prepare(msgs, ProviderId.ANTHROPIC, "claude-opus-5-5")
        assertNull(prepared[1].rawAnthropic)
        val body = AnthropicWire.buildBody(LlmRequest("claude-opus-5-5", "S", prepared, emptyList(), 100))
        assertEquals(emptyList<String>(), unmatchedToolUse(body))
    }

    @Test fun matchedRawIsStillReplayedVerbatim() {
        val raw = """[{"type":"thinking","thinking":"","signature":"S2"},{"type":"tool_use","id":"t1","name":"add_water","input":{"ml":1}}]"""
        val msgs = listOf(
            ChatMessage(Role.USER, listOf(Block.Text("a"))),
            ChatMessage(Role.ASSISTANT, listOf(Block.ToolUse("t1", "add_water", """{"ml":1}""")), raw, "ANTHROPIC", "m"),
            ChatMessage(Role.USER, listOf(Block.ToolResult("t1", "ok"))),
        )
        assertTrue(History.prepare(msgs, ProviderId.ANTHROPIC, "m")[1].rawAnthropic!!.contains("S2"))
    }
}

class RedirectTest {
    @Test fun redirectIsNotFollowedAndKeyNeverReachesSecondHost() {
        for (code in listOf(302, 307, 308)) redirectCase(code)
    }

    private fun redirectCase(code: Int) {
        FakeServer { ex, _ -> FakeServer.reply(ex, 200, "{}") }.use { other ->
            FakeServer { ex, _ ->
                ex.responseHeaders.add("Location", other.url + "/stolen")
                ex.sendResponseHeaders(code, -1)
            }.use { first ->
                val r = Http.postJson(first.url + "/x", mapOf("x-goog-api-key" to "test-key-123"), "{}")
                assertEquals(code, r.status)
                assertTrue("ikinci hosta istek gitmemeli", other.requests.isEmpty())
                val e = runBlocking { GeminiProvider("test-key-123", first.url).complete(
                    LlmRequest("m", "s", listOf(ChatMessage(Role.USER, listOf(Block.Text("a")))), emptyList(), 10)).error!! }
                assertFalse(e.message.contains("test-key"))
                assertTrue(other.requests.isEmpty())
            }
        }
    }
}

class DiscardImagesTest {
    private val img = listOf(Block.Image("/f/a.jpg", "image/jpeg"), Block.Image("/f/b.jpg", "image/jpeg"))
    private val discarded = ArrayList<String>()
    private var cfg = AssistantConfig(ProviderId.GEMINI, "m", "medium", "", true, true, false, null)
    private var key: String? = "k-12345678"
    private var provider: LlmProvider = ScriptedProvider(listOf(LlmResult(Status.COMPLETED, "ok")))
    private val chat = FakeChatStore()

    private fun engine(usage: UsageStore = MemoryUsageStore()) = AssistantEngine(
        chat, ToolExecutor(FakeActionStore(), RecordingWriter(), { emptyData() }, { false }, { DAY }, { 1L }), usage,
        { key }, { cfg }, { _, _ -> provider }, { _, _ -> "CTX" }, today = { DAY },
        discardImages = { l -> discarded += l.map { it.path } },
    )

    @Test fun noKeyDiscards() = runBlocking {
        key = null
        engine().send("a", img)
        assertEquals(listOf("/f/a.jpg", "/f/b.jpg"), discarded)
    }

    @Test fun unsupportedImagesDiscard() = runBlocking {
        provider = ScriptedProvider(listOf(LlmResult(Status.COMPLETED, "x")), Capabilities(false, true))
        engine().send("a", img)
        assertEquals(2, discarded.size)
    }

    @Test fun dailyLimitReachedDiscardsAndStoresNothing() = runBlocking {
        cfg = cfg.copy(dailyLimit = 2) // cap = 1
        val e = engine()
        assertTrue(e.send("1").ok)
        val o = e.send("2", img)
        assertEquals(AssistantError.Kind.DAILY_LIMIT, o.error!!.kind)
        assertEquals(2, discarded.size)
        assertEquals(2, chat.messages.size) // yalniz ilk tur
    }

    @Test fun invalidMessageDiscards() = runBlocking {
        engine().send("x".repeat(AssistantEngine.MAX_TEXT + 1), img)
        assertEquals(2, discarded.size)
    }

    @Test fun acceptedMessageKeepsFiles() = runBlocking {
        assertTrue(engine().send("a", img).ok)
        assertTrue(discarded.isEmpty())
    }

    @Test fun busyEngineDiscardsSecondCallsImages() = runBlocking {
        val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
        provider = object : LlmProvider {
            override val id = ProviderId.GEMINI
            override val capabilities = Capabilities(true, true)
            override suspend fun complete(req: LlmRequest): LlmResult { gate.await(); return LlmResult(Status.COMPLETED, "ok") }
        }
        val e = engine()
        val first = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch { e.send("a") }
        while (!e.isBusy) Thread.sleep(5)
        val o = e.send("b", img)
        assertEquals(AssistantError.Kind.BUSY, o.error!!.kind)
        assertEquals(2, discarded.size)
        gate.complete(Unit); first.join()
    }
}

class ErrorLabelTest {
    private val toolBody = """{"error":{"message":"tools.0.input_schema is invalid function schema"}}"""

    @Test fun toggleHintOnlyForOpenAiCompat() {
        val oa = ErrorMapper.fromHttp(400, null, toolBody, ProviderId.OPENAI_COMPAT)
        assertEquals(AssistantError.Kind.TOOL_SCHEMA, oa.kind)
        assertTrue(oa.message.contains("araç desteğini kapat"))
        for (p in listOf(ProviderId.ANTHROPIC, ProviderId.GEMINI, null)) {
            val e = ErrorMapper.fromHttp(400, null, toolBody, p)
            assertEquals(AssistantError.Kind.BAD_REQUEST, e.kind)
            assertFalse(e.message.contains("araç desteğini"))
        }
        assertFalse(ErrorMapper.toolSchema().message.contains("kapat"))
    }

    @Test fun maxTokensInBadRequestBodyIsGenericBadRequest() {
        assertEquals(AssistantError.Kind.BAD_REQUEST, ErrorMapper.fromHttp(400, null, """{"error":"max_tokens: must be > 0"}""", ProviderId.ANTHROPIC).kind)
    }
}

class LimitHelperTest {
    @Test fun tooSmallLimitsRejectedWithHelpers() {
        assertEquals(2, ConfigRules.minimumUsableLimit())
        assertFalse(ConfigRules.validLimit(1))
        assertTrue(ConfigRules.validLimit(2))
        assertEquals(0, ConfigRules.effectiveCap(1))
        assertEquals(1, ConfigRules.effectiveCap(2))
        assertEquals(99, ConfigRules.effectiveCap(100))
        assertNull(ConfigRules.effectiveCap(null))
        // minimumUsableLimit gercekten kullanilabilir en kucuk sinir
        assertTrue(ConfigRules.effectiveCap(ConfigRules.minimumUsableLimit())!! >= 1)
        assertEquals(0, ConfigRules.effectiveCap(ConfigRules.minimumUsableLimit() - 1))
    }
}

class UiConstantParityTest {
    private fun uiConst(name: String): Int {
        val f = Class.forName("com.emir.gym.ui.ActivitySectionsKt").getDeclaredField(name)
        f.isAccessible = true
        return f.getInt(null)
    }

    @Test fun activityLimitsMatchUiTwins() {
        assertEquals(uiConst("MAX_MINUTES"), ToolDefs.MAX_MINUTES)
        assertEquals(uiConst("MAX_DISTANCE_M"), ToolDefs.MAX_DISTANCE_M)
    }

    @Test fun mealAndWaterLimitsComeFromUiObjects() {
        // Bunlar dogrudan UI nesnelerinden okunur (kopya degil).
        assertEquals(com.emir.gym.ui.MealForm.MAX_KCAL, 5000)
        assertEquals(com.emir.gym.ui.NutritionUi.MAX_WATER_ML, 5000)
    }
    // MAX_NOTE (80): UI'daki not alaninin sinirsiz oldugu icin ikizi yok; yalnizca asistana ozel sinir.
}
