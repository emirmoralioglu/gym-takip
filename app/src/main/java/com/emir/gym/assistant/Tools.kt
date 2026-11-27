package com.emir.gym.assistant

import com.emir.gym.data.ActivityType
import com.emir.gym.data.MealType
import com.emir.gym.ui.MealForm
import com.emir.gym.ui.NutritionUi
import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Asistan araclari: tarafsiz sema tanimlari. */
object ToolDefs {
    const val ADD_MEAL = "add_meal"
    const val ADD_WATER = "add_water"
    const val LOG_BODY_WEIGHT = "log_body_weight"
    const val LOG_ACTIVITY = "log_activity"
    const val GET_DAY_SUMMARY = "get_day_summary"
    const val GET_TODAY_PLAN = "get_today_plan"

    val WRITE_TOOLS = setOf(ADD_MEAL, ADD_WATER, LOG_BODY_WEIGHT, LOG_ACTIVITY)

    private const val DATE = """{"type":"string","description":"Tarih, YYYY-MM-DD. Verilmezse bugün. Gelecek tarih kabul edilmez."}"""

    val all: List<ToolSpec> = listOf(
        ToolSpec(
            ADD_MEAL,
            "Kullanıcının günlüğüne öğün ekleme ÖNERİSİ oluşturur (kullanıcı onaylayınca kaydedilir). Yalnızca kullanıcı öğünü kaydetmek istediğinde ve değerleri özetleyip sorduktan sonra çağır.",
            """{"type":"object","properties":{
              "date":$DATE,
              "meal_type":{"type":"string","enum":["kahvalti","ogle","aksam","ara"],"description":"Öğün türü."},
              "description":{"type":"string","description":"Kısa açıklama, en fazla ${MealForm.MAX_DESC} karakter."},
              "protein_g":{"type":"number","minimum":0,"maximum":${MealForm.MAX_PROTEIN.toInt()},"description":"Tahmini protein (g)."},
              "calories_kcal":{"type":"integer","minimum":0,"maximum":${MealForm.MAX_KCAL},"description":"Tahmini kalori (kcal), tam sayı."},
              "carbs_g":{"type":"number","minimum":0,"maximum":${MealForm.MAX_CARBS.toInt()},"description":"Tahmini karbonhidrat (g), isteğe bağlı."},
              "fat_g":{"type":"number","minimum":0,"maximum":${MealForm.MAX_FAT.toInt()},"description":"Tahmini yağ (g), isteğe bağlı."}
            },"required":["meal_type","description","protein_g","calories_kcal"]}""",
        ),
        ToolSpec(
            ADD_WATER, "Su kaydı ekleme önerisi oluşturur.",
            """{"type":"object","properties":{"date":$DATE,"ml":{"type":"integer","minimum":1,"maximum":${NutritionUi.MAX_WATER_ML},"description":"Miktar (ml)."}},"required":["ml"]}""",
        ),
        ToolSpec(
            LOG_BODY_WEIGHT, "Vücut kilosu kaydı önerisi oluşturur (günde tek tartı; aynı güne yazmak eskisini değiştirir).",
            """{"type":"object","properties":{"date":$DATE,"kg":{"type":"number","minimum":1,"maximum":$MAX_KG,"description":"Kilo (kg)."}},"required":["kg"]}""",
        ),
        ToolSpec(
            LOG_ACTIVITY, "Yüzme, sauna ya da buhar kaydı ekleme önerisi oluşturur.",
            """{"type":"object","properties":{
              "date":$DATE,
              "type":{"type":"string","enum":["YUZME","SAUNA","BUHAR"],"description":"Aktivite türü."},
              "minutes":{"type":"integer","minimum":1,"maximum":$MAX_MINUTES,"description":"Süre (dakika)."},
              "distance_m":{"type":"integer","minimum":1,"maximum":$MAX_DISTANCE_M,"description":"Mesafe (metre), yalnızca yüzme için, isteğe bağlı."},
              "note":{"type":"string","description":"Kısa not, en fazla $MAX_NOTE karakter."}
            },"required":["type","minutes"]}""",
        ),
        ToolSpec(
            GET_DAY_SUMMARY, "Bir günün beslenme, su, kilo ve aktivite özetini okur. Veritabanına yazmaz.",
            """{"type":"object","properties":{"date":$DATE}}""",
        ),
        ToolSpec(
            GET_TODAY_PLAN, "Bugünün antrenman planını ve bu haftanın ilerlemesini okur. Veritabanına yazmaz.",
            """{"type":"object","properties":{}}""",
        ),
    ).map { it.copy(parametersJson = it.parametersJson.replace(Regex("\\s*\\n\\s*"), "")) }

