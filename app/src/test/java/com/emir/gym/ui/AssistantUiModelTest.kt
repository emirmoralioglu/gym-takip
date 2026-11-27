package com.emir.gym.ui

import com.emir.gym.R
import com.emir.gym.assistant.ActionPayload
import com.emir.gym.assistant.AssistantError
import com.emir.gym.assistant.ConfirmResult
import com.emir.gym.assistant.ImageException
import com.emir.gym.assistant.PreparedImage
import com.emir.gym.assistant.ProviderId
import com.emir.gym.assistant.SendResult
import com.emir.gym.data.ChatMessageEntity
import com.emir.gym.data.MealType
import com.emir.gym.data.PendingActionEntity
import com.emir.gym.data.PendingStatus
import java.time.LocalDate
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun img(name: String) = PreparedImage("/chat/$name.jpg", "/chat/${name}_thumb.jpg", "image/jpeg", 10, 10, 100)

private class FakeBackend : ChatBackend {
    override val messages: Flow<List<ChatMessageEntity>> = flowOf(emptyList())
    override val actions: Flow<List<PendingActionEntity>> = flowOf(emptyList())
    override var busy = false
    var clearOk = true
    val discarded = ArrayList<List<PreparedImage>>()
    val existing = HashSet<String>()
    val sent = ArrayList<Pair<String, List<PreparedImage>>>()
    var sendResult: SendResult? = null
    var prepareGate: CompletableDeferred<Unit>? = null
    var prepareFails: Exception? = null
    var confirmResult: ConfirmResult = ConfirmResult.NotFound
    var rejectResult = true
    var resumed = 0
    private var n = 0

    override suspend fun sweepOrphanImages() {}
    override suspend fun prepare(handle: Any): PreparedImage {
        prepareGate?.await()
        prepareFails?.let { throw it }
        return img("p${n++}").also { existing += it.path }
    }
    override fun discard(list: List<PreparedImage>) { discarded += list }
    override fun imageExists(path: String) = path in existing
    override fun sendPrepared(text: String, prepared: List<PreparedImage>, onDone: (SendResult) -> Unit) {
        sent += text to prepared
        sendResult?.let(onDone)
    }
    override fun resume() { resumed++ }
    override fun cancel() {}
    override fun clearChat(): Boolean = clearOk
    override fun confirm(id: Long, onResult: (ConfirmResult) -> Unit) = onResult(confirmResult)
    override fun confirmEdited(id: Long, edited: ActionPayload, onResult: (ConfirmResult) -> Unit) = onResult(confirmResult)
    override fun reject(id: Long, onResult: (Boolean) -> Unit) = onResult(rejectResult)
    override fun hasKey(p: ProviderId) = true
    override fun setKey(p: ProviderId, key: String) = true
    override fun clearKey(p: ProviderId) {}
    override suspend fun testConnection(): AssistantError? = null
}

class AssistantUiModelTest {
    private val backend = FakeBackend()
    private var now = 0L
    private val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
    private val ui = AssistantUiModel(backend, { id, a -> "$id|${a.joinToString(",")}" }, scope, { now })
    private val events = ArrayList<UiEvent>()

    init { scope.launch { ui.events.collect { events += it } } }

    private fun attachOne(): PreparedImage {
        ui.addPicked(listOf(PickedImage("u")))
        return ui.attached.last()
    }

    private val card = ActionCard(
        7, ActionPayload.AddMeal(LocalDate.now().toEpochDay(), MealType.OGLE, "Tavuk", 30.0, 500, null, null),
        PendingStatus.PENDING, auto = false, edited = false,
    )

    private fun text(id: Int, vararg a: Any) = "$id|${a.joinToString(",")}"

    // ---- Onay sonuclari kullaniciya bildirilir ----

    @Test fun everyConfirmResultIsReportedWithRightText() {
        fun run(r: ConfirmResult): UiEvent {
            events.clear()
            backend.confirmResult = r
            ui.confirm(card)
            assertEquals("tek olay beklenir: $r", 1, events.size)
            return events[0]
        }
        val ok = run(ConfirmResult.Confirmed(7, "Su: 500 ml", "water:1"))
        assertEquals(text(R.string.asst_added_toast, "Su: 500 ml"), ok.text)
        assertEquals(3, ok.goTab) // ogun -> Beslenme
        assertEquals(text(R.string.asst_already_confirmed), run(ConfirmResult.AlreadyConfirmed(7)).text)
        assertEquals(text(R.string.asst_already_rejected), run(ConfirmResult.AlreadyRejected(7)).text)
        assertEquals(text(R.string.asst_action_missing), run(ConfirmResult.NotFound).text)
        val failed = run(ConfirmResult.Failed("Kaydedilemedi."))
        assertEquals(text(R.string.asst_confirm_failed, "Kaydedilemedi."), failed.text)
        assertEquals(null, failed.goTab)
    }

    @Test fun editedConfirmFailureIsAlsoReported() {
        backend.confirmResult = ConfirmResult.Failed("Protein 0 ile 300 g arasında olmalı.")
        ui.confirmEdited(card, card.payload)
        assertEquals(text(R.string.asst_confirm_failed, "Protein 0 ile 300 g arasında olmalı."), events.single().text)
    }

