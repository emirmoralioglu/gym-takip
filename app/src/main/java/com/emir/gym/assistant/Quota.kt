package com.emir.gym.assistant

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock

/**
 * Bir istegin kaydi: duvar saati (epoch ms) ve cihazin acilistan beri gecen suresi (elapsedRealtime).
 * Duvar saati geri alinabilir / sicrayabilir; elapsed ayni acilis icinde guvenilirdir. Yas hesabinda
 * IKISININ KUCUGU alinir (kayit daha uzun yasar): sayac asla eksik saymaz.
 */
data class Stamp(val wall: Long, val elapsed: Long)

/**
 * Ucretsiz katman korumasi: KAYAN 24 SAAT penceresi. Saglayici kotasi hangi saatte sifirlanirsa
 * sifirlansin, herhangi bir saglayici gunu en cok 24 saatlik bir pencerenin icindedir; bu yuzden son 24
 * saatte atilan istek sayisi floor(sinir x 0.99)'u asamaz. Tam sayi aritmetigi.
 */
object QuotaLogic {
    const val WINDOW_MS = 24L * 60 * 60 * 1000

    /** Sinirsizken de gosterge icin tutulan en cok kayit. */
    const val MAX_KEPT = 2000

    /** Efektif ust sinir: floor(limit*0.99); limit null = sinirsiz. */
    fun cap(limit: Int?): Int? = limit?.let { (it.toLong() * 99L / 100L).toInt() }

    /** Saat geri alinmissa 'simdi' en son kayitli damgadan kucuk olamaz. */
    fun effectiveNow(stamps: List<Stamp>, nowWall: Long): Long = maxOf(nowWall, stamps.maxOfOrNull { it.wall } ?: nowWall)

    private fun age(s: Stamp, effNow: Long, nowElapsed: Long): Long {
        val w = maxOf(0L, effNow - s.wall)
        val e = nowElapsed - s.elapsed
        return if (e >= 0) minOf(w, e) else w // e<0: cihaz yeniden acilmis, elapsed gecersiz
    }

    /** Pencerede (son 24 saat) kalan kayitlar; eskileri budanir. */
    fun live(stamps: List<Stamp>, nowWall: Long, nowElapsed: Long): List<Stamp> {
        val eff = effectiveNow(stamps, nowWall)
        return stamps.filter { age(it, eff, nowElapsed) < WINDOW_MS }
    }

    /** Istek atilabilir mi; atilabilirse yeni kayit eklenmis liste doner. */
    fun tryAcquire(stamps: List<Stamp>, nowWall: Long, nowElapsed: Long, limit: Int?): Pair<Boolean, List<Stamp>> {
        val live = live(stamps, nowWall, nowElapsed)
        val cap = cap(limit)
        if (cap != null && live.size >= cap) return false to live
        val eff = effectiveNow(live, nowWall)
        return true to (live + Stamp(eff, nowElapsed)).takeLast(MAX_KEPT)
    }

    /** Pencerede kalan istek hakki; sinirsizsa null. */
    fun remaining(stamps: List<Stamp>, nowWall: Long, nowElapsed: Long, limit: Int?): Int? =
        cap(limit)?.let { maxOf(0, it - live(stamps, nowWall, nowElapsed).size) }

    /** Bir kaydin pencereden dusecegi duvar saati zamani (epoch ms), 'simdi'ye gore. */
    private fun expiries(stamps: List<Stamp>, nowWall: Long, nowElapsed: Long): List<Long> {
        val eff = effectiveNow(stamps, nowWall)
        return live(stamps, nowWall, nowElapsed).map { eff + (WINDOW_MS - age(it, eff, nowElapsed)) }.sorted()
    }

    /** En eski istegin dusecegi zaman (epoch ms); kayit yoksa null. */
    fun oldestExpiry(stamps: List<Stamp>, nowWall: Long, nowElapsed: Long): Long? =
        expiries(stamps, nowWall, nowElapsed).firstOrNull()

    /** Sinira ulasilmissa yeniden istek atilabilecegi zaman (epoch ms); su an atilabiliyorsa (ya da sinir kullanilamazsa) null. */
    fun reopensAt(stamps: List<Stamp>, nowWall: Long, nowElapsed: Long, limit: Int?): Long? {
        val cap = cap(limit) ?: return null
        val ex = expiries(stamps, nowWall, nowElapsed)
        if (ex.size < cap) return null
        return ex.getOrNull(ex.size - cap)
    }

