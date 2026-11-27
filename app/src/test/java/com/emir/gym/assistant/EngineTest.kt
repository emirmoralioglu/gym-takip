package com.emir.gym.assistant

import com.emir.gym.data.ActivityLog
import com.emir.gym.data.ActivityType
import com.emir.gym.data.BodyWeight
import com.emir.gym.data.Meal
import com.emir.gym.data.MealType
import com.emir.gym.data.NutritionGoals
import com.emir.gym.data.PlanDay
import com.emir.gym.data.PlanDayType
import com.emir.gym.data.PlanExercise
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val TODAY = LocalDate.of(2026, 11, 27) // Cuma

class ChatCodecAndHistoryTest {
    @Test fun codecRoundTripKeepsEverythingIncludingRaw() {
        val raw = """[{"type":"thinking","thinking":"","signature":"S=="},{"type":"text","text":"a"}]"""
        val m = ChatMessage(
            Role.ASSISTANT,
            listOf(Block.Text("a"), Block.ToolUse("t1", "add_water", """{"ml":1}""", """{"sig":"x"}"""), Block.Image("/i.jpg", "image/jpeg"),
                Block.ToolResult("t1", "ok", true, 7L), Block.Context("ctx")),
            raw,
        )
        val back = ChatCodec.decode(ChatCodec.encode(m), Role.ASSISTANT)
        assertEquals(m.blocks, back.blocks)
        assertEquals(Js.parse(raw), Js.parse(back.rawAnthropic!!))
    }

    @Test fun codecSurvivesGarbage() {
        assertTrue(ChatCodec.decode("{{{", Role.USER).blocks.isEmpty())
        assertTrue(ChatCodec.decode("""{"blocks":[{"t":"???"}]}""", Role.USER).blocks.isEmpty())
    }

    private fun u(vararg b: Block) = ChatMessage(Role.USER, b.toList(), provider = "GEMINI", model = "m")
    private fun a(vararg b: Block, raw: String? = null, provider: String = "GEMINI", model: String = "m") = ChatMessage(Role.ASSISTANT, b.toList(), raw, provider, model)

    @Test fun orphanToolUseGetsSyntheticErrorResult() {
        val out = History.prepare(listOf(u(Block.Text("a")), a(Block.ToolUse("t1", "add_water", "{}"))), ProviderId.GEMINI, "m")
        assertEquals(3, out.size)
        val r = out[2].blocks.single() as Block.ToolResult
        assertEquals("t1", r.id); assertTrue(r.isError)
    }

    @Test fun missingOneOfTwoResultsIsFilled() {
        val out = History.prepare(
            listOf(u(Block.Text("a")), a(Block.ToolUse("t1", "x", "{}"), Block.ToolUse("t2", "y", "{}")), u(Block.ToolResult("t1", "ok"))),
            ProviderId.GEMINI, "m",
        )
        val ids = out[2].blocks.filterIsInstance<Block.ToolResult>().map { it.id }.toSet()
        assertEquals(setOf("t1", "t2"), ids)
    }

    @Test fun orphanResultsAreDropped() {
        val out = History.prepare(listOf(u(Block.ToolResult("zzz", "x")), u(Block.Text("a"))), ProviderId.GEMINI, "m")
        assertEquals(1, out.size)
        assertTrue(out[0].blocks.single() is Block.Text)
    }

    @Test fun consecutiveUserMessagesMergeWithResultsFirst() {
        val out = History.prepare(
            listOf(u(Block.Text("ilk")), a(Block.ToolUse("t1", "x", "{}")), u(Block.ToolResult("t1", "ok")), u(Block.Text("yeni"))),
            ProviderId.GEMINI, "m",
        )
        assertEquals(3, out.size)
        assertTrue(out[2].blocks[0] is Block.ToolResult)
        assertTrue(out[2].blocks[1] is Block.Text)
    }

