package com.emir.gym.assistant

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/** Adres kurali: yalniz HTTPS. Yerel test icin (yalniz debug) localhost/10.0.2.2 HTTP'ye izin verilebilir. */
object UrlPolicy {
    private val LOCAL_HOSTS = setOf("localhost", "127.0.0.1", "10.0.2.2")

    /** @return null = gecerli; aksi halde Turkce hata mesaji. */
    fun check(url: String, allowLocalCleartext: Boolean): String? {
        val u = try { URI(url.trim()) } catch (e: Exception) { return "Adres geçerli değil." }
        val host = u.host?.lowercase() ?: return "Adres geçerli değil."
        if (u.userInfo != null) return "Adres kullanıcı bilgisi içeremez."
        return when (u.scheme?.lowercase()) {
            "https" -> null
            "http" -> if (allowLocalCleartext && host in LOCAL_HOSTS) null else "Yalnızca https:// adresleri kabul edilir."
            else -> "Yalnızca https:// adresleri kabul edilir."
        }
    }

    /** Sondaki '/' atilir. */
    fun normalize(url: String) = url.trim().trimEnd('/')
}

data class HttpResponse(val status: Int, val body: String, val retryAfter: String?)

/**
 * Ham HTTPS cagrisi (HttpURLConnection; ek bagimlilik yok). Anahtar yalnizca basliklarda gonderilir,
 * URL'ye yazilmaz ve hicbir yerde loglanmaz. Zaman asimlari: baglanti 20 sn, okuma 90 sn.
 */
object Http {
    const val CONNECT_MS = 20_000
    const val READ_MS = 90_000
    private const val MAX_BODY_BYTES = 8 * 1024 * 1024

    fun postJson(
        url: String, headers: Map<String, String>, body: String,
        connectMs: Int = CONNECT_MS, readMs: Int = READ_MS,
    ): HttpResponse {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = connectMs
            conn.readTimeout = readMs
            conn.instanceFollowRedirects = false
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> conn.setRequestProperty(k, v) }
            val bytes = body.toByteArray(Charsets.UTF_8)
            // Sabit uzunluklu akis: HttpURLConnection bu modda POST yonlendirmesini (ve govdeyi yeniden gondermeyi) izleyemez;
            // instanceFollowRedirects=false ile birlikte anahtar basligi hicbir sekilde ikinci hosta gitmez.
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else (conn.errorStream)
            val text = stream?.use { s ->
                val buf = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val n = s.read(chunk)
                    if (n < 0) break
                    if (buf.size() + n > MAX_BODY_BYTES) throw java.io.IOException("response too large")
                    buf.write(chunk, 0, n)
                }
                buf.toString("UTF-8")
            }.orEmpty()
            return HttpResponse(status, text, conn.getHeaderField("Retry-After"))
        } finally {
            conn.disconnect()
        }
    }
}