    fun encode(stamps: List<Stamp>): String = stamps.joinToString(",") { "${it.wall}:${it.elapsed}" }

    fun decode(s: String?): List<Stamp> = s.orEmpty().split(',').mapNotNull {
        val p = it.split(':')
        val w = p.getOrNull(0)?.toLongOrNull(); val e = p.getOrNull(1)?.toLongOrNull()
        if (p.size == 2 && w != null && e != null) Stamp(w, e) else null
    }
}

class QuotaClock(val wall: () -> Long, val elapsed: () -> Long) {
    companion object {
        /** Uretim: duvar saati + elapsedRealtime (uyku dahil). */
        fun system() = QuotaClock(System::currentTimeMillis) { SystemClock.elapsedRealtime() }
        /** Saf JVM testleri icin. */
        fun jvm() = QuotaClock(System::currentTimeMillis) { System.nanoTime() / 1_000_000 }
    }
}

interface UsageStore {
    /** Atomik: izin varsa kaydi KALICI yazar ve true doner. Yazilamazsa false (az sayma yonunde degil, reddet). */
    fun tryAcquire(provider: ProviderId, limit: Int?): Boolean
    /** Son 24 saatteki istek sayisi. */
    fun count(provider: ProviderId): Int
    /** Kalan istek hakki (sinirsizsa null). */
    fun remaining(provider: ProviderId, limit: Int?): Int?
    /** Sinira ulasilmissa yeniden acilma zamani (epoch ms); degilse null. */
    fun reopensAt(provider: ProviderId, limit: Int?): Long?
    /** En eski isteginin dusecegi zaman (epoch ms). */
    fun oldestExpiry(provider: ProviderId): Long?
}

/** Kalici depo soyutlamasi (test icin sahte yazilabilir). write false = yazilamadi. */
interface StampStorage {
    fun read(provider: ProviderId): String?
    fun write(provider: ProviderId, value: String): Boolean
}

class PrefsStampStorage(context: Context) : StampStorage {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("assistant_usage", Context.MODE_PRIVATE)
    override fun read(provider: ProviderId): String? = prefs.getString("stamps_${provider.name}", null)
    override fun write(provider: ProviderId, value: String): Boolean = prefs.edit().putString("stamps_${provider.name}", value).commit()
}

class MemoryStampStorage : StampStorage {
    private val map = HashMap<ProviderId, String>()
    override fun read(provider: ProviderId): String? = map[provider]
    override fun write(provider: ProviderId, value: String): Boolean { map[provider] = value; return true }
}

class StoredUsageStore(private val storage: StampStorage, private val clock: QuotaClock = QuotaClock.system()) : UsageStore {
    private fun stamps(p: ProviderId) = QuotaLogic.decode(storage.read(p))

    @Synchronized
    override fun tryAcquire(provider: ProviderId, limit: Int?): Boolean {
        val (ok, next) = QuotaLogic.tryAcquire(stamps(provider), clock.wall(), clock.elapsed(), limit)
        if (!ok) return false
        // Sayac diske yazilamazsa istek ATILMAZ (az sayma ucretsiz sinirin asilmasina yol acar).
        return storage.write(provider, QuotaLogic.encode(next))
    }

    @Synchronized override fun count(provider: ProviderId): Int = QuotaLogic.live(stamps(provider), clock.wall(), clock.elapsed()).size
    @Synchronized override fun remaining(provider: ProviderId, limit: Int?): Int? = QuotaLogic.remaining(stamps(provider), clock.wall(), clock.elapsed(), limit)
    @Synchronized override fun reopensAt(provider: ProviderId, limit: Int?): Long? = QuotaLogic.reopensAt(stamps(provider), clock.wall(), clock.elapsed(), limit)
    @Synchronized override fun oldestExpiry(provider: ProviderId): Long? = QuotaLogic.oldestExpiry(stamps(provider), clock.wall(), clock.elapsed())
}

fun PrefsUsageStore(context: Context): UsageStore = StoredUsageStore(PrefsStampStorage(context), QuotaClock.system())

/** Testler icin bellek ici sayac. */
fun MemoryUsageStore(clock: QuotaClock = QuotaClock.jvm()): UsageStore = StoredUsageStore(MemoryStampStorage(), clock)