    @Test fun rawKeptOnlyForSameProviderAndModel() {
        val raw = """[{"type":"text","text":"a"}]"""
        val src = listOf(u(Block.Text("q")), a(Block.Text("a"), raw = raw, provider = "ANTHROPIC", model = "claude-opus-5-5"))
        assertNotNull(History.prepare(src, ProviderId.ANTHROPIC, "claude-opus-5-5")[1].rawAnthropic)
        assertNull(History.prepare(src, ProviderId.ANTHROPIC, "claude-sonnet-5-5")[1].rawAnthropic)
        assertNull(History.prepare(src, ProviderId.GEMINI, "claude-opus-5-5")[1].rawAnthropic)
    }

    @Test fun longHistoryIsWindowedAtUserTextBoundary() {
        val msgs = ArrayList<ChatMessage>()
        repeat(50) { i -> msgs += u(Block.Text("q$i")); msgs += a(Block.Text("a$i")) }
        val out = History.prepare(msgs, ProviderId.GEMINI, "m")
        assertTrue(out.size <= History.MAX_MESSAGES)
        assertEquals(Role.USER, out.first().role)
        assertEquals("a49", (out.last().blocks.single() as Block.Text).text)
    }
}

class ToolExecutorTest {
    private val store = FakeActionStore()
    private val writer = RecordingWriter()
    private var auto = false
    private val exec = ToolExecutor(store, writer, { emptyData() }, { auto }, { TODAY }, { 1L })

    private fun call(name: String, json: String) = runBlocking { exec.run(ToolCall("id1", name, json)) }

    @Test fun writeToolOnlyProposesByDefault() {
        val o = call("add_water", """{"ml":250}""")
        assertFalse(o.isError)
        assertNotNull(o.actionId)
        assertTrue(o.text.contains("onayına sunuldu"))
        assertTrue("onaydan once yazilmaz", writer.written.isEmpty())
        assertEquals("PENDING", store.rows[o.actionId]!!.status)
    }

    @Test fun confirmWritesOnceAndDoubleConfirmIsIdempotent() = runBlocking {
        val id = call("add_meal", """{"meal_type":"ogle","description":"Pilav","protein_g":20,"calories_kcal":500}""").actionId!!
        val first = exec.confirm(id)
        val second = exec.confirm(id)
        assertTrue(first is ConfirmResult.Confirmed)
        assertTrue(second is ConfirmResult.AlreadyConfirmed)
        assertEquals(1, writer.written.size)
        assertEquals(MealType.OGLE, (writer.written[0] as ActionPayload.AddMeal).mealType)
    }

    @Test fun concurrentDoubleTapWritesOnce() = runBlocking {
        val id = call("add_water", """{"ml":250}""").actionId!!
        val results = (1..20).map { async(Dispatchers.Default) { exec.confirm(id) } }.map { it.await() }
        assertEquals(1, results.count { it is ConfirmResult.Confirmed })
        assertEquals(1, writer.written.size)
    }

    @Test fun rejectedActionCannotBeConfirmed() = runBlocking {
        val id = call("add_water", """{"ml":250}""").actionId!!
        assertTrue(store.reject(id))
        assertTrue(exec.confirm(id) is ConfirmResult.AlreadyRejected)
        assertTrue(writer.written.isEmpty())
        assertTrue(exec.confirm(999) is ConfirmResult.NotFound)
    }

    @Test fun autoAddOnWritesImmediatelyAndOnlyOnce() = runBlocking {
        auto = true
        val o = call("log_body_weight", """{"kg":79.5}""")
        assertTrue(o.text.startsWith("Eklendi"))
        assertTrue(o.written)
        assertEquals(1, writer.written.size)
        assertTrue(exec.confirm(o.actionId!!) is ConfirmResult.AlreadyConfirmed)
        assertEquals(1, writer.written.size)
    }

    @Test fun invalidArgsNeverCreateActionOrWrite() {
        auto = true
        for (j in listOf("""{"ml":-5}""", """{"ml":9999}""", """{"ml":250,"date":"2026-12-01"}""", "{bozuk")) {
            val o = call("add_water", j)
            assertTrue(j, o.isError)
            assertTrue(o.text.startsWith("Hata"))
        }
        assertTrue(store.rows.isEmpty())
        assertTrue(writer.written.isEmpty())
    }

