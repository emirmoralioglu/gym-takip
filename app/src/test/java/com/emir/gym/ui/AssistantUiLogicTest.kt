package com.emir.gym.ui

import com.emir.gym.assistant.ActionPayload
import com.emir.gym.assistant.ActionRef
import com.emir.gym.assistant.AssistantConfig
import com.emir.gym.assistant.AssistantError
import com.emir.gym.assistant.Block
import com.emir.gym.assistant.ChatMessage
import com.emir.gym.assistant.ProviderId
import com.emir.gym.assistant.Role
import com.emir.gym.assistant.ToolDefs
import com.emir.gym.assistant.ToolValidator
import com.emir.gym.data.ActivityType
import com.emir.gym.data.MealType
import com.emir.gym.data.PendingActionEntity
import com.emir.gym.data.PendingStatus
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val TODAY = LocalDate.now()

class ChatFormatTest {
    private fun bold(s: String) = ChatFormat.spans(s).map { it.text to it.bold }

    @Test fun boldSpans() {
        assertEquals(listOf("a " to false, "b" to true, " c" to false), bold("a **b** c"))
        assertEquals(listOf("tamam" to true), bold("**tamam**"))
        assertEquals(listOf("düz" to false), bold("düz"))
    }

    @Test fun unmatchedMarkersStayLiteral() {
        assertEquals(listOf("a **b" to false), bold("a **b"))
        assertEquals(listOf("a " to false, "b" to true, " **c" to false), bold("a **b** **c"))
    }

    @Test fun bulletsParagraphsAndGaps() {
        val l = ChatFormat.parse("Başlık\n\n- **Protein** 30 g\n* ikinci\n• üçüncü\n\n\nSon")
        assertTrue(l[0] is ChatFormat.Line.Para)
        assertTrue(l[1] is ChatFormat.Line.Gap)
        assertEquals(listOf(true, true, true), l.slice(2..4).map { it is ChatFormat.Line.Bullet })
        assertEquals("Protein", (l[2] as ChatFormat.Line.Bullet).spans[0].text)
        assertTrue((l[2] as ChatFormat.Line.Bullet).spans[0].bold)
        assertTrue(l[5] is ChatFormat.Line.Gap)
        assertEquals(7, l.size)
    }

    @Test fun boldMarkerIsNotABullet() {
        val l = ChatFormat.parse("**Kalın** satır")
        assertTrue(l.single() is ChatFormat.Line.Para)
    }

    @Test fun emptyAndTurkishChars() {
        assertTrue(ChatFormat.parse("").isEmpty())
        assertEquals("çğıöşüİ", (ChatFormat.parse("çğıöşüİ").single() as ChatFormat.Line.Para).spans.single().text)
    }
}

class ChatItemsTest {
    private fun action(id: Long, type: String, p: ActionPayload, status: PendingStatus = PendingStatus.PENDING, ref: String? = null) =
        PendingActionEntity(id, 1L, type, ToolValidator.encode(p), status, ref)

    private val water = ActionPayload.AddWater(TODAY.toEpochDay(), 500)

    @Test fun contextAndToolBlocksAreHidden() {
        val msgs = listOf(
            ChatMessage(Role.USER, listOf(Block.Context("CTX"), Block.Text("Merhaba")), id = 1),
            ChatMessage(Role.ASSISTANT, listOf(Block.Text("Selam"), Block.ToolUse("t", "add_water", "{}")), id = 2),
            ChatMessage(Role.USER, listOf(Block.ToolResult("t", "Öneri ... sunuldu", false, 7L)), id = 3),
            ChatMessage(Role.ASSISTANT, listOf(Block.Text("Ekledim mi? Onayla.")), id = 4),
        )
        val items = ChatItems.build(msgs, listOf(action(7, ToolDefs.ADD_WATER, water)))
        assertEquals(listOf("u1", "a2", "c7", "a4"), items.map { it.key })
        assertEquals("Merhaba", (items[0] as ChatItem.User).text)
    }

    @Test fun imageOnlyUserMessageShows() {
        val items = ChatItems.build(listOf(ChatMessage(Role.USER, listOf(Block.Context("c"), Block.Image("/p.jpg", "image/jpeg")), id = 1)), emptyList())
        assertEquals(listOf("/p.jpg"), (items.single() as ChatItem.User).imagePaths)
    }

