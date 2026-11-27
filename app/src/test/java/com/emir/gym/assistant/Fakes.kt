package com.emir.gym.assistant

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class Recorded(val method: String, val path: String, val headers: Map<String, String>, val body: String)

/** Yerel sahte sunucu (JVM, com.sun.net.httpserver). Gercek aga cikilmaz. */
class FakeServer(private val handler: (HttpExchange, Recorded) -> Unit) : AutoCloseable {
    val requests = CopyOnWriteArrayList<Recorded>()
    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    val url: String get() = "http://127.0.0.1:${server.address.port}"

    init {
        server.createContext("/") { ex ->
            val body = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            val headers = ex.requestHeaders.entries.associate { it.key.lowercase() to it.value.joinToString(",") }
            val rec = Recorded(ex.requestMethod, ex.requestURI.toString(), headers, body)
            requests += rec
            try { handler(ex, rec) } finally { ex.close() }
        }
        server.start()
    }

    override fun close() = server.stop(0)

    companion object {
        fun reply(ex: HttpExchange, status: Int, body: String, headers: Map<String, String> = emptyMap()) {
            val bytes = body.toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "application/json")
            headers.forEach { (k, v) -> ex.responseHeaders.add(k, v) }
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }

        /** Sirayla verilen yanitlari doner (son yanit tekrarlanir). */
        fun sequence(vararg replies: Pair<Int, String>): (HttpExchange, Recorded) -> Unit {
            val i = java.util.concurrent.atomic.AtomicInteger()
            return { ex, _ ->
                val (s, b) = replies[minOf(i.getAndIncrement(), replies.size - 1)]
                reply(ex, s, b)
            }
        }
    }
}

class FakeActionStore : ActionStore {
    data class Row(val payload: ActionPayload, var status: String = "PENDING", var ref: String? = null)
    val rows = LinkedHashMap<Long, Row>()
    private val mutex = Mutex()
    private var next = 1L

    override suspend fun insert(payload: ActionPayload, now: Long): Long = mutex.withLock { val id = next++; rows[id] = Row(payload); id }

    override suspend fun confirm(id: Long, apply: suspend (ActionPayload) -> String): ConfirmResult = mutex.withLock {
        val r = rows[id] ?: return ConfirmResult.NotFound
        when (r.status) {
            "CONFIRMED" -> ConfirmResult.AlreadyConfirmed(id)
            "REJECTED" -> ConfirmResult.AlreadyRejected(id)
            else -> {
                r.status = "CONFIRMED"
                try { r.ref = apply(r.payload) } catch (e: Exception) { r.status = "PENDING"; throw e }
                ConfirmResult.Confirmed(id, ToolValidator.describe(r.payload), r.ref!!)
            }
        }
    }

    override suspend fun reject(id: Long): Boolean = mutex.withLock {
        val r = rows[id] ?: return false
        if (r.status != "PENDING") false else { r.status = "REJECTED"; true }
    }
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

/** Sirayla onceden hazir sonuc veren saglayici; aldigi istekleri kaydeder. */
class ScriptedProvider(
    private val results: List<LlmResult>,
    override val capabilities: Capabilities = Capabilities(supportsImages = true, supportsTools = true),
    override val id: ProviderId = ProviderId.GEMINI,
) : LlmProvider {
    val requests = ArrayList<LlmRequest>()
    override suspend fun complete(req: LlmRequest): LlmResult {
        requests += req
        return results[minOf(requests.size - 1, results.size - 1)]
    }
}

fun emptyData(goals: com.emir.gym.data.NutritionGoals = com.emir.gym.data.NutritionGoals()) =
    AssistantData(emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), goals)