    // Arayuzdeki formlarla ayni sinirlar (ActivitySections ozel sabitleri ve SettingsStore kilo siniri).
    const val MAX_KG = 500
    const val MAX_MINUTES = 1000
    const val MAX_DISTANCE_M = 100_000
    const val MAX_NOTE = 80
}

/** Dogrulanmis, veritabanina yazilmaya hazir onerinin verisi. */
sealed interface ActionPayload {
    val type: String
    val epochDay: Long

    data class AddMeal(
        override val epochDay: Long, val mealType: MealType, val description: String,
        val proteinG: Double, val calories: Int, val carbsG: Double?, val fatG: Double?,
    ) : ActionPayload { override val type get() = ToolDefs.ADD_MEAL }

    data class AddWater(override val epochDay: Long, val ml: Int) : ActionPayload { override val type get() = ToolDefs.ADD_WATER }
    data class LogWeight(override val epochDay: Long, val kg: Double) : ActionPayload { override val type get() = ToolDefs.LOG_BODY_WEIGHT }
    data class LogActivity(
        override val epochDay: Long, val activity: ActivityType, val minutes: Int, val distanceM: Int?, val note: String,
    ) : ActionPayload { override val type get() = ToolDefs.LOG_ACTIVITY }
}

sealed interface Validation {
    data class Ok(val payload: ActionPayload) : Validation
    data class Invalid(val message: String) : Validation
}

/** Saf arac arguman dogrulamasi. Hata mesajlari modele doner (Turkce, kisa). */
object ToolValidator {
    private fun num(n: JsonNode?): Double? = when {
        n == null || n.isNull -> null
        n.isNumber -> n.asDouble()
        n.isTextual -> n.asText().trim().replace(',', '.').toDoubleOrNull()
        else -> null
    }?.takeIf { it.isFinite() }

    /** Tam sayi bekleyen alan: 25.0 kabul, 25.5 degil. */
    private fun int(n: JsonNode?): Int? = num(n)?.takeIf { it == Math.floor(it) && Math.abs(it) < 1e9 }?.toInt()

    private sealed interface D { data class Day(val epochDay: Long) : D; data class Bad(val msg: String) : D }

    private fun day(args: JsonNode, today: LocalDate): D {
        val n = args.get("date")
        if (n == null || n.isNull || (n.isTextual && n.asText().isBlank())) return D.Day(today.toEpochDay())
        if (!n.isTextual) return D.Bad("Hata: tarih YYYY-MM-DD biçiminde olmalı.")
        val d = try { LocalDate.parse(n.asText().trim()) } catch (e: DateTimeParseException) { return D.Bad("Hata: tarih YYYY-MM-DD biçiminde olmalı.") }
        if (d.isAfter(today)) return D.Bad("Hata: gelecek tarihe kayıt eklenemez.")
        if (d.isBefore(today.minusYears(5))) return D.Bad("Hata: tarih çok eski.")
        return D.Day(d.toEpochDay())
    }

    fun parseArgs(argsJson: String): JsonNode? =
        (if (argsJson.isBlank()) Js.obj() else Js.parse(argsJson))?.takeIf { it.isObject }

