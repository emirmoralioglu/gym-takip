package com.emir.gym.assistant

/** Saglayicidan bagimsiz sohbet modeli. */

enum class ProviderId(val label: String) {
    ANTHROPIC("Claude (Anthropic)"),
    GEMINI("Gemini (Google)"),
    OPENAI_COMPAT("OpenAI uyumlu"),
}

enum class Role { USER, ASSISTANT }

sealed interface Block {
    data class Text(val text: String) : Block

    /** Kullanici gorseli; dosya uygulama ozel dizinindedir. */
    data class Image(val path: String, val mime: String) : Block

    /** Modelin istedigi arac cagrisi. meta: saglayiciya ozel gerekli veri (Gemini thoughtSignature). */
    data class ToolUse(val id: String, val name: String, val argsJson: String, val meta: String? = null) : Block

    /** Arac sonucu. actionId: sonuc bir onay kartina bagliysa o kartin id'si. */
    data class ToolResult(val id: String, val content: String, val isError: Boolean = false, val actionId: Long? = null) : Block

    /** Her kullanici turunun basindaki degisken baglam (DB'den). Arayuzde gosterilmez; saglayiciya metin olarak gider. */
    data class Context(val text: String) : Block
}

data class ChatMessage(
    val role: Role,
    val blocks: List<Block>,
    /** Yalniz Anthropic assistant: thinking dahil ham icerik dizisi (JSON); degistirilmeden geri gonderilir. */
    val rawAnthropic: String? = null,
    val provider: String = "",
    val model: String = "",
    val id: Long = 0,
    val createdAt: Long = 0,
) {
    val text: String get() = blocks.filterIsInstance<Block.Text>().joinToString("\n") { it.text }
}

/** Arac sema alt kumesi: type, description, enum, properties, required, items, minimum, maximum. */
data class ToolSpec(val name: String, val description: String, val parametersJson: String)

data class Capabilities(val supportsImages: Boolean, val supportsTools: Boolean)

data class LlmRequest(
    val model: String,
    val system: String,
    val messages: List<ChatMessage>,
    val tools: List<ToolSpec>,
    val maxTokens: Int,
    /** Yalniz Anthropic: low|medium|high|xhigh|max. */
    val effort: String? = null,
    /** Gorsel yolu -> base64 (yoksa null). Ag/dosya erisimi saf kurucudan ayri tutulur. */
    val imageBase64: (String) -> String? = { null },
)

data class ToolCall(val id: String, val name: String, val argsJson: String, val meta: String? = null)

data class Usage(val inputTokens: Int = 0, val outputTokens: Int = 0)

enum class Status { COMPLETED, REFUSED, MAX_TOKENS, ERROR }

data class LlmResult(
    val status: Status,
    val text: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val usage: Usage = Usage(),
    val error: AssistantError? = null,
    /** Anthropic: ham assistant icerik dizisi (JSON). */
    val rawAnthropic: String? = null,
)

interface LlmProvider {
    val id: ProviderId
    val capabilities: Capabilities
    suspend fun complete(req: LlmRequest): LlmResult
}

/** Kullaniciya gosterilecek tek hata turu. Mesaj Turkce, kisa; yigin izi, anahtar ya da ham govde icermez. */
data class AssistantError(
    val kind: Kind,
    val message: String,
    val retryAfterSec: Int? = null,
) {
    enum class Kind {
        NO_KEY, BAD_CONFIG, UNAUTHORIZED, MODEL_NOT_FOUND, RATE_LIMITED, SERVER, TIMEOUT, NETWORK,
        REFUSED, TRUNCATED, TOOL_SCHEMA, BAD_REQUEST, TOO_LARGE, CONTEXT_TOO_LONG, BILLING,
        DAILY_LIMIT, IMAGE_UNSUPPORTED, LOOP_LIMIT, INVALID_RESPONSE, BUSY, UNKNOWN,
    }
}
