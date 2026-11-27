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

/** Arayuz modelinin ihtiyac duydugu arka uc yuzeyi (Android'siz birim test icin ince arayuz). */
interface ChatBackend {
    val messages: kotlinx.coroutines.flow.Flow<List<com.emir.gym.data.ChatMessageEntity>>
    val actions: kotlinx.coroutines.flow.Flow<List<PendingActionEntity>>

    /** Gonderim ya da baglanti testi suruyor. */
    val busy: Boolean
    suspend fun sweepOrphanImages()
    suspend fun prepare(handle: Any): PreparedImage
    fun discard(list: List<PreparedImage>)
    fun imageExists(path: String): Boolean
    fun sendPrepared(text: String, prepared: List<PreparedImage>, onDone: (com.emir.gym.assistant.SendResult) -> Unit)
    fun resume()
    fun cancel()

    /** @return false: mesgul, temizlenmedi. */
    fun clearChat(): Boolean
    fun confirm(id: Long, onResult: (ConfirmResult) -> Unit)
    fun confirmEdited(id: Long, edited: ActionPayload, onResult: (ConfirmResult) -> Unit)
    fun reject(id: Long, onResult: (Boolean) -> Unit)
    fun hasKey(p: ProviderId): Boolean
    fun setKey(p: ProviderId, key: String): Boolean
    fun clearKey(p: ProviderId)
    suspend fun testConnection(): com.emir.gym.assistant.AssistantError?
}

/** Gercek denetleyiciye baglanti. */
class ControllerBackend(val ctl: AssistantController) : ChatBackend {
    override val messages get() = ctl.messages
    override val actions get() = ctl.actions
    override val busy get() = ctl.sending.value || ctl.testing.value
    override suspend fun sweepOrphanImages() = ctl.sweepOrphanImages()
    override suspend fun prepare(handle: Any) = ctl.prepare(handle as android.net.Uri)
    override fun discard(list: List<PreparedImage>) = ctl.discard(list)
    override fun imageExists(path: String) = ctl.imageExists(path)
    override fun sendPrepared(text: String, prepared: List<PreparedImage>, onDone: (com.emir.gym.assistant.SendResult) -> Unit) =
        ctl.sendPrepared(text, prepared, onDone)
    override fun resume() = ctl.resume()
    override fun cancel() = ctl.cancel()
    override fun clearChat() = ctl.clearChatNow()
    override fun confirm(id: Long, onResult: (ConfirmResult) -> Unit) = ctl.confirm(id, onResult)
    override fun confirmEdited(id: Long, edited: ActionPayload, onResult: (ConfirmResult) -> Unit) = ctl.confirmEdited(id, edited, onResult)
    override fun reject(id: Long, onResult: (Boolean) -> Unit) = ctl.reject(id, onResult)
    override fun hasKey(p: ProviderId) = ctl.keys.has(p)
    override fun setKey(p: ProviderId, key: String) = ctl.keys.set(p, key)
    override fun clearKey(p: ProviderId) = ctl.keys.clear(p)
    override suspend fun testConnection() = ctl.testConnection()
}

/** Secilen bir gorsel: [handle] (Uri), [tempFile] (kamera gecici dosyasi; isi bitince silinir). */
class PickedImage(val handle: Any, val tempFile: java.io.File? = null)

/**
 * Sohbet ekraninin durumu; ViewModel'de yasar (ekran dondurmede korunur). Sohbet gecmisi Room'dandir
 * (surec olumunde geri gelir); taslak metin ve hazirlanmis gorseller yalniz surec boyunca tutulur.
 */
