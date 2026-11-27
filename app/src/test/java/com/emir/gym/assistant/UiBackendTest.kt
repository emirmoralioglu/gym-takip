package com.emir.gym.assistant

import com.emir.gym.data.ActivityType
import com.emir.gym.data.MealType
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val T = LocalDate.of(2026, 11, 27)
private fun meal(prot: Double = 30.0, kcal: Int = 500, desc: String = "Tavuk", day: LocalDate = T) =
    ActionPayload.AddMeal(day.toEpochDay(), MealType.OGLE, desc, prot, kcal, 40.0, 10.0)

/** Duzenlenmis onay ve sinirlar. */
class ConfirmEditedTest {
    private val store = FakeActionStore()
    private val writer = RecordingWriter()

    private fun pending(p: ActionPayload = meal()) = runBlocking { store.insert(p, 1L) }

    @Test fun editedValuesAreWrittenNotOriginals() = runBlocking {
        val id = pending(meal(prot = 30.0))
        val r = confirmEditedWith(store, writer, id, meal(prot = 42.5, desc = "  Tavuk pilav "))
        assertTrue(r is ConfirmResult.Confirmed)
        val w = writer.written.single() as ActionPayload.AddMeal
        assertEquals(42.5, w.proteinG, 0.0)
        assertEquals("Tavuk pilav", w.description)
        assertTrue((r as ConfirmResult.Confirmed).summary.contains("42,5") || r.summary.contains("42.5"))
        // Duzenlenmis veri ref'te saklanir ve geri okunur.
        val back = ActionRef.edited(ToolDefs.ADD_MEAL, r.ref) as ActionPayload.AddMeal
        assertEquals(42.5, back.proteinG, 0.0)
        assertEquals("rec:1", ActionRef.parse(r.ref)!!.ref)
    }

    @Test fun limitsAreEnforcedAndNothingWritten() = runBlocking {
        val id = pending()
        listOf(
            meal(prot = 300.1), meal(kcal = 5001), meal(desc = "x".repeat(81)), meal(desc = "  "),
            meal(day = T.plusDays(1)), meal(prot = -1.0),
            meal().copy(carbsG = 1000.1), meal().copy(fatG = 500.1),
        ).forEach { bad ->
            assertTrue("$bad", confirmEditedWith(store, writer, id, bad) is ConfirmResult.Failed)
        }
        assertTrue(writer.written.isEmpty())
        // Hala bekliyor: sonra gecerli deger onaylanabilir.
        assertTrue(confirmEditedWith(store, writer, id, meal(prot = 300.0, kcal = 5000)) is ConfirmResult.Confirmed)
    }

    @Test fun waterAndOtherLimits() {
        assertNull(ActionEdit.check(ActionPayload.AddWater(T.toEpochDay(), 5000), T))
        assertNotNull(ActionEdit.check(ActionPayload.AddWater(T.toEpochDay(), 5001), T))
        assertNotNull(ActionEdit.check(ActionPayload.AddWater(T.toEpochDay(), 0), T))
        assertNotNull(ActionEdit.check(ActionPayload.LogWeight(T.toEpochDay(), 500.5), T))
        assertNotNull(ActionEdit.check(ActionPayload.LogActivity(T.toEpochDay(), ActivityType.SAUNA, 20, 100, ""), T))
        assertNull(ActionEdit.check(ActionPayload.LogActivity(T.toEpochDay(), ActivityType.YUZME, 20, 100, ""), T))
        assertNotNull(ActionEdit.check(ActionPayload.LogActivity(T.toEpochDay(), ActivityType.SAUNA, ToolDefs.MAX_MINUTES + 1, null, ""), T))
    }

    @Test fun typeCannotChange() = runBlocking {
        val id = pending(meal())
        val r = confirmEditedWith(store, writer, id, ActionPayload.AddWater(T.toEpochDay(), 250))
        assertTrue(r is ConfirmResult.Failed)
        assertTrue(writer.written.isEmpty())
        assertEquals("PENDING", store.rows[id]!!.status)
    }

    @Test fun doubleConfirmWritesOnce() = runBlocking {
        val id = pending()
        assertTrue(confirmEditedWith(store, writer, id, meal(prot = 31.0)) is ConfirmResult.Confirmed)
        assertTrue(confirmEditedWith(store, writer, id, meal(prot = 32.0)) is ConfirmResult.AlreadyConfirmed)
        assertEquals(1, writer.written.size)
    }