    @Test fun autoAddedIsDetectedOnlyWhenConfirmedAndResultSaysAdded() {
        val msgs = { content: String -> listOf(ChatMessage(Role.USER, listOf(Block.ToolResult("t", content, false, 7L)), id = 3)) }
        val a = ChatItems.build(msgs("Eklendi: Su"), listOf(action(7, ToolDefs.ADD_WATER, water, PendingStatus.CONFIRMED, "water:1")))
        assertTrue((a.single() as ChatItem.Card).card.auto)
        val manual = ChatItems.build(msgs("Öneri kullanıcı onayına sunuldu"), listOf(action(7, ToolDefs.ADD_WATER, water, PendingStatus.CONFIRMED, "water:1")))
        assertFalse((manual.single() as ChatItem.Card).card.auto)
        // Otomatik ekleme basarisiz olduysa kart bekliyor kalir; 'Eklendi:' on-eki olsa bile otomatik sayilmaz.
        val pending = ChatItems.build(msgs("Eklendi: Su"), listOf(action(7, ToolDefs.ADD_WATER, water)))
        assertFalse((pending.single() as ChatItem.Card).card.auto)
    }

    @Test fun editedPayloadWinsOverOriginalForConfirmed() {
        val orig = ActionPayload.AddMeal(TODAY.toEpochDay(), MealType.OGLE, "Tavuk", 30.0, 500, null, null)
        val edited = orig.copy(proteinG = 45.0)
        val msgs = listOf(ChatMessage(Role.USER, listOf(Block.ToolResult("t", "x", false, 7L)), id = 3))
        val card = (ChatItems.build(msgs, listOf(action(7, ToolDefs.ADD_MEAL, orig, PendingStatus.CONFIRMED, ActionRef.withEdit("meal:1", edited)))).single() as ChatItem.Card).card
        assertEquals(45.0, (card.payload as ActionPayload.AddMeal).proteinG, 0.0)
        assertTrue(card.edited)
        val pend = (ChatItems.build(msgs, listOf(action(7, ToolDefs.ADD_MEAL, orig))).single() as ChatItem.Card).card
        assertEquals(30.0, (pend.payload as ActionPayload.AddMeal).proteinG, 0.0)
    }

    @Test fun missingActionAndDuplicateResultsProduceNoCard() {
        val msgs = listOf(
            ChatMessage(Role.USER, listOf(Block.ToolResult("t", "x", false, 99L)), id = 3),
            ChatMessage(Role.USER, listOf(Block.ToolResult("t2", "x", false, 7L)), id = 4),
            ChatMessage(Role.USER, listOf(Block.ToolResult("t3", "x", false, 7L)), id = 5),
        )
        assertEquals(listOf("c7"), ChatItems.build(msgs, listOf(action(7, ToolDefs.ADD_WATER, water))).map { it.key })
    }

    @Test fun retryRowOnlyWhenLastIsUserAndNotSending() {
        val u = ChatMessage(Role.USER, listOf(Block.Text("a")), id = 1)
        val a = ChatMessage(Role.ASSISTANT, listOf(Block.Text("b")), id = 2)
        assertTrue(ChatItems.withRetry(emptyList(), listOf(u), false).single() is ChatItem.Retry)
        assertTrue(ChatItems.withRetry(emptyList(), listOf(u), true).isEmpty())
        assertTrue(ChatItems.withRetry(emptyList(), listOf(u, a), false).isEmpty())
        assertTrue(ChatItems.withRetry(emptyList(), emptyList(), false).isEmpty())
        val tr = ChatMessage(Role.USER, listOf(Block.ToolResult("t", "x")), id = 3)
        assertTrue((ChatItems.withRetry(emptyList(), listOf(u, a, tr), false).single() as ChatItem.Retry).toolStep)
    }
}

class ComposerRulesTest {
    @Test fun sendRules() {
        assertTrue(ComposerRules.canSend("a", 0, false, false, true))
        assertTrue(ComposerRules.canSend("", 1, false, false, true))
        assertFalse(ComposerRules.canSend("  ", 0, false, false, true))
        assertFalse(ComposerRules.canSend("a", 0, true, false, true))
        assertFalse(ComposerRules.canSend("a", 0, false, true, true))
        assertFalse(ComposerRules.canSend("a", 0, false, false, false))
        assertFalse(ComposerRules.canSend("x".repeat(4001), 0, false, false, true))
    }

    @Test fun attachRules() {
        assertTrue(ComposerRules.canAttach(true, 0, false, false, true))
        assertFalse(ComposerRules.canAttach(false, 0, false, false, true))
        assertFalse(ComposerRules.canAttach(true, 3, false, false, true))
        assertFalse(ComposerRules.canAttach(true, 0, true, false, true))
        assertFalse(ComposerRules.canAttach(true, 0, false, true, true))
        assertFalse(ComposerRules.canAttach(true, 0, false, false, false))
        assertEquals(1, ComposerRules.remainingSlots(2))
        assertEquals(0, ComposerRules.remainingSlots(5))
    }

