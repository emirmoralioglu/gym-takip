package com.emir.gym.assistant

import android.app.Application
import android.content.pm.ApplicationInfo
import android.net.Uri
import com.emir.gym.data.AppDatabase
import com.emir.gym.data.NutritionGoals
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Arayuzun (sonraki tur) kullanacagi tek giris noktasi. GymViewModel'den tembelce olusturulur;
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
        providerFor = { cfg, key ->
            when (cfg.provider) {
                ProviderId.ANTHROPIC -> AnthropicProvider(key)
                ProviderId.GEMINI -> GeminiProvider(key)
                ProviderId.OPENAI_COMPAT -> OpenAiCompatProvider(key, cfg.baseUrl, cfg.supportsImages, cfg.supportsTools, debuggable)
            }
        },
        contextText = { day, toolsOn -> AssistantContext.build(day, repository.readData(), toolsOn) },
        imageBase64 = { images.base64(it) },
        allowLocalCleartext = debuggable,
        discardImages = { list -> list.forEach { images.delete(it.path) } },
    )

    val sending = MutableStateFlow(false)

    /** Son turun hatasi (varsa); arayuz gosterip [clearError] ile temizler. */
    val error: MutableStateFlow<AssistantError?> = MutableStateFlow(null)
    val messages get() = repository.observeMessages()
    val actions get() = repository.observeActions()


    fun send(text: String, imageUris: List<Uri> = emptyList()) {
        if (sending.value) { error.value = ErrorMapper.busy(); return }
        sending.value = true
        error.value = null
        scope.launch {
            try {
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
                if (blocks != null) error.value = engine.send(text, blocks).error
            } finally {
                sending.value = false
            }
        }
    }

    fun confirm(actionId: Long, onResult: (ConfirmResult) -> Unit = {}) {
        scope.launch { onResult(repository.confirm(actionId)) }
    }

    fun reject(actionId: Long) { scope.launch { repository.reject(actionId) } }

    /** Sohbeti temizle: mesajlar, oneriler ve gorseller silinir. Gonderim surerken yapilmaz. */
    fun clearChat() {
        if (engine.isBusy || sending.value) { error.value = ErrorMapper.busy(); return }
        scope.launch(Dispatchers.IO) { repository.clearChat(images.dir) }
    }

    fun clearError() { error.value = null }

    /** Gorsel dosyasi yoksa (yeni cihaz, silinmis) null: arayuz yer tutucu gosterir. */
    fun imageFile(path: String?) = images.fileOrNull(path)

    /** Secili saglayici icin kalan istek hakki (sinirsizsa null) ve yeniden acilma zamani (epoch ms). */
    fun remainingRequests(): Int? = settings.config.value.let { usage.remaining(it.provider, it.dailyLimit) }
    fun reopensAt(): Long? = settings.config.value.let { usage.reopensAt(it.provider, it.dailyLimit) }
}