    @Test fun rejectedAndMissing() = runBlocking {
        val id = pending()
        store.reject(id)
        assertTrue(confirmEditedWith(store, writer, id, meal()) is ConfirmResult.AlreadyRejected)
        assertTrue(confirmEditedWith(store, writer, 999L, meal()) is ConfirmResult.NotFound)
    }

    @Test fun writerFailureIsFailedNotSuccess() = runBlocking {
        val id = pending()
        val boom = RecordWriter { throw IllegalStateException("disk") }
        assertTrue(confirmEditedWith(store, boom, id, meal()) is ConfirmResult.Failed)
        assertEquals("PENDING", store.rows[id]!!.status)
    }

    @Test fun refWithoutEditParsesPlain() {
        assertNull(ActionRef.edited(ToolDefs.ADD_MEAL, "meal:12"))
        assertEquals("meal:12", ActionRef.parse("meal:12")!!.ref)
        assertNull(ActionRef.parse(null))
    }

    @Test fun orphanImageNames() {
        val files = listOf("img_1.jpg", "img_1_thumb.jpg", "img_2.jpg", "img_2_thumb.jpg", "img_3_thumb.jpg")
        assertEquals(listOf("img_2.jpg", "img_2_thumb.jpg", "img_3_thumb.jpg"), ImageOrphans.find(files, setOf("img_1.jpg")))
        assertTrue(ImageOrphans.find(files, setOf("img_1.jpg", "img_2.jpg", "img_3.jpg")).isEmpty())
    }
}

/** Yeniden deneme: mesaj ikilenmez. */
class ResumeTest {
    private val chat = FakeChatStore()
    private val usage = MemoryUsageStore()
    private val cfg = AssistantConfig(ProviderId.GEMINI, "gemini-test", "medium", "https://x/v1", false, true, false, null)
    private var provider: LlmProvider = ScriptedProvider(listOf(LlmResult(Status.COMPLETED, "Selam")))

    private fun engine() = AssistantEngine(
        chat = chat,
        tools = ToolExecutor(FakeActionStore(), RecordingWriter(), { emptyData() }, { false }, { T }, { 1L }),
        usage = usage, keyOf = { "test-key-123" }, config = { cfg }, providerFor = { _, _ -> provider },
        contextText = { _, _ -> "CTX" }, today = { T }, now = { 5L },
    )

    @Test fun resumeAnswersWithoutDuplicatingUserMessage() = runBlocking {
        provider = ScriptedProvider(listOf(LlmResult(Status.ERROR, error = ErrorMapper.fromHttp(500))))
        val e = engine()
        assertFalse(e.send("merhaba").ok)
        assertEquals(1, chat.messages.size)
        provider = ScriptedProvider(listOf(LlmResult(Status.COMPLETED, "Selam")))
        val o = e.resume()
        assertTrue(o.ok)
        assertEquals(listOf(Role.USER, Role.ASSISTANT), chat.messages.map { it.role })
        assertEquals(2, usage.count(ProviderId.GEMINI))
    }

    @Test fun resumeWithoutPendingUserMessageDoesNothing() = runBlocking {
        val e = engine()
        assertEquals(AssistantError.Kind.BAD_REQUEST, e.resume().error?.kind)
        e.send("a")
        assertEquals(AssistantError.Kind.BAD_REQUEST, e.resume().error?.kind)
        assertEquals(2, chat.messages.size)
        assertEquals(1, usage.count(ProviderId.GEMINI))
    }

    @Test fun resumeRespectsQuotaAndKey() = runBlocking {
        provider = ScriptedProvider(listOf(LlmResult(Status.ERROR, error = ErrorMapper.fromHttp(500))))
        val limited = cfg.copy(dailyLimit = 2)
        val e = AssistantEngine(
            chat, ToolExecutor(FakeActionStore(), RecordingWriter(), { emptyData() }, { false }, { T }, { 1L }), usage,
            { "k-123456789" }, { limited }, { _, _ -> provider }, { _, _ -> "" }, today = { T }, now = { 5L },
        )
        e.send("a") // 1 istek (cap=1)
        assertEquals(AssistantError.Kind.DAILY_LIMIT, e.resume().error?.kind)
        assertEquals(1, chat.messages.size)
    }
}
