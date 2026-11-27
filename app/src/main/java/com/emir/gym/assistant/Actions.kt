package com.emir.gym.assistant

import com.emir.gym.data.ActivityLog
import com.emir.gym.data.BodyWeight
import com.emir.gym.data.Meal
import com.emir.gym.data.WaterLog
import java.time.LocalDate

sealed interface ConfirmResult {
    data class Confirmed(val actionId: Long, val summary: String, val ref: String) : ConfirmResult
    /** Daha once onaylanmis (cift dokunus / tekrar): yeni kayit yazilmadi. */
    data class AlreadyConfirmed(val actionId: Long) : ConfirmResult
    data class AlreadyRejected(val actionId: Long) : ConfirmResult
    object NotFound : ConfirmResult
    data class Failed(val message: String) : ConfirmResult
}

/** Onay bekleyen onerilerin deposu. confirm: durum gecisi ve kayit yazimi tek atomik islemdir. */
interface ActionStore {
    suspend fun insert(payload: ActionPayload, now: Long): Long
    /** [apply] yalnizca PENDING -> CONFIRMED gecisini kazanan cagrida, ayni islem icinde calisir; hata atarsa gecis geri alinir. */
    suspend fun confirm(id: Long, apply: suspend (ActionPayload) -> String): ConfirmResult
    suspend fun reject(id: Long): Boolean
}

/** Dogrulanmis oneriyi mevcut tablolara yazar; kaydin referansini doner. */
fun interface RecordWriter {
    suspend fun write(p: ActionPayload): String
}

class TrackingRecordWriter(private val dao: com.emir.gym.data.TrackingDao) : RecordWriter {
    override suspend fun write(p: ActionPayload): String = when (p) {
        is ActionPayload.AddMeal -> "meal:" + dao.upsertMeal(
            Meal(0, p.epochDay, p.mealType, p.description, p.proteinG, p.calories, p.carbsG, p.fatG)
        )
        is ActionPayload.AddWater -> "water:" + dao.upsertWater(WaterLog(0, p.epochDay, p.ml))
        is ActionPayload.LogWeight -> { dao.upsertBodyWeight(BodyWeight(p.epochDay, p.kg)); "weight:${p.epochDay}" }
        is ActionPayload.LogActivity -> "activity:" + dao.upsertActivity(
            ActivityLog(0, p.epochDay, p.activity, p.minutes, p.distanceM, p.note)
        )
    }
}

data class ToolOutcome(val text: String, val isError: Boolean, val actionId: Long? = null, val written: Boolean = false)

/**
 * Arac yurutucu. Yazma araclari varsayilan olarak ONERI uretir (veritabanina yazilmaz);
 * 'otomatik ekle' aciksa ayni onay yolundan hemen yazilir. Okuma araclari mevcut mantiktan beslenir.
 */
class ToolExecutor(
    private val store: ActionStore,
    private val writer: RecordWriter,
    private val readData: suspend () -> AssistantData,
    private val autoAdd: () -> Boolean,
    private val today: () -> LocalDate = { LocalDate.now() },
    private val now: () -> Long = System::currentTimeMillis,
) {
    suspend fun run(call: ToolCall): ToolOutcome {
        val args = ToolValidator.parseArgs(call.argsJson) ?: return ToolOutcome("Hata: araç argümanları geçerli bir JSON nesnesi değil.", true)
        val t = today()
        return when (call.name) {
            ToolDefs.GET_TODAY_PLAN -> ToolOutcome(AssistantContext.todayPlan(t, readData()), false)
            ToolDefs.GET_DAY_SUMMARY -> {
                val n = args.get("date")
                val day = if (n == null || n.isNull || (n.isTextual && n.asText().isBlank())) t else try {
                    LocalDate.parse(n.asText().trim())
                } catch (e: Exception) { return ToolOutcome("Hata: tarih YYYY-MM-DD biçiminde olmalı.", true) }
                if (day.isAfter(t)) ToolOutcome("Hata: gelecek tarih için özet yok.", true)
                else ToolOutcome(AssistantContext.daySummary(day, readData()), false)
            }
            in ToolDefs.WRITE_TOOLS -> write(call.name, args, t)
            else -> ToolOutcome("Hata: bilinmeyen araç.", true)
        }
    }

    private suspend fun write(name: String, args: com.fasterxml.jackson.databind.JsonNode, today: LocalDate): ToolOutcome {
        val payload = when (val v = ToolValidator.validate(name, args, today)) {
            is Validation.Invalid -> return ToolOutcome(v.message, true)
            is Validation.Ok -> v.payload
        }
        val summary = ToolValidator.describe(payload)
        val id = try { store.insert(payload, now()) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
            return ToolOutcome("Hata: öneri oluşturulamadı.", true)
        }
        if (!autoAdd()) {
            return ToolOutcome("Öneri kullanıcı onayına sunuldu (henüz kaydedilmedi): $summary", false, id)
        }
        return when (val r = confirm(id)) {
            is ConfirmResult.Confirmed -> ToolOutcome("Eklendi: $summary", false, id, written = true)
            is ConfirmResult.AlreadyConfirmed -> ToolOutcome("Eklendi: $summary", false, id, written = true)
            else -> ToolOutcome("Otomatik eklenemedi; öneri kullanıcı onayına sunuldu (henüz kaydedilmedi): $summary", false, id)
        }
    }

    suspend fun confirm(id: Long): ConfirmResult = try {
        store.confirm(id) { p -> writer.write(p) }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        ConfirmResult.Failed("Kaydedilemedi.")
    }
}
