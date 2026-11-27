package com.emir.gym.assistant

import androidx.room.withTransaction
import com.emir.gym.data.AppDatabase
import com.emir.gym.data.ChatDao
import com.emir.gym.data.NutritionGoals
import com.emir.gym.data.PendingActionEntity
import com.emir.gym.data.PendingStatus
import java.io.File
import kotlinx.coroutines.flow.first

/** Sohbet gecmisi deposu (engine ve testler icin soyut). */
interface ChatStore {
    suspend fun append(m: ChatMessage): Long
    suspend fun all(): List<ChatMessage>
    suspend fun clear()
}

class RoomChatStore(private val dao: ChatDao) : ChatStore {
    override suspend fun append(m: ChatMessage): Long = dao.insertMessage(
        com.emir.gym.data.ChatMessageEntity(
            createdAt = if (m.createdAt > 0) m.createdAt else System.currentTimeMillis(),
            role = m.role.name,
            contentJson = ChatCodec.encode(m),
            imagePath = m.blocks.filterIsInstance<Block.Image>().firstOrNull()?.path,
            provider = m.provider,
            model = m.model,
        )
    )

    override suspend fun all(): List<ChatMessage> = dao.allMessages().map { toMessage(it) }

    override suspend fun clear() { dao.clearMessages(); dao.clearActions() }

    companion object {
        fun toMessage(e: com.emir.gym.data.ChatMessageEntity) = ChatCodec.decode(
            e.contentJson, if (e.role == Role.ASSISTANT.name) Role.ASSISTANT else Role.USER, e.provider, e.model, e.id, e.createdAt,
        )
    }
}

class RoomActionStore(private val db: AppDatabase) : ActionStore {
    private val dao get() = db.chatDao()

    override suspend fun insert(payload: ActionPayload, now: Long): Long =
        dao.insertAction(PendingActionEntity(createdAt = now, type = payload.type, payloadJson = ToolValidator.encode(payload)))

    override suspend fun confirm(id: Long, apply: suspend (ActionPayload) -> String): ConfirmResult = db.withTransaction {
        val a = dao.action(id) ?: return@withTransaction ConfirmResult.NotFound
        when (a.status) {
            PendingStatus.CONFIRMED -> ConfirmResult.AlreadyConfirmed(id)
            PendingStatus.REJECTED -> ConfirmResult.AlreadyRejected(id)
            PendingStatus.PENDING -> {
                val payload = ToolValidator.decode(a.type, a.payloadJson)
                    ?: return@withTransaction ConfirmResult.Failed("Öneri okunamadı.")
                // Yalniz PENDING -> CONFIRMED gecisini kazanan cagri yazar; kayit hata atarsa islem geri alinir.
                if (dao.claim(id) != 1) return@withTransaction ConfirmResult.AlreadyConfirmed(id)
                val ref = apply(payload)
                dao.setResultRef(id, ref)
                ConfirmResult.Confirmed(id, ToolValidator.describe(payload), ref)
            }
        }
    }

    override suspend fun reject(id: Long): Boolean = dao.reject(id) == 1
}

/** Asistanin veritabani katmani: sohbet, oneriler, okuma verisi. */
class AssistantRepository(private val db: AppDatabase, private val goals: () -> NutritionGoals) {
    val chat: ChatStore = RoomChatStore(db.chatDao())
    val actions: ActionStore = RoomActionStore(db)
    private val writer = TrackingRecordWriter(db.trackingDao())

    fun observeMessages() = db.chatDao().observeMessages()
    fun observeActions() = db.chatDao().observeActions()

    suspend fun readData(): AssistantData {
        val tracking = db.trackingDao()
        val plan = db.planDao()
        return AssistantData(
            planDays = plan.observeDays().first(),
            planExercises = plan.observeExercises().first(),
            entries = db.workoutDao().observeAll().first(),
            activities = tracking.observeActivities().first(),
            meals = tracking.observeMeals().first(),
            water = tracking.observeWater().first(),
            weights = tracking.observeBodyWeights().first(),
            goals = goals(),
        )
    }

    /** Onay: oneriyi mevcut tablolara yazar. Ayni oneri iki kez uygulanmaz (cift dokunus guvenli). */
    suspend fun confirm(actionId: Long): ConfirmResult = try {
        actions.confirm(actionId) { writer.write(it) }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        ConfirmResult.Failed("Kaydedilemedi.")
    }

    suspend fun reject(actionId: Long): Boolean = actions.reject(actionId)

    /** Sohbeti temizle: mesajlar, oneriler ve gorsel dosyalari silinir. */
    suspend fun clearChat(imageDir: File?) {
        chat.clear()
        imageDir?.deleteRecursively()
    }
}
