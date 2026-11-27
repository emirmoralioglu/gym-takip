package com.emir.gym.assistant

import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cihazda (emulator) calisan en kucuk HTTP sunucusu: yalniz 127.0.0.1, her baglantida tek yanit. */
private class TinyServer(private val replies: List<Pair<Int, String>>) : AutoCloseable {
    private val ss = ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"))
    val bodies = java.util.concurrent.CopyOnWriteArrayList<String>()
    val heads = java.util.concurrent.CopyOnWriteArrayList<String>()
    val url get() = "http://127.0.0.1:${ss.localPort}"
    @Volatile private var n = 0

    private val t = thread(isDaemon = true) {
        while (!ss.isClosed) {
            val s = try { ss.accept() } catch (e: Exception) { break }
            try { handle(s) } catch (_: Exception) {} finally { runCatching { s.close() } }
        }
    }

    private fun handle(s: Socket) {
        val input = s.getInputStream()
        val head = ByteArrayOutputStream()
        var last4 = 0
        while (true) {
            val b = input.read(); if (b < 0) return
            head.write(b); last4 = (last4 shl 8) or b
            if (last4 == 0x0D0A0D0A) break
        }
        val headText = head.toString("UTF-8")
        val len = Regex("(?i)content-length:\\s*(\\d+)").find(headText)?.groupValues?.get(1)?.toInt() ?: 0
        val body = ByteArray(len); var off = 0
        while (off < len) { val r = input.read(body, off, len - off); if (r < 0) break; off += r }
        heads += headText; bodies += String(body, Charsets.UTF_8)
        val (code, text) = replies[minOf(n++, replies.size - 1)]
        val bytes = text.toByteArray(Charsets.UTF_8)
        s.getOutputStream().apply {
            write("HTTP/1.1 $code X\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
            write(bytes); flush()
        }
    }

    override fun close() { ss.close() }
}

/**
 * Anthropic SDK'sinin (Jackson + OkHttp) Android calisma zamaninda gercekten yuklenip calistigini,
 * ayrica debug cleartext yapilandirmasinin yalniz localhost icin gecerli oldugunu kanitlar.
 * Gercek API'ye cagri yoktur.
 */
class ProviderDeviceTest {
    private val text = """{"id":"msg_1","type":"message","role":"assistant","model":"claude-opus-5-5","content":[{"type":"thinking","thinking":"","signature":"SIG_DEV"},{"type":"text","text":"Merhaba cihaz"}],"stop_reason":"end_turn","stop_sequence":null,"usage":{"input_tokens":3,"output_tokens":2}}"""
    private val tool = """{"id":"msg_2","type":"message","role":"assistant","model":"claude-opus-5-5","content":[{"type":"thinking","thinking":"","signature":"SIG_TOOL_DEV"},{"type":"tool_use","id":"toolu_d1","name":"add_water","input":{"ml":250}}],"stop_reason":"tool_use","stop_sequence":null,"usage":{"input_tokens":3,"output_tokens":2}}"""
    private fun req(model: String) = LlmRequest(model, "SISTEM", listOf(ChatMessage(Role.USER, listOf(Block.Text("selam")))), ToolDefs.all, 1000, "medium")

    @Test fun anthropicSdkRunsOnAndroidAndSendsOpus55Request() = runBlocking {
        TinyServer(listOf(200 to text)).use { s ->
            val r = AnthropicProvider("test-key-123", s.url).complete(req("claude-opus-5-5"))
            assertEquals(r.error?.message, Status.COMPLETED, r.status)
            assertEquals("Merhaba cihaz", r.text)
            assertTrue(r.rawAnthropic!!.contains("SIG_DEV"))
            val b = Js.parse(s.bodies.single())!!
            assertEquals("adaptive", b.get("thinking").get("type").asText())
            assertEquals("medium", b.get("output_config").get("effort").asText())
            assertFalse(b.has("temperature"))
            assertFalse(s.bodies.single().contains("test-key-123"))
        }
    }

    @Test fun anthropicToolLoopOnDevice() = runBlocking {
        val chat = FakeChatStore()
        val store = FakeActionStore()
        TinyServer(listOf(200 to tool, 200 to text)).use { s ->
            val cfg = AssistantConfig(ProviderId.ANTHROPIC, "claude-opus-5-5", "medium", "", false, true, false, null)
            val engine = AssistantEngine(
                chat, ToolExecutor(store, RecordingWriter(), { emptyData() }, { false }), MemoryUsageStore(),
                { "test-key-123" }, { cfg }, { _, k -> AnthropicProvider(k, s.url) }, { _, _ -> "CTX" }, allowLocalCleartext = true,
            )
            val o = engine.send("250 ml su")
            assertTrue(o.error?.message, o.ok)
            assertEquals(1, store.rows.size)
            val second = s.bodies[1]
            assertTrue(second.contains("SIG_TOOL_DEV"))
            assertTrue(second.contains("tool_result"))
        }
    }

    @Test fun anthropicErrorsMapOnDevice() = runBlocking {
        TinyServer(listOf(401 to """{"type":"error","error":{"type":"authentication_error","message":"bad key test-key-123"}}""")).use { s ->
            val r = AnthropicProvider("test-key-123", s.url).complete(req("claude-opus-5-5"))
            assertEquals(AssistantError.Kind.UNAUTHORIZED, r.error!!.kind)
            assertFalse(r.error!!.message.contains("test-key"))
        }
    }

    @Test fun geminiAndOpenAiOverLocalhostCleartext() = runBlocking {
        TinyServer(listOf(200 to """{"candidates":[{"content":{"parts":[{"text":"g"}]},"finishReason":"STOP"}]}""")).use { s ->
            val r = GeminiProvider("test-key-123", s.url).complete(req("gemini-2.5-flash"))
            assertEquals("g", r.text)
            assertTrue(s.heads.single().lowercase().contains("x-goog-api-key: test-key-123"))
        }
        TinyServer(listOf(200 to """{"choices":[{"message":{"content":"o"},"finish_reason":"stop"}]}""")).use { s ->
            val r = OpenAiCompatProvider("test-key-123", s.url, false, true, allowLocalCleartext = true).complete(req("m"))
            assertEquals("o", r.text)
        }
    }

    @Test fun cleartextToNonLocalHostIsBlocked() = runBlocking {
        // Politika (UrlPolicy) ve ag guvenlik yapilandirmasi birlikte: uzak HTTP istegi hic atilmaz.
        val r = OpenAiCompatProvider("test-key-123", "http://example.com/v1", false, true, allowLocalCleartext = true).complete(req("m"))
        assertEquals(AssistantError.Kind.BAD_CONFIG, r.error!!.kind)
        assertNotNull(r.error)
    }
}