    @Test fun rejectReportsBothOutcomes() {
        ui.reject(card)
        assertEquals(text(R.string.asst_rejected_toast), events.last().text)
        backend.rejectResult = false
        ui.reject(card)
        assertEquals(text(R.string.asst_already_done), events.last().text)
    }

    @Test fun tabShortcutPerType() {
        assertEquals(3, ActionTargets.tabFor(card.payload))
        assertEquals(3, ActionTargets.tabFor(ActionPayload.AddWater(1, 250)))
        assertEquals(4, ActionTargets.tabFor(ActionPayload.LogWeight(1, 80.0)))
        assertEquals(0, ActionTargets.tabFor(ActionPayload.LogActivity(1, com.emir.gym.data.ActivityType.SAUNA, 10, null, "")))
    }

    // ---- Gorseller ----

    @Test fun removeImageDiscardsFileAndDropsChip() {
        val a = attachOne(); val b = attachOne()
        ui.removeImage(a)
        assertEquals(listOf(b.path), ui.attached.map { it.path })
        assertEquals(listOf(listOf(a)), backend.discarded)
    }

    @Test fun leavingScreenDiscardsAttached() {
        val a = attachOne(); val b = attachOne()
        ui.discardAttached()
        assertTrue(ui.attached.isEmpty())
        assertEquals(listOf(a, b), backend.discarded.flatten())
    }

    @Test fun clearChatEmptiesComposerImages() {
        val a = attachOne()
        ui.clearChat()
        assertTrue(ui.attached.isEmpty())
        assertEquals(listOf(a), backend.discarded.flatten())
    }

    @Test fun clearChatWhileBusyKeepsComposerImages() {
        attachOne()
        backend.clearOk = false
        ui.clearChat()
        assertEquals(1, ui.attached.size)
        assertTrue(backend.discarded.isEmpty())
    }

    @Test fun imageLimitIsThreeAndPrepareFailureShowsSnack() {
        repeat(3) { attachOne() }
        ui.addPicked(listOf(PickedImage("x")))
        assertEquals(3, ui.attached.size)
        assertEquals(text(R.string.asst_image_limit, 3), events.last().text)
        ui.discardAttached()
        backend.prepareFails = ImageException("Yalnızca JPEG, PNG ve WebP görseller eklenebilir.")
        ui.addPicked(listOf(PickedImage("y")))
        assertTrue(ui.attached.isEmpty())
        assertEquals("Yalnızca JPEG, PNG ve WebP görseller eklenebilir.", events.last().text)
    }

    @Test fun cameraTempFileProtectedWhilePreparingThenDeleted() {
        val f = java.io.File.createTempFile("cam_", ".jpg")
        backend.prepareGate = CompletableDeferred()
        ui.addPicked(listOf(PickedImage("u", f)))
        assertTrue(ui.isTempInUse(f.path))
        assertTrue(f.exists())
        backend.prepareGate!!.complete(Unit)
        assertFalse(ui.isTempInUse(f.path))
        assertFalse(f.exists())
    }

    // ---- Gonder ----

    @Test fun sendWithMissingImageFileDoesNotSendAndSaysSo() {
        val a = attachOne()
        backend.existing.clear() // dosya diskten silindi
        ui.draft = "bu ne?"
        ui.send(configured = true)
        assertTrue(backend.sent.isEmpty())
        assertTrue(ui.attached.isEmpty())
        assertEquals("bu ne?", ui.draft)
        assertEquals(text(R.string.asst_image_gone), events.last().text)
        assertEquals(listOf(a), backend.discarded.flatten())
    }

    @Test fun sendPassesTextAndImagesAndClearsComposer() {
        val a = attachOne()
        ui.draft = "  merhaba  "
        ui.send(true)
        assertEquals(listOf("merhaba" to listOf(a)), backend.sent)
        assertEquals("", ui.draft)
        assertTrue(ui.attached.isEmpty())
    }

    @Test fun draftRestoredWhenNothingWasStored() {
        backend.sendResult = SendResult(stored = false, error = AssistantError(AssistantError.Kind.NO_KEY, "x"))
        ui.draft = "yarım kalan"
        ui.send(true)
        assertEquals("yarım kalan", ui.draft)
    }

    @Test fun draftNotRestoredWhenStoredOrCancelled() {
        backend.sendResult = SendResult(stored = true, error = AssistantError(AssistantError.Kind.NETWORK, "x"))
        ui.draft = "a"; ui.send(true)
        assertEquals("", ui.draft)
        now += 1000
        backend.sendResult = SendResult(stored = false, error = null, cancelled = true)
        ui.draft = "b"; ui.send(true)
        assertEquals("", ui.draft)
    }

    @Test fun busyBackendBlocksSendAndRetry() {
        backend.busy = true
        ui.draft = "x"
        ui.send(true)
        ui.retry()
        assertTrue(backend.sent.isEmpty())
        assertEquals(0, backend.resumed)
        backend.busy = false
        ui.retry()
        assertEquals(1, backend.resumed)
    }

    @Test fun doubleTapSendsOnce() {
        ui.draft = "a"; ui.send(true)
        ui.draft = "b"; ui.send(true) // 0 ms sonra: koruma
        assertEquals(1, backend.sent.size)
    }
}