    @Test fun writerFailureLeavesActionPendingAndReportsHonestly() = runBlocking {
        val failing = ToolExecutor(store, { throw IllegalStateException("disk") }, { emptyData() }, { true }, { TODAY }, { 1L })
        val o = failing.run(ToolCall("i", "add_water", """{"ml":250}"""))
        assertFalse(o.text.startsWith("Eklendi"))
        assertTrue(o.text.contains("onayına sunuldu"))
        assertEquals("PENDING", store.rows[o.actionId]!!.status)
    }

    @Test fun unknownToolIsError() {
        assertTrue(call("rm_rf", "{}").isError)
    }

    @Test fun readToolsDoNotCreateActions() {
        val o = call("get_today_plan", "{}")
        assertFalse(o.isError)
        assertTrue(store.rows.isEmpty())
        assertTrue(call("get_day_summary", """{"date":"2030-01-01"}""").isError)
        assertFalse(call("get_day_summary", "{}").isError)
    }
}

class AssistantContextTest {
    private val plan = listOf(PlanDay(5, "Omuz", PlanDayType.AGIRLIK, ""))
    private val ex = listOf(PlanExercise(1, 5, 0, "Lateral raise", "OMUZ", 3, 12), PlanExercise(2, 5, 1, "Shoulder press machine", "OMUZ", 3, 8))

    @Test fun noWeightMeansNoProteinGoalAndNoMadeUpWeight() {
        val s = AssistantContext.build(TODAY, emptyData(), true)
        assertTrue(s, s.contains("protein hedefi yok"))
        assertTrue(s.contains("kayıtlı tartı yok"))
        assertTrue(s.contains("kalori hedefi yok"))
        assertTrue(s.contains("2026-11-27 Cuma"))
    }

    @Test fun withWeightAndPlan() {
        val data = emptyData(NutritionGoals(calories = 2400)).copy(
            planDays = plan, planExercises = ex,
            weights = listOf(BodyWeight(TODAY.minusDays(2).toEpochDay(), 80.0)),
            meals = listOf(Meal(1, TODAY.toEpochDay(), MealType.KAHVALTI, "Yumurta", 12.0, 180)),
        )
        val s = AssistantContext.build(TODAY, data, true)
        assertTrue(s, s.contains("Omuz"))
        assertTrue(s.contains("Lateral raise 3x12"))
        assertTrue(s.contains("hedef 128-160 g"))
        assertTrue(s.contains("80 kg (2026-11-25)"))
        assertTrue(s.contains("Yumurta"))
        assertTrue(s.contains("hedef 2400 kcal"))
        assertTrue(s.contains("ağırlık 0 / 1"))
    }

    @Test fun daySummaryIncludesActivityAndWeight() {
        val d = TODAY.minusDays(1)
        val data = emptyData().copy(
            activities = listOf(ActivityLog(1, d.toEpochDay(), ActivityType.YUZME, 40, 1200)),
            weights = listOf(BodyWeight(d.toEpochDay(), 79.0)),
        )
        val s = AssistantContext.daySummary(d, data)
        assertTrue(s, s.contains("Yüzme 40 dk 1200 m"))
        assertTrue(s.contains("79 kg"))
    }

    @Test fun noToolsNoticeIsAdded() {
        assertTrue(AssistantContext.build(TODAY, emptyData(), false).contains("kayıt araçları kapalı"))
    }

    @Test fun systemPromptHasNoVariableData() {
        // Sabit sistem metni her istekte ayni kalir: tarih/kisisel veri icermez.
        assertFalse(SystemPrompt.FIXED.contains("2026"))
        assertTrue(SystemPrompt.FIXED.contains("komut"))
    }
}

class EngineTest {
    private val chat = FakeChatStore()
    private val store = FakeActionStore()
    private val writer = RecordingWriter()
    private val usage = MemoryUsageStore()
    private var cfg = AssistantConfig(ProviderId.GEMINI, "gemini-test", "medium", "https://x/v1", false, true, false, null)
    private var key: String? = "test-key-123"
    private var provider: LlmProvider = ScriptedProvider(listOf(LlmResult(Status.COMPLETED, "Selam")))

