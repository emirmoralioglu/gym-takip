package com.emir.gym.ui

import com.emir.gym.assistant.ActionEdit
import com.emir.gym.assistant.ActionPayload
import com.emir.gym.assistant.ActionRef
import com.emir.gym.assistant.AssistantConfig
import com.emir.gym.assistant.AssistantEngine
import com.emir.gym.assistant.AssistantError
import com.emir.gym.assistant.Block
import com.emir.gym.assistant.ChatMessage
import com.emir.gym.assistant.ConfigRules
import com.emir.gym.assistant.ProviderId
import com.emir.gym.assistant.Role
import com.emir.gym.assistant.ToolDefs
import com.emir.gym.assistant.UrlPolicy
import com.emir.gym.data.ActivityType
import com.emir.gym.data.MealType
import com.emir.gym.data.PendingActionEntity
import com.emir.gym.data.PendingStatus
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** **kalin** ve madde isaretli satirlari ayristiran saf biçimlendirme (baska Markdown yok). */
object ChatFormat {
    data class Span(val text: String, val bold: Boolean)

    sealed interface Line {
        data class Para(val spans: List<Span>) : Line
        data class Bullet(val spans: List<Span>) : Line
        /** Bos satir: paragraflar arasi bosluk. */
        object Gap : Line
    }

    private val BULLETS = listOf("- ", "* ", "• ", "– ")

    fun parse(text: String): List<Line> {
        val out = ArrayList<Line>()
        for (raw in text.replace("\r\n", "\n").split('\n')) {
            val t = raw.trim()
            if (t.isEmpty()) {
                if (out.isNotEmpty() && out.last() !is Line.Gap) out += Line.Gap
                continue
            }
            val b = BULLETS.firstOrNull { t.startsWith(it) }
            out += if (b != null) Line.Bullet(spans(t.removePrefix(b).trim())) else Line.Para(spans(t))
        }
        while (out.lastOrNull() is Line.Gap) out.removeAt(out.lastIndex)
        return out
    }

    /** Eslesmeyen son ** metin olarak kalir. */
    fun spans(s: String): List<Span> {
        val parts = s.split("**")
        val pairs = (parts.size - 1) / 2 * 2 // eslesen ** isaretlerinin sayisi (cift)
        val out = ArrayList<Span>()
        var bold = false
        for ((i, p) in parts.withIndex()) {
            if (i > 0) {
                if (i <= pairs) bold = !bold else out += Span("**", false)
            }
            if (p.isNotEmpty()) out += Span(p, bold)
        }
        return merge(out)
    }

    private fun merge(l: List<Span>): List<Span> {
        val out = ArrayList<Span>()
        for (s in l) {
            val last = out.lastOrNull()
            if (last != null && last.bold == s.bold) out[out.lastIndex] = Span(last.text + s.text, s.bold) else out += s
        }
        return out
    }
}

/** Sohbet listesinin ogeleri. */
sealed interface ChatItem {
    val key: String

    data class User(val id: Long, val text: String, val imagePaths: List<String>) : ChatItem { override val key get() = "u$id" }
    data class Assistant(val id: Long, val text: String) : ChatItem { override val key get() = "a$id" }
    data class Card(val card: ActionCard) : ChatItem { override val key get() = "c${card.id}" }

    /** Yanitsiz kalan son mesaj: 'Gonderilemedi - Tekrar dene'. [toolStep] true ise yanit yarida kalmis (arac adimi). */
    data class Retry(val toolStep: Boolean) : ChatItem { override val key get() = "retry" }
}

data class ActionCard(
    val id: Long,
    val payload: ActionPayload,
    val status: PendingStatus,
    /** Otomatik ekle ile eklendi. */
    val auto: Boolean,
    /** Kullanici duzenleyerek ekledi. */
    val edited: Boolean,
)

object ChatItems {
    /** Otomatik eklenen oneriler icin arac sonucu bu on-ekle baslar (bkz. ToolExecutor). */
    const val AUTO_PREFIX = "Eklendi:"

