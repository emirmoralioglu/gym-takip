package com.emir.gym.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.emir.gym.R
import com.emir.gym.assistant.ActionPayload
import com.emir.gym.assistant.AssistantController
import com.emir.gym.assistant.ChatMessage
import com.emir.gym.assistant.ConfirmResult
import com.emir.gym.assistant.ImageException
import com.emir.gym.assistant.PreparedImage
import com.emir.gym.assistant.ProviderId
import com.emir.gym.assistant.RoomChatStore
import com.emir.gym.data.PendingActionEntity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Snackbar mesaji; [goTab] verilirse eylem dugmesi o sekmeye gecer. */
data class UiEvent(val text: String, val actionLabel: String? = null, val goTab: Int? = null)

data class ChatSnapshot(val messages: List<ChatMessage>, val actions: List<PendingActionEntity>)

sealed interface TestState {
    object Idle : TestState
    object Running : TestState
    data class Done(val ok: Boolean, val message: String) : TestState
}

/**
 * Sohbet ekraninin durumu; ViewModel'de yasar (ekran dondurmede korunur). Sohbet gecmisi Room'dandir
 * (surec olumunde geri gelir); taslak metin ve hazirlanmis gorseller yalniz surec boyunca tutulur.
 */
class AssistantUiModel(private val app: Application, val ctl: AssistantController, private val scope: CoroutineScope) {
    var draft by mutableStateOf("")
    var attached by mutableStateOf<List<PreparedImage>>(emptyList())
        private set
    var preparing by mutableIntStateOf(0)
        private set

    /** Ayar/anahtar degisince artar; anahtar durumu ve kalan hak yeniden okunur. */
    var version by mutableIntStateOf(0)
        private set

    var testState by mutableStateOf<TestState>(TestState.Idle)
        private set