    fun validate(name: String, args: JsonNode, today: LocalDate): Validation {
        val d = when (val r = day(args, today)) { is D.Bad -> return Validation.Invalid(r.msg); is D.Day -> r.epochDay }
        return when (name) {
            ToolDefs.ADD_MEAL -> meal(args, d)
            ToolDefs.ADD_WATER -> {
                val ml = int(args.get("ml")) ?: return bad("ml tam sayı olmalı")
                if (ml < 1 || ml > NutritionUi.MAX_WATER_ML) return bad("ml 1 ile ${NutritionUi.MAX_WATER_ML} arasında olmalı")
                Validation.Ok(ActionPayload.AddWater(d, ml))
            }
            ToolDefs.LOG_BODY_WEIGHT -> {
                val kg = num(args.get("kg")) ?: return bad("kg sayı olmalı")
                if (kg <= 0.0 || kg > ToolDefs.MAX_KG) return bad("kg 0 ile ${ToolDefs.MAX_KG} arasında olmalı")
                Validation.Ok(ActionPayload.LogWeight(d, Math.round(kg * 100) / 100.0))
            }
            ToolDefs.LOG_ACTIVITY -> activity(args, d)
            else -> Validation.Invalid("Hata: bilinmeyen araç.")
        }
    }

    private fun bad(msg: String) = Validation.Invalid("Hata: $msg.")

    private fun meal(a: JsonNode, d: Long): Validation {
        val type = when (Js.text(a, "meal_type")?.trim()?.lowercase()) {
            "kahvalti" -> MealType.KAHVALTI
            "ogle" -> MealType.OGLE
            "aksam" -> MealType.AKSAM
            "ara" -> MealType.ARA
            else -> return bad("meal_type kahvalti, ogle, aksam ya da ara olmalı")
        }
        val desc = Js.text(a, "description")?.trim().orEmpty()
        if (desc.isEmpty()) return bad("açıklama boş olamaz")
        if (desc.length > MealForm.MAX_DESC) return bad("açıklama en fazla ${MealForm.MAX_DESC} karakter olabilir")
        val protein = num(a.get("protein_g")) ?: return bad("protein_g sayı olmalı")
        if (protein < 0 || protein > MealForm.MAX_PROTEIN) return bad("protein 0 ile ${MealForm.MAX_PROTEIN.toInt()} g arasında olmalı")
        val kcal = int(a.get("calories_kcal")) ?: return bad("calories_kcal tam sayı olmalı")
        if (kcal < 0 || kcal > MealForm.MAX_KCAL) return bad("kalori 0 ile ${MealForm.MAX_KCAL} arasında olmalı")
        fun opt(field: String, max: Double): Pair<Double?, Boolean> {
            val n = a.get(field)
            if (n == null || n.isNull) return null to true
            val v = num(n) ?: return null to false
            return if (v < 0 || v > max) null to false else v to true
        }
        val (carbs, okC) = opt("carbs_g", MealForm.MAX_CARBS)
        if (!okC) return bad("karbonhidrat 0 ile ${MealForm.MAX_CARBS.toInt()} g arasında olmalı")
        val (fat, okF) = opt("fat_g", MealForm.MAX_FAT)
        if (!okF) return bad("yağ 0 ile ${MealForm.MAX_FAT.toInt()} g arasında olmalı")
        return Validation.Ok(ActionPayload.AddMeal(d, type, desc, round1(protein), kcal, carbs?.let(::round1), fat?.let(::round1)))
    }

    private fun round1(v: Double) = Math.round(v * 10) / 10.0

    private fun activity(a: JsonNode, d: Long): Validation {
        val type = try { ActivityType.valueOf(Js.text(a, "type")?.trim()?.uppercase().orEmpty()) } catch (e: IllegalArgumentException) {
            return bad("type YUZME, SAUNA ya da BUHAR olmalı")
        }
        val minutes = int(a.get("minutes")) ?: return bad("minutes tam sayı olmalı")
        if (minutes < 1 || minutes > ToolDefs.MAX_MINUTES) return bad("süre 1 ile ${ToolDefs.MAX_MINUTES} dakika arasında olmalı")
        var dist: Int? = null
        val dn = a.get("distance_m")
        if (dn != null && !dn.isNull) {
            dist = int(dn) ?: return bad("distance_m tam sayı olmalı")
            if (dist < 1 || dist > ToolDefs.MAX_DISTANCE_M) return bad("mesafe 1 ile ${ToolDefs.MAX_DISTANCE_M} m arasında olmalı")
            if (type != ActivityType.YUZME) return bad("mesafe yalnızca yüzme için girilebilir")
        }
        val note = Js.text(a, "note")?.trim().orEmpty()
        if (note.length > ToolDefs.MAX_NOTE) return bad("not en fazla ${ToolDefs.MAX_NOTE} karakter olabilir")
        return Validation.Ok(ActionPayload.LogActivity(d, type, minutes, dist, note))
    }