class AssistantUiModel(
    private val backend: ChatBackend,
    private val strings: (Int, Array<out Any>) -> String,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    constructor(app: Application, ctl: AssistantController, scope: CoroutineScope) :
        this(ControllerBackend(ctl), { id, a -> app.getString(id, *a) }, scope, { android.os.SystemClock.elapsedRealtime() })

    /** Ekranlarin (ayarlar, onizleme) kullandigi gercek denetleyici. */
    val ctl: AssistantController get() = (backend as ControllerBackend).ctl

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
    private val tempInUse = HashSet<String>()

    /** Hazirlanmakta olan kamera gecici dosyasi mi (temizlik silmemeli). */
    fun isTempInUse(path: String): Boolean = path in tempInUse

    /** null = ilk veri henuz gelmedi. */
    val snapshot: StateFlow<ChatSnapshot?> = combine(backend.messages, backend.actions) { m, a ->
        ChatSnapshot(m.map { RoomChatStore.toMessage(it) }, a)
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        // Surec yeni acildi: hicbir mesaja bagli olmayan (onceki surecten kalan) gorseller silinir.
        scope.launch { backend.sweepOrphanImages(); ready.complete(Unit) }
    }

    private fun say(text: String, actionLabel: String? = null, goTab: Int? = null) {
        events.tryEmit(UiEvent(text, actionLabel, goTab))
    }

    private fun str(id: Int, vararg a: Any) = strings(id, a)

    fun bump() { version++ }

    fun msg(id: Int): String = str(id)

    fun hasKey(p: ProviderId): Boolean = backend.hasKey(p)

    // ---- Gorseller ----

    fun addImages(uris: List<android.net.Uri>, deleteSource: Boolean = false) =
        addPicked(uris.map { PickedImage(it, if (deleteSource) tempOf(it) else null) })

    private fun tempOf(u: android.net.Uri): java.io.File? = u.path?.let { java.io.File(it) }

    /** Secilen gorselleri sirayla hazirlar; kalan hakkin otesi atlanir. Basarisiz olan icin snackbar. */
    fun addPicked(picked: List<PickedImage>) {
        if (picked.isEmpty()) return
        picked.forEach { it.tempFile?.let { f -> tempInUse += f.path } }
        scope.launch {
            ready.await()
            var skipped = false
            for (p in picked) {
                if (ComposerRules.remainingSlots(attached.size + preparing) <= 0) {
                    skipped = true
                    p.tempFile?.let { tempInUse -= it.path; it.delete() }
                    continue
                }
                preparing++
                try {
                    val img = backend.prepare(p.handle)
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
                    p.tempFile?.let { tempInUse -= it.path; it.delete() }
                }
            }
            if (skipped) say(str(R.string.asst_image_limit, ComposerRules.MAX_IMAGES))
        }
    }

    fun removeImage(img: PreparedImage) {
        attached = attached.filterNot { it.path == img.path }
        backend.discard(listOf(img))
    }

    /** Ekrandan cikis: gonderilmeyen gorseller silinir. */
    fun discardAttached() {
        val l = attached
        attached = emptyList()
        backend.discard(l)
    }

    // ---- Gonder / iptal / tekrar ----

    fun send(configured: Boolean) {
        val text = draft.trim()
        if (!ComposerRules.canSend(text, attached.size, backend.busy, preparing > 0, configured)) return
        if (!gate.tryPass(clock())) return
        // Diskte olmayan gorsel (ornegin sohbet temizlendi) sessizce gorselsiz gonderilmez: kaldirilir, gonderim durur.
        val missing = attached.filter { !backend.imageExists(it.path) }
        if (missing.isNotEmpty()) {
            attached = attached.filterNot { a -> missing.any { it.path == a.path } }
            backend.discard(missing)
            say(str(R.string.asst_image_gone))
            return
        }
        val imgs = attached
        draft = ""
        attached = emptyList()
        backend.sendPrepared(text, imgs) { r ->
            // Mesaj hic kaydedilmediyse (anahtar yok, sinir, ag oncesi hata) yazilan metin kaybolmasin.
            if (!r.stored && !r.cancelled && r.error != null && draft.isBlank()) draft = text
        }
    }

    fun retry() {
        if (backend.busy) return
        if (!gate.tryPass(clock())) return
        backend.resume()
    }

    fun cancel() = backend.cancel()

    /** Sohbeti temizler; composer'daki bekleyen gorseller de (dosyalari silindigi icin) birakilir. */
    fun clearChat() {
        if (!backend.clearChat()) return
        val l = attached
        attached = emptyList()
        backend.discard(l)
    }

    // ---- Oneri kartlari ----

    internal fun report(r: ConfirmResult, payload: ActionPayload?) {
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

    /** Cift dokunus: ayni kart icin islem surerken ikinci cagri atlanir (arka uc de idempotent). */
    fun confirm(card: ActionCard) {
        if (!busyCards.add(card.id)) return
        backend.confirm(card.id) { r -> busyCards.remove(card.id); report(r, card.payload) }
    }

    fun confirmEdited(card: ActionCard, edited: ActionPayload) {
        if (!busyCards.add(card.id)) return
        backend.confirmEdited(card.id, edited) { r -> busyCards.remove(card.id); report(r, edited) }
    }

    fun reject(card: ActionCard) {
        if (!busyCards.add(card.id)) return
        backend.reject(card.id) { ok ->
            busyCards.remove(card.id)
            say(str(if (ok) R.string.asst_rejected_toast else R.string.asst_already_done))
        }
    }

    // ---- Ayarlar ----

    fun saveKey(p: ProviderId, key: String): Boolean {
        val ok = backend.setKey(p, key)
        say(str(if (ok) R.string.asst_key_saved else R.string.asst_key_save_failed))
        bump()
        return ok
    }

    fun deleteKey(p: ProviderId) {
        backend.clearKey(p)
        val gone = !backend.hasKey(p)
        say(str(if (gone) R.string.asst_key_deleted else R.string.asst_key_delete_failed))
        bump()
    }

    fun testConnection() {
        if (testState == TestState.Running) return
        testState = TestState.Running
        scope.launch {
            val e = backend.testConnection()
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
