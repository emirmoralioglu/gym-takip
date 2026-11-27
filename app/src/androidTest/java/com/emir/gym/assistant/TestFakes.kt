package com.emir.gym.assistant

import com.emir.gym.data.NutritionGoals
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FakeActionStore : ActionStore {
    data class Row(val payload: ActionPayload, var status: String = "PENDING", var ref: String? = null)
    val rows = LinkedHashMap<Long, Row>()
    private val mutex = Mutex()
    private var next = 1L

    override suspend fun insert(payload: ActionPayload, now: Long): Long = mutex.withLock { val id = next++; rows[id] = Row(payload); id }

    override suspend fun confirm(id: Long, apply: suspend (ActionPayload) -> String): ConfirmResult = mutex.withLock {
        val r = rows[id] ?: return ConfirmResult.NotFound
        if (r.status != "PENDING") return ConfirmResult.AlreadyConfirmed(id)
        r.status = "CONFIRMED"
        r.ref = apply(r.payload)
        ConfirmResult.Confirmed(id, ToolValidator.describe(r.payload), r.ref!!)
    }

    override suspend fun reject(id: Long): Boolean = false
}

class RecordingWriter : RecordWriter {
    val written = ArrayList<ActionPayload>()
    override suspend fun write(p: ActionPayload): String { written += p; return "rec:${written.size}" }
}

class FakeChatStore : ChatStore {
    val messages = ArrayList<ChatMessage>()
    override suspend fun append(m: ChatMessage): Long { messages += m.copy(id = messages.size + 1L); return messages.size.toLong() }
    override suspend fun all(): List<ChatMessage> = messages.toList()
    override suspend fun clear() { messages.clear() }
}

fun emptyData() = AssistantData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), NutritionGoals())