    private fun engine() = AssistantEngine(
        chat = chat,
        tools = ToolExecutor(store, writer, { emptyData() }, { cfg.autoAdd }, { TODAY }, { 1L }),
        usage = usage,
        keyOf = { key },
        config = { cfg },
        providerFor = { _, _ -> provider },
        contextText = { _, _ -> "CTX" },
        today = { TODAY },
        now = { 5L },
    )

    private fun toolCall(i: Int) = LlmResult(Status.COMPLETED, "", listOf(ToolCall("c$i", "add_water", """{"ml":250}""")))

    @Test fun simpleTurnStoresUserAndAssistantMessages() = runBlocking {
        val o = engine().send("Merhaba")
        assertTrue(o.ok)
        assertEquals("Selam", o.text)
        assertEquals(listOf(Role.USER, Role.ASSISTANT), chat.messages.map { it.role })
        assertTrue(chat.messages[0].blocks[0] is Block.Context)
        assertEquals(1, usage.count(ProviderId.GEMINI, TODAY.toEpochDay()))
    }

    @Test fun systemPromptIsFixedAndContextLivesInUserMessage() = runBlocking {
        val p = ScriptedProvider(listOf(LlmResult(Status.COMPLETED, "ok"))); provider = p
        engine().send("a")
        assertEquals(SystemPrompt.FIXED, p.requests[0].system)
        assertFalse(p.requests[0].system.contains("CTX"))
        assertEquals("CTX", (p.requests[0].messages.last().blocks[0] as Block.Context).text)
    }

    @Test fun noKeyMakesNoRequestAndStoresNothing() = runBlocking {
        key = null
        val p = ScriptedProvider(listOf(LlmResult(Status.COMPLETED, "x"))); provider = p
        val o = engine().send("a")
        assertEquals(AssistantError.Kind.NO_KEY, o.error!!.kind)
        assertTrue(p.requests.isEmpty()); assertTrue(chat.messages.isEmpty())
    }

    @Test fun toolLoopRunsResultsInOneUserMessageThenFinalText() = runBlocking {
        val two = LlmResult(Status.COMPLETED, "", listOf(ToolCall("c1", "add_water", """{"ml":250}"""), ToolCall("c2", "get_today_plan", "{}")))
        val p = ScriptedProvider(listOf(two, LlmResult(Status.COMPLETED, "Öneri hazır"))); provider = p
        val o = engine().send("su ekle")
        assertTrue(o.ok); assertEquals("Öneri hazır", o.text)
        assertEquals(2, o.requests)
        assertEquals(1, o.actionIds.size)
        // user, assistant(tool_use x2), user(tool_result x2), assistant
        assertEquals(4, chat.messages.size)
        val results = chat.messages[2].blocks.filterIsInstance<Block.ToolResult>()
        assertEquals(listOf("c1", "c2"), results.map { it.id })
        assertEquals(o.actionIds[0], results[0].actionId)
        assertTrue("onaysiz yazilmamali", writer.written.isEmpty())
        // ikinci istek sonuclari gormeli
        assertTrue(p.requests[1].messages.last().blocks.any { it is Block.ToolResult })
    }

    @Test fun autoAddWritesDuringLoop() = runBlocking {
        cfg = cfg.copy(autoAdd = true)
        provider = ScriptedProvider(listOf(toolCall(1), LlmResult(Status.COMPLETED, "Eklendi")))
        engine().send("su")
        assertEquals(1, writer.written.size)
    }

    @Test fun infiniteToolLoopStopsAtSixRounds() = runBlocking {
        val p = ScriptedProvider(listOf(toolCall(1))); provider = p
        val o = engine().send("su")
        assertEquals(AssistantError.Kind.LOOP_LIMIT, o.error!!.kind)
        assertEquals(6, p.requests.size)
        assertEquals(6, usage.count(ProviderId.GEMINI, TODAY.toEpochDay()))
        // gecmis tutarli: son mesaj arac sonuclari
        assertEquals(Role.USER, chat.messages.last().role)
    }

