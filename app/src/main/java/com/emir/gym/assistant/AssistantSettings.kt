package com.emir.gym.assistant

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Secili saglayici icin etkin ayarlar. */
data class AssistantConfig(
    val provider: ProviderId,
    val model: String,
    /** Yalniz Anthropic. */
    val effort: String,
    /** Yalniz OpenAI uyumlu. */
    val baseUrl: String,
    val supportsImages: Boolean,
    val supportsTools: Boolean,
    val autoAdd: Boolean,
    /** Secili saglayici icin gunluk istek sinirlari; null = sinirsiz. */
    val dailyLimit: Int?,
)

/** Saf dogrulamalar (testli). */
object ConfigRules {
    private val MODEL = Regex("^[A-Za-z0-9][A-Za-z0-9._:/@-]{0,99}$")
    const val MAX_DAILY_LIMIT = 1_000_000

    /** floor(%99) >= 1 olan en kucuk sinir: 1 girilirse hicbir istek atilamaz. */
    fun minimumUsableLimit(): Int = 2

    /** Efektif istek ust siniri (floor(limit x 0.99)); null = sinirsiz. */
    fun effectiveCap(limit: Int?): Int? = QuotaLogic.cap(limit)

    /** Gemini'de 'models/' oneki atilir; '/' icermeyen ad beklenir. */
    fun cleanModel(provider: ProviderId, raw: String): String? {
        var t = raw.trim()
        if (provider == ProviderId.GEMINI) t = t.removePrefix("models/")
        if (!MODEL.matches(t)) return null
        if (provider != ProviderId.OPENAI_COMPAT && '/' in t) return null
        return t
    }

    fun validLimit(n: Int?) = n == null || n in minimumUsableLimit()..MAX_DAILY_LIMIT
}

/**
 * Asistan ayarlari (SharedPreferences; anahtarlar burada DEGIL, [ApiKeyStore]'da sifreli).
 * Otomatik ekle varsayilan KAPALI; OpenAI uyumlu: gorsel varsayilan kapali, arac varsayilan acik.
 */
class AssistantSettings(context: Context, private val allowLocalCleartext: Boolean = false) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("assistant_settings", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(read())
    val config: StateFlow<AssistantConfig> = _config

    private fun defaultModel(p: ProviderId) = when (p) {
        ProviderId.ANTHROPIC -> AnthropicWire.DEFAULT_MODEL
        ProviderId.GEMINI -> GeminiWire.DEFAULT_MODEL
        ProviderId.OPENAI_COMPAT -> ""
    }

    private fun read(): AssistantConfig {
        val p = prefs.getString("provider", null)?.let { n -> ProviderId.entries.firstOrNull { it.name == n } } ?: ProviderId.ANTHROPIC
        return AssistantConfig(
            provider = p,
            model = modelOf(p),
            effort = prefs.getString("effort", null)?.takeIf { it in AnthropicWire.EFFORTS } ?: AnthropicWire.DEFAULT_EFFORT,
            baseUrl = prefs.getString("openai_base_url", null) ?: OpenAiWire.EXAMPLE_BASE_URL,
            supportsImages = prefs.getBoolean("openai_images", false),
            supportsTools = prefs.getBoolean("openai_tools", true),
            autoAdd = prefs.getBoolean("auto_add", false),
            dailyLimit = limitOf(p),
        )
    }

    fun modelOf(p: ProviderId): String = prefs.getString("model_${p.name}", null)?.takeIf { it.isNotBlank() } ?: defaultModel(p)
    fun limitOf(p: ProviderId): Int? = if (prefs.contains("limit_${p.name}")) prefs.getInt("limit_${p.name}", 0) else null

    private fun commit(block: SharedPreferences.Editor.() -> Unit) {
        prefs.edit().apply(block).commit()
        _config.value = read()
    }

    fun setProvider(p: ProviderId) = commit { putString("provider", p.name) }

    /** @return false: gecersiz model adi. */
    fun setModel(p: ProviderId, model: String): Boolean {
        val m = ConfigRules.cleanModel(p, model) ?: return false
        commit { putString("model_${p.name}", m) }
        return true
    }

    fun setEffort(effort: String): Boolean {
        if (effort !in AnthropicWire.EFFORTS) return false
        commit { putString("effort", effort) }
        return true
    }

    /** @return false: adres https degil / gecersiz. */
    fun setBaseUrl(url: String): Boolean {
        if (UrlPolicy.check(url, allowLocalCleartext) != null) return false
        commit { putString("openai_base_url", UrlPolicy.normalize(url)) }
        return true
    }

    fun setSupportsImages(v: Boolean) = commit { putBoolean("openai_images", v) }
    fun setSupportsTools(v: Boolean) = commit { putBoolean("openai_tools", v) }
    fun setAutoAdd(v: Boolean) = commit { putBoolean("auto_add", v) }

    /** null = sinirsiz. En az [ConfigRules.minimumUsableLimit] (2), en cok MAX; disi gecersiz (false). */
    fun setDailyLimit(p: ProviderId, limit: Int?): Boolean {
        if (!ConfigRules.validLimit(limit)) return false
        commit { if (limit == null) remove("limit_${p.name}") else putInt("limit_${p.name}", limit) }
        return true
    }
}