    // ---- Kalicilik (pending_actions.payloadJson) ----

    fun encode(p: ActionPayload): String {
        val o = Js.obj().put("epochDay", p.epochDay)
        when (p) {
            is ActionPayload.AddMeal -> o.put("mealType", p.mealType.name).put("description", p.description).put("proteinG", p.proteinG)
                .put("calories", p.calories).also { n -> p.carbsG?.let { n.put("carbsG", it) }; p.fatG?.let { n.put("fatG", it) } }
            is ActionPayload.AddWater -> o.put("ml", p.ml)
            is ActionPayload.LogWeight -> o.put("kg", p.kg)
            is ActionPayload.LogActivity -> o.put("activity", p.activity.name).put("minutes", p.minutes).put("note", p.note)
                .also { n -> p.distanceM?.let { n.put("distanceM", it) } }
        }
        return Js.str(o)
    }

    fun decode(type: String, json: String): ActionPayload? {
        val o = Js.parse(json) ?: return null
        val d = o.get("epochDay")?.takeIf { it.isNumber }?.asLong() ?: return null
        return try {
            when (type) {
                ToolDefs.ADD_MEAL -> ActionPayload.AddMeal(
                    d, MealType.valueOf(o.get("mealType").asText()), o.get("description").asText(), o.get("proteinG").asDouble(),
                    o.get("calories").asInt(), o.get("carbsG")?.takeIf { it.isNumber }?.asDouble(), o.get("fatG")?.takeIf { it.isNumber }?.asDouble(),
                )
                ToolDefs.ADD_WATER -> ActionPayload.AddWater(d, o.get("ml").asInt())
                ToolDefs.LOG_BODY_WEIGHT -> ActionPayload.LogWeight(d, o.get("kg").asDouble())
                ToolDefs.LOG_ACTIVITY -> ActionPayload.LogActivity(
                    d, ActivityType.valueOf(o.get("activity").asText()), o.get("minutes").asInt(),
                    o.get("distanceM")?.takeIf { it.isNumber }?.asInt(), o.get("note")?.asText().orEmpty(),
                )
                else -> null
            }
        } catch (e: Exception) { null }
    }

    /** Kart ve arac sonucu icin kisa Turkce ozet. */
    fun describe(p: ActionPayload): String {
        val day = LocalDate.ofEpochDay(p.epochDay)
        fun g(v: Double) = NutritionUi.grams(v)
        return when (p) {
            is ActionPayload.AddMeal -> "${p.mealType.label} ($day): ${p.description}, ${g(p.proteinG)} g protein, ${p.calories} kcal" +
                (p.carbsG?.let { ", ${g(it)} g karbonhidrat" } ?: "") + (p.fatG?.let { ", ${g(it)} g yağ" } ?: "")
            is ActionPayload.AddWater -> "Su ($day): ${p.ml} ml"
            is ActionPayload.LogWeight -> "Kilo ($day): ${g(p.kg)} kg"
            is ActionPayload.LogActivity -> "${activityLabel(p.activity)} ($day): ${p.minutes} dk" +
                (p.distanceM?.let { ", $it m" } ?: "") + (if (p.note.isNotEmpty()) ", ${p.note}" else "")
        }
    }

    fun activityLabel(t: ActivityType) = when (t) { ActivityType.YUZME -> "Yüzme"; ActivityType.SAUNA -> "Sauna"; ActivityType.BUHAR -> "Buhar" }
}
