package com.emir.gym.assistant

import com.emir.gym.assistant.AssistantError.Kind
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Tum saglayici hatalari tek AssistantError turune cevrilir. Ham govde, anahtar ve yigin izi
 * hicbir zaman mesaja girmez; govde yalnizca siniflandirma icin (kucuk harfe cevrilip) taranir.
 */
object ErrorMapper {
    fun noKey() = AssistantError(Kind.NO_KEY, "API anahtarı girilmemiş. Ayarlardan anahtarını ekle.")
    fun badConfig(msg: String) = AssistantError(Kind.BAD_CONFIG, msg)
    fun daily() = AssistantError(Kind.DAILY_LIMIT, "Günlük istek sınırına ulaşıldı. Yarın tekrar dene ya da ayarlardan sınırı değiştir.")
    fun refused() = AssistantError(Kind.REFUSED, "Model bu isteği güvenlik nedeniyle yanıtlamadı. İsteği değiştirip tekrar dene.")
    fun truncated() = AssistantError(Kind.TRUNCATED, "Yanıt uzunluk sınırında kesildi. Daha kısa bir istek dene.")
    fun toolSchema() = AssistantError(Kind.TOOL_SCHEMA, "Araç tanımı bu model tarafından kabul edilmedi. Ayarlardan araç desteğini kapatıp dene.")
    fun invalid() = AssistantError(Kind.INVALID_RESPONSE, "Sağlayıcıdan beklenmeyen bir yanıt geldi.")
    fun loopLimit() = AssistantError(Kind.LOOP_LIMIT, "Asistan çok fazla adım denedi ve durduruldu. İsteği daha açık yazıp tekrar dene.")
    fun imageUnsupported() = AssistantError(Kind.IMAGE_UNSUPPORTED, "Seçili model görsel desteklemiyor. Ayarlardan görsel desteğini aç ya da görselsiz yaz.")
    fun busy() = AssistantError(Kind.BUSY, "Önceki mesaj hâlâ gönderiliyor.")

    /**
     * @param status HTTP durum kodu
     * @param retryAfter Retry-After basligi (saniye); yoksa null
     * @param body govde (yalniz siniflandirma icin)
     */
    fun fromHttp(status: Int, retryAfter: String? = null, body: String? = null): AssistantError {
        val b = body?.lowercase().orEmpty()
        val wait = retryAfter?.trim()?.toDoubleOrNull()?.takeIf { it >= 0 && it.isFinite() }?.let { Math.ceil(it).toInt() }
        return when {
            status == 401 || status == 403 ->
                AssistantError(Kind.UNAUTHORIZED, "API anahtarı geçersiz ya da bu işlem için yetkisiz. Anahtarı kontrol et.")
            status == 402 ->
                AssistantError(Kind.BILLING, "Hesapta ödeme ya da bakiye sorunu var. Sağlayıcı hesabını kontrol et.")
            status == 404 ->
                AssistantError(Kind.MODEL_NOT_FOUND, "Model bulunamadı. Ayarlardaki model adını kontrol et.")
            status == 413 ->
                AssistantError(Kind.TOO_LARGE, "İstek çok büyük. Görseli küçült ya da sohbeti temizle.")
            status == 429 -> AssistantError(
                Kind.RATE_LIMITED,
                if (wait != null) "Sağlayıcı istek sınırına ulaştı. $wait saniye sonra tekrar dene."
                else "Sağlayıcı istek sınırına ulaştı. Biraz bekleyip tekrar dene.",
                wait,
            )
            status in 500..599 ->
                AssistantError(Kind.SERVER, "Sağlayıcıda geçici bir sorun var. Biraz sonra tekrar dene.")
            status == 400 || status == 422 -> when {
                "prompt is too long" in b || "context" in b && ("length" in b || "window" in b) ->
                    AssistantError(Kind.CONTEXT_TOO_LONG, "Sohbet çok uzadı. Sohbeti temizleyip tekrar dene.")
                "tool" in b || "function" in b || "schema" in b ->
                    toolSchema()
                else -> AssistantError(Kind.BAD_REQUEST, "İstek sağlayıcı tarafından reddedildi. Model adını ve ayarları kontrol et.")
            }
            else -> AssistantError(Kind.UNKNOWN, "Beklenmeyen bir hata oluştu (kod $status).")
        }
    }

    /** Ag ve diger istisnalar. Istisna metni kullaniciya gosterilmez. */
    fun fromThrowable(e: Throwable): AssistantError {
        var t: Throwable? = e
        var depth = 0
        while (t != null && depth++ < 6) {
            when (t) {
                is SocketTimeoutException -> return timeout()
                is UnknownHostException, is ConnectException -> return network()
                is SSLException -> return AssistantError(Kind.NETWORK, "Güvenli bağlantı kurulamadı. Adresi ve saat/tarih ayarını kontrol et.")
            }
            if (t.javaClass.simpleName.contains("Timeout", ignoreCase = true)) return timeout()
            t = t.cause
        }
        if (e is IOException) return network()
        return AssistantError(Kind.UNKNOWN, "Beklenmeyen bir hata oluştu.")
    }

    private fun timeout() = AssistantError(Kind.TIMEOUT, "Yanıt zamanında gelmedi. Bağlantını kontrol edip tekrar dene.")
    private fun network() = AssistantError(Kind.NETWORK, "Bağlantı kurulamadı. İnternetini kontrol et.")
}