    fun build(messages: List<ChatMessage>, actions: List<PendingActionEntity>): List<ChatItem> {
        val byId = actions.associateBy { it.id }
        val out = ArrayList<ChatItem>()
        val shown = HashSet<Long>()
        for (m in messages) {
            when (m.role) {
                Role.ASSISTANT -> if (m.text.isNotBlank()) out += ChatItem.Assistant(m.id, m.text)
                Role.USER -> {
                    val imgs = m.blocks.filterIsInstance<Block.Image>().map { it.path }
                    val text = m.text
                    if (text.isNotBlank() || imgs.isNotEmpty()) out += ChatItem.User(m.id, text, imgs)
                    for (r in m.blocks.filterIsInstance<Block.ToolResult>()) {
                        val aid = r.actionId ?: continue
                        val a = byId[aid] ?: continue
                        if (!shown.add(aid)) continue
                        val original = com.emir.gym.assistant.ToolValidator.decode(a.type, a.payloadJson) ?: continue
                        val edited = if (a.status == PendingStatus.CONFIRMED) ActionRef.edited(a.type, a.resultRef) else null
                        out += ChatItem.Card(
                            ActionCard(
                                id = aid,
                                payload = edited ?: original,
                                status = a.status,
                                auto = a.status == PendingStatus.CONFIRMED && r.content.startsWith(AUTO_PREFIX),
                                edited = edited != null,
                            )
                        )
                    }
                }
            }
        }
        return out
    }

    /** Son kayit kullanici mesajiysa model yanit vermemis demektir: tekrar denenebilir. */
    fun resumable(messages: List<ChatMessage>): Boolean = messages.lastOrNull()?.role == Role.USER

    /** Tekrar-dene ogesi: yalniz bekleyen gonderim yokken ve yanitsiz mesaj varsa. */
    fun withRetry(items: List<ChatItem>, messages: List<ChatMessage>, sending: Boolean): List<ChatItem> {
        if (sending || !resumable(messages)) return items
        val last = messages.last()
        val toolStep = last.blocks.any { it is Block.ToolResult }
        return items + ChatItem.Retry(toolStep)
    }
}

/** Gonder / ekle dugmeleri ve gorsel destegi icin saf kurallar. */
object ComposerRules {
    const val MAX_IMAGES = 3

    fun supportsImages(cfg: AssistantConfig): Boolean =
        if (cfg.provider == ProviderId.OPENAI_COMPAT) cfg.supportsImages else true

    fun canSend(text: String, imageCount: Int, sending: Boolean, preparing: Boolean, configured: Boolean): Boolean =
        configured && !sending && !preparing && text.length <= AssistantEngine.MAX_TEXT && (text.isNotBlank() || imageCount > 0)

    fun canAttach(imagesSupported: Boolean, attached: Int, sending: Boolean, preparing: Boolean, configured: Boolean): Boolean =
        configured && imagesSupported && !sending && !preparing && attached < MAX_IMAGES

    /** Galeriden en cok kac gorsel secilebilir (kalan hak). */
    fun remainingSlots(attached: Int): Int = (MAX_IMAGES - attached).coerceAtLeast(0)
}

/** Hata bandi: ne gosterilir, hangi dugmeler cikar. */
data class ErrorBand(val message: String, val canRetry: Boolean, val toSettings: Boolean, val reopensAt: Long?)

object ErrorBands {
    private val SETTINGS_KINDS = setOf(
        AssistantError.Kind.NO_KEY, AssistantError.Kind.UNAUTHORIZED, AssistantError.Kind.BAD_CONFIG,
        AssistantError.Kind.MODEL_NOT_FOUND, AssistantError.Kind.IMAGE_UNSUPPORTED, AssistantError.Kind.TOOL_SCHEMA,
        AssistantError.Kind.BILLING,
    )

    /** Ayarlar degismeden tekrar denemenin anlamsiz oldugu turler. */
    private val NO_RETRY = SETTINGS_KINDS + AssistantError.Kind.DAILY_LIMIT + AssistantError.Kind.BUSY