    @Test fun imageSupportFollowsProviderAndOpenAiCheckbox() {
        fun cfg(p: ProviderId, img: Boolean) = AssistantConfig(p, "m", "medium", "https://x", img, true, false, null)
        assertTrue(ComposerRules.supportsImages(cfg(ProviderId.ANTHROPIC, false)))
        assertTrue(ComposerRules.supportsImages(cfg(ProviderId.GEMINI, false)))
        assertFalse(ComposerRules.supportsImages(cfg(ProviderId.OPENAI_COMPAT, false)))
        assertTrue(ComposerRules.supportsImages(cfg(ProviderId.OPENAI_COMPAT, true)))
    }
}

class ErrorBandsTest {
    private fun e(k: AssistantError.Kind) = AssistantError(k, "m")

    @Test fun settingsKindsOfferSettingsAndNoRetry() {
        listOf(AssistantError.Kind.NO_KEY, AssistantError.Kind.UNAUTHORIZED, AssistantError.Kind.BAD_CONFIG).forEach {
            val b = ErrorBands.of(e(it), canResume = true, reopensAt = null)
            assertTrue(it.name, b.toSettings); assertFalse(it.name, b.canRetry)
        }
    }

    @Test fun transientKindsRetryOnlyWhenResumable() {
        listOf(AssistantError.Kind.NETWORK, AssistantError.Kind.TIMEOUT, AssistantError.Kind.SERVER, AssistantError.Kind.RATE_LIMITED).forEach {
            assertTrue(ErrorBands.of(e(it), true, null).canRetry)
            assertFalse(ErrorBands.of(e(it), false, null).canRetry)
            assertFalse(ErrorBands.of(e(it), true, null).toSettings)
        }
    }

    @Test fun dailyLimitShowsReopenTimeAndNoRetry() {
        val b = ErrorBands.of(e(AssistantError.Kind.DAILY_LIMIT), true, 123L)
        assertEquals(123L, b.reopensAt); assertFalse(b.canRetry)
        assertNull(ErrorBands.of(e(AssistantError.Kind.NETWORK), true, 123L).reopensAt)
    }

    @Test fun reopenTextTodayTomorrowLater() {
        val z = ZoneId.of("Europe/Istanbul")
        fun ms(y: Int, m: Int, d: Int, h: Int, min: Int) = java.time.ZonedDateTime.of(y, m, d, h, min, 0, 0, z).toInstant().toEpochMilli()
        val now = ms(2026, 11, 27, 10, 0)
        assertEquals("bugün 14:30", ErrorBands.reopenText(ms(2026, 11, 27, 14, 30), now, z))
        assertEquals("yarın 02:05", ErrorBands.reopenText(ms(2026, 11, 28, 2, 5), now, z))
        assertEquals("30.11 09:00", ErrorBands.reopenText(ms(2026, 11, 30, 9, 0), now, z))
    }
}

class CardEditTest {
    private val d = TODAY.toEpochDay()
    private val meal = ActionPayload.AddMeal(d, MealType.AKSAM, "Tavuk", 30.0, 500, 40.0, null)

    private fun ok(p: ActionPayload, f: EditForm) = (CardEdit.toPayload(p, f, TODAY) as CardEdit.Result.Ok).payload
    private fun bad(p: ActionPayload, f: EditForm) = CardEdit.toPayload(p, f, TODAY) as CardEdit.Result.Invalid

    @Test fun formRoundTrip() {
        assertEquals(meal, ok(meal, CardEdit.formOf(meal)))
        val w = ActionPayload.AddWater(d, 250)
        assertEquals(w, ok(w, CardEdit.formOf(w)))
        val a = ActionPayload.LogActivity(d, ActivityType.YUZME, 30, 800, "havuz")
        assertEquals(a, ok(a, CardEdit.formOf(a)))
        val k = ActionPayload.LogWeight(d, 80.5)
        assertEquals(k, ok(k, CardEdit.formOf(k)))
    }

    @Test fun mealLimits() {
        val f = CardEdit.formOf(meal)
        assertEquals(setOf("protein"), bad(meal, f.copy(protein = "300,1")).fields)
        assertEquals(300.0, (ok(meal, f.copy(protein = "300")) as ActionPayload.AddMeal).proteinG, 0.0)
        assertEquals(setOf("calories"), bad(meal, f.copy(calories = "5001")).fields)
        assertEquals(setOf("description"), bad(meal, f.copy(description = " ")).fields)
        assertEquals(setOf("description"), bad(meal, f.copy(description = "x".repeat(81))).fields)
        assertEquals(setOf("carbs"), bad(meal, f.copy(carbs = "1001")).fields)
        assertEquals(setOf("fat"), bad(meal, f.copy(fat = "500,5")).fields)
        assertEquals(setOf("protein", "calories"), bad(meal, f.copy(protein = "abc", calories = "-1")).fields)
        // Virgul ondalik ayirici kabul; bos karb/yag = yok.
        val p = ok(meal, f.copy(protein = "32,5", carbs = "", fat = "")) as ActionPayload.AddMeal
        assertEquals(32.5, p.proteinG, 0.0); assertNull(p.carbsG); assertNull(p.fatG)
    }