    val events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)

    private val ready = CompletableDeferred<Unit>()
    private val gate = ClickGate()
    private val busyCards = HashSet<Long>()

    /** null = ilk veri henuz gelmedi. */
    val snapshot: StateFlow<ChatSnapshot?> = combine(ctl.messages, ctl.actions) { m, a ->
        ChatSnapshot(m.map { RoomChatStore.toMessage(it) }, a)
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Surec yeni acildi: hicbir mesaja bagli olmayan (onceki surecten kalan) gorseller silinir.
        scope.launch { ctl.sweepOrphanImages(); ready.complete(Unit) }
    }

    private fun say(text: String, actionLabel: String? = null, goTab: Int? = null) {
        events.tryEmit(UiEvent(text, actionLabel, goTab))
    }

    private fun str(id: Int, vararg a: Any) = app.getString(id, *a)

    fun bump() { version++ }

    fun msg(id: Int): String = app.getString(id)

    fun hasKey(p: ProviderId): Boolean = ctl.keys.has(p)

    // ---- Gorseller ----

    /** Secilen gorselleri sirayla hazirlar; kalan hakkin otesi atlanir. Basarisiz olan icin snackbar. */
    fun addImages(uris: List<android.net.Uri>, deleteSource: Boolean = false) {
        if (uris.isEmpty()) return
        scope.launch {
            ready.await()
            var skipped = false
            for (uri in uris) {
                if (ComposerRules.remainingSlots(attached.size + preparing) <= 0) {
                    skipped = true
                    if (deleteSource) uri.path?.let { java.io.File(it).delete() }
                    continue
                }
                preparing++
                try {
                    val img = ctl.prepare(uri)
                    attached = attached + img
                } catch (e: ImageException) {
                    say(e.message ?: str(R.string.asst_image_failed))
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Exception) {
                    say(str(R.string.asst_image_failed))
                } finally {
                    preparing--
                    // Kamera gecici dosyasi: hazirlandiktan (ya da basarisiz olduktan) sonra silinir.
                    if (deleteSource) uri.path?.let { java.io.File(it).delete() }
                }
            }
            if (skipped) say(str(R.string.asst_image_limit, ComposerRules.MAX_IMAGES))
        }
    }

    fun removeImage(img: PreparedImage) {
        attached = attached.filterNot { it.path == img.path }
        ctl.discard(listOf(img))
    }

    /** Ekrandan cikis: gonderilmeyen gorseller silinir. */
    fun discardAttached() {
        val l = attached
        attached = emptyList()
        ctl.discard(l)
    }

    // ---- Gonder / iptal / tekrar ----

    fun send(configured: Boolean) {
        val text = draft.trim()
        if (!ComposerRules.canSend(text, attached.size, ctl.sending.value, preparing > 0, configured)) return
        if (!gate.tryPass(android.os.SystemClock.elapsedRealtime())) return
        val imgs = attached
        draft = ""
        attached = emptyList()
        ctl.sendPrepared(text, imgs) { r ->
            // Mesaj hic kaydedilmediyse (anahtar yok, sinir, ag oncesi hata) yazilan metin kaybolmasin.
            if (!r.stored && !r.cancelled && r.error != null && draft.isBlank()) draft = text
        }
    }

    fun retry() {
        if (ctl.sending.value) return
        if (!gate.tryPass(android.os.SystemClock.elapsedRealtime())) return
        ctl.resume()
    }

    fun cancel() = ctl.cancel()

    fun clearChat() = ctl.clearChat()

    // ---- Oneri kartlari ----

    private fun report(r: ConfirmResult, payload: ActionPayload?) {
        when (r) {
            is ConfirmResult.Confirmed -> {
                val tab = payload?.let { ActionTargets.tabFor(it) }
                say(str(R.string.asst_added_toast, r.summary), payload?.let { str(ActionTargets.labelRes(it)) }, tab)
            }
            is ConfirmResult.AlreadyConfirmed -> say(str(R.string.asst_already_confirmed))
            is ConfirmResult.AlreadyRejected -> say(str(R.string.asst_already_rejected))
            ConfirmResult.NotFound -> say(str(R.string.asst_action_missing))
            is ConfirmResult.Failed -> say(str(R.string.asst_confirm_failed, r.message))
        }
    }

    /** Cift dokunus: ayni kart icin islem surerken ve kisa aralikta ikinci cagri atlanir (arka uc de idempotent). */
    fun confirm(card: ActionCard) {
        if (!busyCards.add(card.id)) return
        ctl.confirm(card.id) { r -> busyCards.remove(card.id); report(r, card.payload) }
    }

    fun confirmEdited(card: ActionCard, edited: ActionPayload) {
        if (!busyCards.add(card.id)) return
        ctl.confirmEdited(card.id, edited) { r -> busyCards.remove(card.id); report(r, edited) }
    }

    fun reject(card: ActionCard) {
        if (!busyCards.add(card.id)) return
        ctl.reject(card.id) { ok ->
            busyCards.remove(card.id)
            say(str(if (ok) R.string.asst_rejected_toast else R.string.asst_already_done))
        }
    }

    // ---- Ayarlar ----

    fun saveKey(p: ProviderId, key: String): Boolean {
        val ok = ctl.keys.set(p, key)
        say(str(if (ok) R.string.asst_key_saved else R.string.asst_key_save_failed))
        bump()
        return ok
    }

    fun deleteKey(p: ProviderId) {
        ctl.keys.clear(p)
        val gone = !ctl.keys.has(p)
        say(str(if (gone) R.string.asst_key_deleted else R.string.asst_key_delete_failed))
        bump()
    }

    fun testConnection() {
        if (testState == TestState.Running) return
        testState = TestState.Running
        scope.launch {
            val e = ctl.testConnection()
            testState = if (e == null) TestState.Done(true, str(R.string.asst_test_ok)) else TestState.Done(false, e.message)
            bump()
        }
    }

    fun clearTest() { testState = TestState.Idle }
}

/** Kart eklenince hangi sekmeye kisayol verilir. */
object ActionTargets {
    fun tabFor(p: ActionPayload): Int = when (p) {
        is ActionPayload.AddMeal, is ActionPayload.AddWater -> 3
        is ActionPayload.LogWeight -> 4
        is ActionPayload.LogActivity -> 0
    }

    fun labelRes(p: ActionPayload): Int = when (p) {
        is ActionPayload.AddMeal, is ActionPayload.AddWater -> R.string.asst_see_nutrition
        is ActionPayload.LogWeight -> R.string.asst_see_settings
        is ActionPayload.LogActivity -> R.string.asst_see_workout
    }
}
