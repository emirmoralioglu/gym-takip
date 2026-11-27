package com.emir.gym.assistant

import android.app.Application
import android.content.pm.ApplicationInfo
import android.net.Uri
import com.emir.gym.data.AppDatabase
import com.emir.gym.data.NutritionGoals
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Bir gonderimin sonucu: [stored] = kullanici mesaji kaydedildi mi; [error] = hata (varsa); [cancelled] = kullanici iptal etti. */
data class SendResult(val stored: Boolean, val error: AssistantError?, val cancelled: Boolean = false)

/**
 * Arayuzun kullandigi tek giris noktasi. GymViewModel'den tembelce olusturulur;
 * asistan kullanilmadikca anahtar deposu / ag katmani hic baslatilmaz.
 */
class AssistantController(
    private val app: Application,
    private val scope: CoroutineScope,
    goals: () -> NutritionGoals,
) {
    private val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    val repository = AssistantRepository(AppDatabase.get(app), goals)
    val settings = AssistantSettings(app, allowLocalCleartext = debuggable)
    val keys = ApiKeyStore(app)
    val usage: UsageStore = PrefsUsageStore(app)
    val images = ImagePreparer(app)

    private fun makeProvider(cfg: AssistantConfig, key: String): LlmProvider = when (cfg.provider) {
        ProviderId.ANTHROPIC -> AnthropicProvider(key)
        ProviderId.GEMINI -> GeminiProvider(key)
        ProviderId.OPENAI_COMPAT -> OpenAiCompatProvider(key, cfg.baseUrl, cfg.supportsImages, cfg.supportsTools, debuggable)
    }

    private val tools = ToolExecutor(
        store = repository.actions,
        writer = TrackingRecordWriter(AppDatabase.get(app).trackingDao()),
        readData = { repository.readData() },
        autoAdd = { settings.config.value.autoAdd },
    )

    val engine = AssistantEngine(
        chat = repository.chat,
        tools = tools,
        usage = usage,
        keyOf = { keys.get(it) },
        config = { settings.config.value },
        providerFor = { cfg, key -> makeProvider(cfg, key) },
        contextText = { day, toolsOn -> AssistantContext.build(day, repository.readData(), toolsOn) },
        imageBase64 = { images.base64(it) },
        allowLocalCleartext = debuggable,
        discardImages = { list -> list.forEach { images.delete(it.path) } },
    )

    val sending = MutableStateFlow(false)

    /** Iptal istendi ama surmekte olan ag istegi (en cok 90 sn) henuz bitmedi. */
    val cancelling = MutableStateFlow(false)

    /** 'Baglantiyi test et' suruyor. */
    val testing = MutableStateFlow(false)

    /** Son turun hatasi (varsa); arayuz gosterip [clearError] ile temizler. */
    val error: MutableStateFlow<AssistantError?> = MutableStateFlow(null)
    val messages get() = repository.observeMessages()
    val actions get() = repository.observeActions()

    private var job: Job? = null

    /** Gonderilecek gorseli hazirlar (EXIF, <=1568 px, JPEG). Basarisizsa [ImageException] (Turkce mesajli). */
    suspend fun prepare(uri: Uri): PreparedImage = withContext(Dispatchers.IO) {
        images.prepare { app.contentResolver.openInputStream(uri) }
    }

    /** Hazirlanmis ama gonderilmeyen gorselleri (ve onizlemelerini) siler. */
    fun discard(list: List<PreparedImage>) {
        if (list.isEmpty()) return
        scope.launch(NonCancellable + Dispatchers.IO) { list.forEach { images.delete(it.path) } }
    }

    fun send(text: String, imageUris: List<Uri> = emptyList(), onDone: (SendResult) -> Unit = {}) {
        if (sending.value) { error.value = ErrorMapper.busy(); onDone(SendResult(false, error.value)); return }
        sending.value = true
        cancelling.value = false
        error.value = null
        start(onDone) {
            val blocks = withContext(Dispatchers.IO) {
                val done = ArrayList<Block.Image>()
                try {
                    imageUris.map { uri -> images.prepare { app.contentResolver.openInputStream(uri) }.toBlock().also { done += it } }
                } catch (e: ImageException) {
                    // Biri basarisizsa oncekiler sahipsiz kalmasin.
                    done.forEach { images.delete(it.path) }
                    error.value = AssistantError(AssistantError.Kind.BAD_REQUEST, e.message ?: "Görsel eklenemedi.")
                    null
                }
            }
            if (blocks != null) engine.send(text, blocks) else null
        }
    }

    /** Onceden hazirlanmis gorsellerle gonderim (gorseller gonderimden sonra sohbetin parcasi olur; hata olursa motor siler). */
    fun sendPrepared(text: String, prepared: List<PreparedImage>, onDone: (SendResult) -> Unit = {}) {
        if (sending.value) { discard(prepared); error.value = ErrorMapper.busy(); onDone(SendResult(false, error.value)); return }
        sending.value = true
        cancelling.value = false
        error.value = null
        start(onDone) { engine.send(text, prepared.map { it.toBlock() }) }
    }

    /** Yanitsiz kalan son kullanici mesajini ayni gecmisle yeniden dener (mesaj ikilenmez). */
    fun resume(onDone: (SendResult) -> Unit = {}) {
        if (sending.value) { error.value = ErrorMapper.busy(); onDone(SendResult(false, error.value)); return }
        sending.value = true
        cancelling.value = false
        error.value = null
        start(onDone) { engine.resume() }
    }

    private fun start(onDone: (SendResult) -> Unit, block: suspend () -> TurnOutcome?) {
        job = scope.launch {
            var out: TurnOutcome? = null
            var cancelled = false
            val before = withContext(Dispatchers.IO) { runCatching { repository.chat.all().size }.getOrDefault(0) }
            try {
                out = block()
                // Gorsel hazirlama hatasi arayuze error ile bildirilmistir; motor hatasi burada yazilir.
                if (out != null) error.value = out.error
            } catch (e: CancellationException) {
                cancelled = true
            } finally {
                withContext(NonCancellable) {
                    val after = withContext(Dispatchers.IO) { runCatching { repository.chat.all().size }.getOrDefault(before) }
                    sending.value = false
                    cancelling.value = false
                    onDone(SendResult(stored = after > before, error = if (cancelled) null else error.value, cancelled = cancelled))
                }
            }
        }
    }

    /**
     * Surmekte olan gonderimi iptal eder. Ag istegi bloklayan oldugundan istek bitene (en cok 90 sn) dek
     * [cancelling] true kalir; sonuc yok sayilir.
     */
    fun cancel() {
        if (!sending.value) return
        cancelling.value = true
        job?.cancel()
    }

    fun confirm(actionId: Long, onResult: (ConfirmResult) -> Unit = {}) {
        scope.launch { onResult(repository.confirm(actionId)) }
    }

    fun confirmEdited(actionId: Long, edited: ActionPayload, onResult: (ConfirmResult) -> Unit = {}) {
        scope.launch { onResult(repository.confirmEdited(actionId, edited)) }
    }

    fun reject(actionId: Long, onResult: (Boolean) -> Unit = {}) {
        scope.launch { onResult(try { repository.reject(actionId) } catch (e: CancellationException) { throw e } catch (e: Exception) { false }) }
    }

    /** Sohbeti temizle: mesajlar, oneriler ve gorseller silinir. Gonderim surerken yapilmaz. */
    fun clearChat() {
        if (engine.isBusy || sending.value) { error.value = ErrorMapper.busy(); return }
        scope.launch(Dispatchers.IO) { repository.clearChat(images.dir) }
    }

    fun clearError() { error.value = null }

    /** Gorsel dosyasi yoksa (yeni cihaz, silinmis) null: arayuz yer tutucu gosterir. */
    fun imageFile(path: String?) = images.fileOrNull(path)

    /** Onizleme dosyasi (varsa), yoksa tam gorsel, yoksa null. */
    fun previewFile(path: String?): File? {
        val full = images.fileOrNull(path) ?: return null
        val thumb = File(full.parentFile, full.nameWithoutExtension + "_thumb.jpg")
        return if (thumb.isFile) thumb else full
    }

    /** Surec yeni acildiginda: hicbir mesaja bagli olmayan gorsel dosyalarini siler (surec olumunden kalanlar). */
    suspend fun sweepOrphanImages() = withContext(Dispatchers.IO) {
        try {
            val dir = images.dir
            val files = dir.listFiles()?.map { it.name }.orEmpty()
            if (files.isEmpty()) return@withContext
            val referenced = repository.chat.all().flatMap { m -> m.blocks.filterIsInstance<Block.Image>().map { File(it.path).name } }.toSet()
            ImageOrphans.find(files, referenced).forEach { File(dir, it).delete() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { /* temizlik en iyi cabadir */ }
    }

    /** Secili saglayici icin kalan istek hakki (sinirsizsa null) ve yeniden acilma zamani (epoch ms). */
    fun remainingRequests(): Int? = settings.config.value.let { usage.remaining(it.provider, it.dailyLimit) }
    fun reopensAt(): Long? = settings.config.value.let { usage.reopensAt(it.provider, it.dailyLimit) }

    /**
     * Kisa bir istek atar; 1 istek SAYILIR (kalan hak azalir). Sohbete yazilmaz.
     * @return null = baglanti calisiyor; aksi halde Turkce hata.
     */
    suspend fun testConnection(): AssistantError? {
        if (sending.value || engine.isBusy || testing.value) return ErrorMapper.busy()
        testing.value = true
        try {
            val cfg = settings.config.value
            val key = keys.get(cfg.provider) ?: return ErrorMapper.noKey()
            if (cfg.model.isBlank()) return ErrorMapper.badConfig("Model adı girilmemiş. Model adını yaz.")
            if (cfg.provider == ProviderId.OPENAI_COMPAT) UrlPolicy.check(cfg.baseUrl, debuggable)?.let { return ErrorMapper.badConfig(it) }
            if (!usage.tryAcquire(cfg.provider, cfg.dailyLimit)) return ErrorMapper.daily()
            val provider = makeProvider(cfg, key)
            val r = withContext(Dispatchers.IO) {
                provider.complete(
                    LlmRequest(
                        model = cfg.model,
                        system = "Kısa yanıt ver.",
                        messages = listOf(ChatMessage(Role.USER, listOf(Block.Text("Merhaba")))),
                        tools = emptyList(),
                        maxTokens = 256,
                        effort = if (cfg.provider == ProviderId.ANTHROPIC) "low" else null,
                    )
                )
            }
            return if (r.status == Status.ERROR || r.status == Status.REFUSED) (r.error ?: ErrorMapper.invalid()) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return ErrorMapper.fromThrowable(e)
        } finally {
            testing.value = false
        }
    }
}

/** Saf: hicbir mesajin gorseline bagli olmayan dosyalar. Onizleme dosyasi ('_thumb') ana dosyayla birlikte yasar. */
object ImageOrphans {
    fun find(fileNames: List<String>, referencedNames: Set<String>): List<String> = fileNames.filter { n ->
        val base = if (n.endsWith("_thumb.jpg")) n.removeSuffix("_thumb.jpg") + ".jpg" else n
        base !in referencedNames
    }
}