    @Test fun waterWeightActivityLimits() {
        val w = ActionPayload.AddWater(d, 250)
        assertEquals(setOf("ml"), bad(w, EditForm(ml = "5001")).fields)
        assertEquals(setOf("ml"), bad(w, EditForm(ml = "0")).fields)
        assertEquals(5000, (ok(w, EditForm(ml = "5000")) as ActionPayload.AddWater).ml)
        val k = ActionPayload.LogWeight(d, 80.0)
        assertEquals(setOf("kg"), bad(k, EditForm(kg = "501")).fields)
        val sauna = ActionPayload.LogActivity(d, ActivityType.SAUNA, 20, null, "")
        assertEquals(setOf("distance"), bad(sauna, EditForm(minutes = "20", distance = "100")).fields)
        assertEquals(setOf("minutes"), bad(sauna, EditForm(minutes = (ToolDefs.MAX_MINUTES + 1).toString())).fields)
        assertEquals(setOf("note"), bad(sauna, EditForm(minutes = "20", note = "x".repeat(81))).fields)
    }

    @Test fun invalidCarriesTurkishMessage() {
        assertTrue(bad(meal, CardEdit.formOf(meal).copy(protein = "999")).message.contains("Protein"))
    }

    @Test fun futureDayIsRejectedByFinalGate() {
        val future = meal.copy(epochDay = TODAY.plusDays(1).toEpochDay())
        assertTrue(CardEdit.toPayload(future, CardEdit.formOf(future), TODAY) is CardEdit.Result.Invalid)
    }
}

class AssistantSettingsRulesTest {
    @Test fun limitParse() {
        assertEquals(AssistantSettingsRules.LimitParse.Unlimited, AssistantSettingsRules.parseLimit(""))
        assertEquals(AssistantSettingsRules.LimitParse.Unlimited, AssistantSettingsRules.parseLimit("  "))
        assertEquals(AssistantSettingsRules.LimitParse.Invalid, AssistantSettingsRules.parseLimit("1"))
        assertEquals(AssistantSettingsRules.LimitParse.Value(2), AssistantSettingsRules.parseLimit("2"))
        assertEquals(AssistantSettingsRules.LimitParse.Value(5000), AssistantSettingsRules.parseLimit("5000"))
        assertEquals(AssistantSettingsRules.LimitParse.Invalid, AssistantSettingsRules.parseLimit("5001"))
        assertEquals(AssistantSettingsRules.LimitParse.Invalid, AssistantSettingsRules.parseLimit("abc"))
        assertEquals(AssistantSettingsRules.LimitParse.Invalid, AssistantSettingsRules.parseLimit("-5"))
        assertEquals(AssistantSettingsRules.LimitParse.Invalid, AssistantSettingsRules.parseLimit("0"))
    }

    @Test fun urlRulesReleaseVersusDebug() {
        val local = listOf("http://localhost:8080/v1", "http://127.0.0.1:9000/v1", "http://10.0.2.2:8000/v1")
        local.forEach {
            assertTrue("release: $it", AssistantSettingsRules.baseUrlError(it, debuggable = false) != null)
            assertNull("debug: $it", AssistantSettingsRules.baseUrlError(it, debuggable = true))
        }
        assertNull(AssistantSettingsRules.baseUrlError("https://openrouter.ai/api/v1", false))
        assertNull(AssistantSettingsRules.baseUrlError("https://openrouter.ai/api/v1", true))
        // Debug'ta bile yalniz bu uc ana makine adi; yakisikli adlar ve baska http reddedilir.
        listOf("http://localhost.evil.com/v1", "http://127.0.0.1.evil.com", "http://evil.com", "http://192.168.1.5:8000", "ftp://localhost", "http://user@localhost/")
            .forEach { assertTrue(it, AssistantSettingsRules.baseUrlError(it, true) != null) }
    }

    @Test fun claudeTiers() {
        assertEquals(AssistantSettingsRules.ClaudeTier.OPUS, AssistantSettingsRules.claudeTier("claude-opus-5-5"))
        assertEquals(AssistantSettingsRules.ClaudeTier.SONNET, AssistantSettingsRules.claudeTier("claude-sonnet-5-5"))
        assertEquals(AssistantSettingsRules.ClaudeTier.HAIKU, AssistantSettingsRules.claudeTier("claude-haiku-5-5"))
        assertNull(AssistantSettingsRules.claudeTier("gpt-x"))
    }
}