    @Test fun dailyLimitStopsAt99PercentAndNeverSwitchesProvider() = runBlocking {
        cfg = cfg.copy(dailyLimit = 3) // floor(2.97) = 2
        val p = ScriptedProvider(listOf(LlmResult(Status.COMPLETED, "ok"))); provider = p
        val e = engine()
        assertTrue(e.send("1").ok)
        assertTrue(e.send("2").ok)
        val o = e.send("3")
        assertEquals(AssistantError.Kind.DAILY_LIMIT, o.error!!.kind)
        assertEquals(2, p.requests.size)
        assertEquals(0, usage.count(ProviderId.ANTHROPIC, TODAY.toEpochDay()))
    }

    @Test fun dailyLimitCountsEveryToolRound() = runBlocking {
        cfg = cfg.copy(dailyLimit = 4) // floor(3.96)=3
        val p = ScriptedProvider(listOf(toolCall(1))); provider = p
        val o = engine().send("su")
        assertEquals(AssistantError.Kind.DAILY_LIMIT, o.error!!.kind)
        assertEquals(3, p.requests.size)
    }

    @Test fun errorsAndRefusalsAreReturnedNotStoredAsAssistantMessages() = runBlocking {
        provider = ScriptedProvider(listOf(LlmResult(Status.REFUSED, error = ErrorMapper.refused())))
        val o = engine().send("a")
        assertEquals(AssistantError.Kind.REFUSED, o.error!!.kind)
        assertEquals(1, chat.messages.size)
        // sonraki mesaj gecmisi bozmaz (ardisik kullanici mesajlari birlesir)
        provider = ScriptedProvider(listOf(LlmResult(Status.COMPLETED, "ok")))
        assertTrue(engine().send("b").ok)
    }

    @Test fun truncatedReplyKeepsPartialTextAndReportsError() = runBlocking {
        provider = ScriptedProvider(listOf(LlmResult(Status.MAX_TOKENS, "yarim")))
        val o = engine().send("a")
        assertEquals(AssistantError.Kind.TRUNCATED, o.error!!.kind)
        assertEquals("yarim", o.text)
    }

    @Test fun imageToTextOnlyModelIsRejectedBeforeSending() = runBlocking {
        val p = ScriptedProvider(listOf(LlmResult(Status.COMPLETED, "x")), Capabilities(supportsImages = false, supportsTools = true)); provider = p
        val o = engine().send("bak", listOf(Block.Image("/a.jpg", "image/jpeg")))
        assertEquals(AssistantError.Kind.IMAGE_UNSUPPORTED, o.error!!.kind)
        assertTrue(p.requests.isEmpty()); assertTrue(chat.messages.isEmpty())
    }

    @Test fun toolsAreOmittedWhenProviderHasNoToolSupport() = runBlocking {
        val p = ScriptedProvider(listOf(LlmResult(Status.COMPLETED, "x")), Capabilities(supportsImages = false, supportsTools = false)); provider = p
        engine().send("a")
        assertTrue(p.requests[0].tools.isEmpty())
    }

    @Test fun emptyOrHugeMessageRejected() = runBlocking {
        assertEquals(AssistantError.Kind.BAD_REQUEST, engine().send("   ").error!!.kind)
        assertEquals(AssistantError.Kind.BAD_REQUEST, engine().send("x".repeat(AssistantEngine.MAX_TEXT + 1)).error!!.kind)
        assertTrue(chat.messages.isEmpty())
    }

    @Test fun openAiHttpBaseUrlRejectedBeforeAnyRequest() = runBlocking {
        cfg = cfg.copy(provider = ProviderId.OPENAI_COMPAT, baseUrl = "http://api.example.com/v1")
        val p = ScriptedProvider(listOf(LlmResult(Status.COMPLETED, "x"))); provider = p
        val o = engine().send("a")
        assertEquals(AssistantError.Kind.BAD_CONFIG, o.error!!.kind)
        assertTrue(p.requests.isEmpty())
    }
}
