package com.emir.gym.assistant

import java.time.LocalDate
import kotlinx.coroutines.sync.Mutex

data class TurnOutcome(
    /** Son asistan metni (hata olsa bile varsa kismi metin). */
    val text: String = "",
    val error: AssistantError? = null,
    /** Bu turda olusan onay kartlari. */
    val actionIds: List<Long> = emptyList(),
    val requests: Int = 0,
) {
    val ok: Boolean get() = error == null
}

/**
 * Sohbet motoru: kullanici mesajini kaydeder, saglayiciyi cagirir, arac dongusunu (en cok [MAX_ROUNDS] tur)
 * elle yurutur, her HTTP istegini gunluk sayaca yazar. Saglayiciya kendiliginden GECMEZ.
 */
class AssistantEngine(
    private val chat: ChatStore,
    private val tools: ToolExecutor,
    private val usage: UsageStore,
    private val keyOf: (ProviderId) -> String?,
    private val config: () -> AssistantConfig,
    private val providerFor: (AssistantConfig, String) -> LlmProvider,
    private val contextText: suspend (LocalDate, Boolean) -> String,
    private val imageBase64: (String) -> String? = { null },
    private val today: () -> LocalDate = { LocalDate.now() },
    private val now: () -> Long = System::currentTimeMillis,
    private val allowLocalCleartext: Boolean = false,
    /** Mesaj hic kaydedilmeden reddedilirse hazirlanan gorsellerin silinmesi icin (sahipsiz dosya kalmaz). */
    private val discardImages: (List<Block.Image>) -> Unit = {},
) {
    companion object {
        const val MAX_ROUNDS = 6
        const val MAX_TEXT = 4000
        const val MAX_IMAGES = 4
    }

    private val lock = Mutex()
    val isBusy: Boolean get() = lock.isLocked

    suspend fun send(text: String, images: List<Block.Image> = emptyList()): TurnOutcome = turn(text, images, resume = false)

    /**
     * Yanitsiz kalan son kullanici mesajini YENIDEN gondermeden ayni gecmisle tekrar dener (mesaj ikilenmez).
     * Son kayit bir kullanici mesaji degilse hicbir sey yapmaz.
     */
    suspend fun resume(): TurnOutcome = turn("", emptyList(), resume = true)

    private suspend fun turn(text: String, images: List<Block.Image>, resume: Boolean): TurnOutcome {
        if (!lock.tryLock()) { discardSafely(images); return TurnOutcome(error = ErrorMapper.busy()) }
        stored = false
        try {
            return run(text.trim(), images, resume)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            return TurnOutcome(error = AssistantError(AssistantError.Kind.UNKNOWN, "Beklenmeyen bir hata oluştu."))
        } finally {
            // Kullanici mesaji kaydedilmediyse gorsel dosyalari kimseye bagli degildir: silinir.
            if (!stored) discardSafely(images)
            lock.unlock()
        }
    }

    @Volatile private var stored = false

    private fun discardSafely(images: List<Block.Image>) {
        if (images.isNotEmpty()) runCatching { discardImages(images) }
    }

    private suspend fun run(text: String, images: List<Block.Image>, resume: Boolean = false): TurnOutcome {
        if (resume && chat.all().lastOrNull()?.role != Role.USER) {
            return TurnOutcome(error = AssistantError(AssistantError.Kind.BAD_REQUEST, "Tekrar denenecek bir mesaj yok."))
        }
        if (!resume && text.isEmpty() && images.isEmpty()) return TurnOutcome(error = AssistantError(AssistantError.Kind.BAD_REQUEST, "Mesaj boş."))
        if (text.length > MAX_TEXT) return TurnOutcome(error = AssistantError(AssistantError.Kind.BAD_REQUEST, "Mesaj çok uzun (en fazla $MAX_TEXT karakter)."))
        if (images.size > MAX_IMAGES) return TurnOutcome(error = AssistantError(AssistantError.Kind.BAD_REQUEST, "En fazla $MAX_IMAGES görsel eklenebilir."))
        val cfg = config()
        val key = keyOf(cfg.provider) ?: return TurnOutcome(error = ErrorMapper.noKey())
        if (cfg.model.isBlank()) return TurnOutcome(error = ErrorMapper.badConfig("Model adı girilmemiş. Ayarlardan model adını yaz."))
        if (cfg.provider == ProviderId.OPENAI_COMPAT) {
            UrlPolicy.check(cfg.baseUrl, allowLocalCleartext)?.let { return TurnOutcome(error = ErrorMapper.badConfig(it)) }
        }
        val provider = providerFor(cfg, key)
        if (images.isNotEmpty() && !provider.capabilities.supportsImages) return TurnOutcome(error = ErrorMapper.imageUnsupported())
        val toolsOn = provider.capabilities.supportsTools
        val day = today()

        // Ilk istegin sayaci mesaj kaydedilmeden alinir: sinirdaysa mesaj/gorsel hic birakilmaz.
        if (!usage.tryAcquire(cfg.provider, cfg.dailyLimit)) return TurnOutcome(error = ErrorMapper.daily())

        val tag = cfg.provider.name
        if (!resume) {
            val ctx = contextText(day, toolsOn)
            val blocks = ArrayList<Block>()
            blocks += Block.Context(ctx)
            if (text.isNotEmpty()) blocks += Block.Text(text)
            blocks += images
            chat.append(ChatMessage(Role.USER, blocks, provider = tag, model = cfg.model, createdAt = now()))
            stored = true
        }

        val actionIds = ArrayList<Long>()
        var requests = 0
        var lastText = ""
        for (round in 1..MAX_ROUNDS) {
            if (round > 1 && !usage.tryAcquire(cfg.provider, cfg.dailyLimit)) {
                return TurnOutcome(lastText, ErrorMapper.daily(), actionIds, requests)
            }
            requests++
            val history = History.prepare(chat.all(), cfg.provider, cfg.model)
            val result = provider.complete(
                LlmRequest(
                    model = cfg.model,
                    system = SystemPrompt.FIXED,
                    messages = history,
                    tools = if (toolsOn) ToolDefs.all else emptyList(),
                    maxTokens = when (cfg.provider) {
                        ProviderId.ANTHROPIC -> AnthropicWire.MAX_TOKENS
                        ProviderId.GEMINI -> GeminiWire.MAX_TOKENS
                        ProviderId.OPENAI_COMPAT -> OpenAiWire.MAX_TOKENS
                    },
                    effort = if (cfg.provider == ProviderId.ANTHROPIC) cfg.effort else null,
                    imageBase64 = imageBase64,
                )
            )
            if (result.status == Status.ERROR || result.status == Status.REFUSED) {
                return TurnOutcome(lastText, result.error ?: ErrorMapper.invalid(), actionIds, requests)
            }
            val aBlocks = ArrayList<Block>()
            if (result.text.isNotEmpty()) aBlocks += Block.Text(result.text)
            result.toolCalls.forEach { aBlocks += Block.ToolUse(it.id, it.name, it.argsJson, it.meta) }
            if (aBlocks.isEmpty()) return TurnOutcome(lastText, ErrorMapper.invalid(), actionIds, requests)
            chat.append(ChatMessage(Role.ASSISTANT, aBlocks, result.rawAnthropic, tag, cfg.model, createdAt = now()))
            if (result.text.isNotEmpty()) lastText = result.text
            if (result.status == Status.MAX_TOKENS) return TurnOutcome(lastText, ErrorMapper.truncated(), actionIds, requests)
            if (result.toolCalls.isEmpty()) return TurnOutcome(lastText, null, actionIds, requests)

            val results = result.toolCalls.map { call ->
                val o = tools.run(call)
                o.actionId?.let { actionIds += it }
                Block.ToolResult(call.id, o.text, o.isError, o.actionId)
            }
            // Tum sonuclar tek kullanici mesajinda.
            chat.append(ChatMessage(Role.USER, results, provider = tag, model = cfg.model, createdAt = now()))
            if (round == MAX_ROUNDS) return TurnOutcome(lastText, ErrorMapper.loopLimit(), actionIds, requests)
        }
        return TurnOutcome(lastText, ErrorMapper.loopLimit(), actionIds, requests)
    }
}