    fun of(e: AssistantError, canResume: Boolean, reopensAt: Long?): ErrorBand = ErrorBand(
        message = e.message,
        canRetry = canResume && e.kind !in NO_RETRY,
        toSettings = e.kind in SETTINGS_KINDS,
        reopensAt = if (e.kind == AssistantError.Kind.DAILY_LIMIT) reopensAt else null,
    )

    /** 'bugun 14:30' / 'yarin 02:10' / '28.11 09:00'. */
    fun reopenText(reopensAt: Long, now: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(reopensAt), zone)
        val today = LocalDateTime.ofInstant(Instant.ofEpochMilli(now), zone).toLocalDate()
        val hm = t.format(DateTimeFormatter.ofPattern("HH:mm"))
        return when (t.toLocalDate()) {
            today -> "bugün $hm"
            today.plusDays(1) -> "yarın $hm"
            else -> t.format(DateTimeFormatter.ofPattern("dd.MM HH:mm"))
        }
    }
}

/** Kart 'Duzenle' formu: alanlar metin; [toPayload] mevcut dogrulama sinirlariyla tipli kayda cevirir. */
data class EditForm(
    val description: String = "",
    val protein: String = "",
    val calories: String = "",
    val carbs: String = "",
    val fat: String = "",
    val ml: String = "",
    val kg: String = "",
    val minutes: String = "",
    val distance: String = "",
    val note: String = "",
)

object CardEdit {
    sealed interface Result {
        data class Ok(val payload: ActionPayload) : Result
        /** [fields]: hatali alan adlari (EditForm alan adi); [message]: ilk hata (Turkce). */
        data class Invalid(val fields: Set<String>, val message: String) : Result
    }

    private fun num(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

    fun formOf(p: ActionPayload): EditForm = when (p) {
        is ActionPayload.AddMeal -> EditForm(
            description = p.description, protein = num(p.proteinG), calories = p.calories.toString(),
            carbs = p.carbsG?.let(::num).orEmpty(), fat = p.fatG?.let(::num).orEmpty(),
        )
        is ActionPayload.AddWater -> EditForm(ml = p.ml.toString())
        is ActionPayload.LogWeight -> EditForm(kg = num(p.kg))
        is ActionPayload.LogActivity -> EditForm(minutes = p.minutes.toString(), distance = p.distanceM?.toString().orEmpty(), note = p.note)
    }

    fun toPayload(orig: ActionPayload, f: EditForm, today: LocalDate = LocalDate.now()): Result {
        val bad = linkedSetOf<String>()
        var msg: String? = null
        fun fail(field: String, m: String) { bad += field; if (msg == null) msg = m }
        val payload: ActionPayload? = when (orig) {
            is ActionPayload.AddMeal -> {
                val r = MealForm.validate(MealForm.Input(orig.mealType, f.description, f.protein, f.calories, f.carbs, f.fat), orig.epochDay)
                when (r) {
                    is MealForm.Result.Ok -> ActionPayload.AddMeal(
                        orig.epochDay, orig.mealType, r.meal.description, r.meal.proteinG, r.meal.calories, r.meal.carbsG, r.meal.fatG,
                    )
                    is MealForm.Result.Invalid -> {
                        r.errors.forEach { (field, err) ->
                            val name = when (field) {
                                MealForm.Field.DESCRIPTION -> "description"; MealForm.Field.PROTEIN -> "protein"
                                MealForm.Field.CALORIES -> "calories"; MealForm.Field.CARBS -> "carbs"; MealForm.Field.FAT -> "fat"
                            }
                            fail(name, when (field) {
                                MealForm.Field.DESCRIPTION -> if (err == MealForm.Error.EMPTY) "Açıklama boş olamaz." else "Açıklama en fazla ${MealForm.MAX_DESC} karakter olabilir."
                                MealForm.Field.PROTEIN -> "Protein 0 ile ${MealForm.MAX_PROTEIN.toInt()} g arasında olmalı."
                                MealForm.Field.CALORIES -> "Kalori 0 ile ${MealForm.MAX_KCAL} arasında tam sayı olmalı."
                                MealForm.Field.CARBS -> "Karbonhidrat 0 ile ${MealForm.MAX_CARBS.toInt()} g arasında olmalı."
                                MealForm.Field.FAT -> "Yağ 0 ile ${MealForm.MAX_FAT.toInt()} g arasında olmalı."
                            })
                        }
                        null
                    }
                }
            }
            is ActionPayload.AddWater -> {
                val v = NutritionUi.parseWaterMl(f.ml)
                if (v == null) { fail("ml", "Su 1 ile ${NutritionUi.MAX_WATER_ML} ml arasında olmalı."); null } else ActionPayload.AddWater(orig.epochDay, v)
            }
            is ActionPayload.LogWeight -> {
                val v = MealForm.parseDecimal(f.kg)
                if (v == null || v <= 0.0 || v > ToolDefs.MAX_KG) { fail("kg", "Kilo 0 ile ${ToolDefs.MAX_KG} kg arasında olmalı."); null }
                else ActionPayload.LogWeight(orig.epochDay, Math.round(v * 100) / 100.0)
            }
            is ActionPayload.LogActivity -> {
                val min = f.minutes.trim().takeIf { Regex("^\\d{1,4}$").matches(it) }?.toInt()
                if (min == null || min < 1 || min > ToolDefs.MAX_MINUTES) fail("minutes", "Süre 1 ile ${ToolDefs.MAX_MINUTES} dakika arasında olmalı.")
                var dist: Int? = null
                if (f.distance.isNotBlank()) {
                    dist = f.distance.trim().takeIf { Regex("^\\d{1,6}$").matches(it) }?.toInt()
                    if (dist == null || dist < 1 || dist > ToolDefs.MAX_DISTANCE_M) fail("distance", "Mesafe 1 ile ${ToolDefs.MAX_DISTANCE_M} m arasında olmalı.")
                    else if (orig.activity != ActivityType.YUZME) fail("distance", "Mesafe yalnızca yüzme için girilebilir.")
                }
                if (f.note.trim().length > ToolDefs.MAX_NOTE) fail("note", "Not en fazla ${ToolDefs.MAX_NOTE} karakter olabilir.")
                if (bad.isEmpty()) ActionPayload.LogActivity(orig.epochDay, orig.activity, min!!, dist, f.note.trim()) else null
            }
        }
        if (payload == null) return Result.Invalid(bad, msg ?: "Değerler geçersiz.")
        // Son kapi: arka uctaki ayni kural.
        ActionEdit.check(payload, today)?.let { return Result.Invalid(emptySet(), it) }
        return Result.Ok(payload)
    }

    /** Kartin duzenlenebilir alanlari (kilo/su/etkinlik icin dar form). */
    fun mealTypeLabel(t: MealType) = t.label
}

/** Ayarlar alani dogrulamalari. */
object AssistantSettingsRules {
    sealed interface LimitParse {
        object Unlimited : LimitParse
        data class Value(val n: Int) : LimitParse
        object Invalid : LimitParse
    }

    /** Bos = sinirsiz; yalniz rakam; en az [ConfigRules.minimumUsableLimit], en cok [ConfigRules.MAX_DAILY_LIMIT]. */
    fun parseLimit(text: String): LimitParse {
        val t = text.trim()
        if (t.isEmpty()) return LimitParse.Unlimited
        if (!Regex("^\\d{1,5}$").matches(t)) return LimitParse.Invalid
        val n = t.toInt()
        return if (ConfigRules.validLimit(n)) LimitParse.Value(n) else LimitParse.Invalid
    }

    /** null = gecerli. Release'te yalniz https; debug'ta ek olarak yerel http. */
    fun baseUrlError(url: String, debuggable: Boolean): String? = UrlPolicy.check(url, debuggable)

    /** Claude modelleri icin tek satir maliyet ipucu anahtari (bilinmeyen = null). */
    enum class ClaudeTier { OPUS, SONNET, HAIKU }

    fun claudeTier(model: String): ClaudeTier? = when (model) {
        "claude-opus-5-5" -> ClaudeTier.OPUS
        "claude-sonnet-5-5" -> ClaudeTier.SONNET
        "claude-haiku-5-5" -> ClaudeTier.HAIKU
        else -> null
    }

    val EFFORT_CHOICES = listOf("low", "medium", "high")
}
