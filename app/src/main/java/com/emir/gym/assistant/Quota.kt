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

    /**
     * Yas = duvar ve elapsed yaslarinin kucugu. Gelecege ait duvar damgasi (saat kazara ileri kurulup geri alinmis)
     * 'simdi' sayilir (yas 0): kilitlenme en cok 24 saat surer, ve yas kucuk hesaplandigi icin guvenli yon bozulmaz.
     */
    private fun age(s: Stamp, nowWall: Long, nowElapsed: Long): Long {
        val w = maxOf(0L, nowWall - s.wall)
        val e = nowElapsed - s.elapsed
        return if (e >= 0) minOf(w, e) else w // e<0: cihaz yeniden acilmis, elapsed gecersiz
    }

    /** Pencerede (son 24 saat) kalan kayitlar; eskileri budanir. */
    fun live(stamps: List<Stamp>, nowWall: Long, nowElapsed: Long): List<Stamp> =
        stamps.filter { age(it, nowWall, nowElapsed) < WINDOW_MS }.map { s ->
            val w = maxOf(0L, nowWall - s.wall)
            when {
                // Gelecek damgasi: 'simdi' say (yas 0), elapsed'i da simdiye hizala.
                s.wall > nowWall -> Stamp(nowWall, nowElapsed)
                // Yeniden acilis (elapsed gecersiz): elapsed'i duvar yasina gore yeniden kur; yoksa eski elapsed sonradan
                // 'pozitif' gorunup yasi yapay olarak kucultur ve kilidi 24 saatten uzatir.
                nowElapsed - s.elapsed < 0 -> Stamp(s.wall, nowElapsed - w)
                else -> s
            }
        }

    /** Istek atilabilir mi; atilabilirse yeni kayit eklenmis liste doner. */
    fun tryAcquire(stamps: List<Stamp>, nowWall: Long, nowElapsed: Long, limit: Int?): Pair<Boolean, List<Stamp>> {
        val live = live(stamps, nowWall, nowElapsed)
        val cap = cap(limit)
        if (cap != null && live.size >= cap) return false to live
        val next = live + Stamp(nowWall, nowElapsed)
        // Sinirliyken KIRPMA YOK (24 saatten eski budama yeterli; en cok cap kayit). Yalniz sinirsizken gosterge icin MAX_KEPT.
        return true to (if (cap == null) next.takeLast(MAX_KEPT) else next)
    }

    /** Pencerede kalan istek hakki; sinirsizsa null. */
    fun remaining(stamps: List<Stamp>, nowWall: Long, nowElapsed: Long, limit: Int?): Int? =
        cap(limit)?.let { maxOf(0, it - live(stamps, nowWall, nowElapsed).size) }

    /** Bir kaydin pencereden dusecegi duvar saati zamani (epoch ms), 'simdi'ye gore. */
    private fun expiries(stamps: List<Stamp>, nowWall: Long, nowElapsed: Long): List<Long> {
        return live(stamps, nowWall, nowElapsed).map { nowWall + (WINDOW_MS - age(it, nowWall, nowElapsed)) }.sorted()
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

    fun decode(s: String?): List<Stamp> = decodeChecked(s).first

    /** (kayitlar, bozuk mu). Bos/yok = bozuk degil; cozulemeyen herhangi bir parca = bozuk. */
    fun decodeChecked(s: String?): Pair<List<Stamp>, Boolean> {
        if (s.isNullOrEmpty()) return emptyList<Stamp>() to false
        var bad = false
        val list = s.split(',').mapNotNull {
            val p = it.split(':')
            val w = p.getOrNull(0)?.toLongOrNull(); val e = p.getOrNull(1)?.toLongOrNull()
            if (p.size == 2 && w != null && e != null) Stamp(w, e) else { bad = true; null }
        }
        return list to bad
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
    /**
     * Kayitlari okur. Bozuk kayit (cozulemeyen parca): sayac SIFIRLANMAZ; guvenli yon olarak sinirli saglayicida
     * 'simdi' damgali cap kadar istek yapilmis sayilir (24 saat sonra kendiliginden acilir) ve duzeltilmis kayit yazilir.
     * Gelecek damgalari kalici olarak 'simdi'ye sikistirilip yazilir.
     */
    private fun stamps(p: ProviderId, limit: Int?, persist: Boolean): List<Stamp> {
        val (list, corrupt) = QuotaLogic.decodeChecked(storage.read(p))
        val w = clock.wall(); val e = clock.elapsed()
        if (corrupt) {
            val synthetic = QuotaLogic.cap(limit)?.let { c -> List(c) { Stamp(w, e) } } ?: emptyList()
            if (persist) storage.write(p, QuotaLogic.encode(synthetic))
            return synthetic
        }
        val normalized = QuotaLogic.live(list, w, e)
        // Gelecek damgasi / yeniden acilis duzeltmesi kalici yazilir (budama tek basina yazdirmaz).
        if (persist && normalized.size == list.size && normalized != list) storage.write(p, QuotaLogic.encode(normalized))
        return normalized
    }

    @Synchronized
    override fun tryAcquire(provider: ProviderId, limit: Int?): Boolean {
        val (ok, next) = QuotaLogic.tryAcquire(stamps(provider, limit, true), clock.wall(), clock.elapsed(), limit)
        if (!ok) return false
        // Sayac diske yazilamazsa istek ATILMAZ (az sayma ucretsiz sinirin asilmasina yol acar).
        return storage.write(provider, QuotaLogic.encode(next))
    }

    @Synchronized override fun count(provider: ProviderId): Int = QuotaLogic.live(stamps(provider, null, false), clock.wall(), clock.elapsed()).size
    @Synchronized override fun remaining(provider: ProviderId, limit: Int?): Int? = QuotaLogic.remaining(stamps(provider, limit, false), clock.wall(), clock.elapsed(), limit)
    @Synchronized override fun reopensAt(provider: ProviderId, limit: Int?): Long? = QuotaLogic.reopensAt(stamps(provider, limit, false), clock.wall(), clock.elapsed(), limit)
    @Synchronized override fun oldestExpiry(provider: ProviderId): Long? = QuotaLogic.oldestExpiry(stamps(provider, null, false), clock.wall(), clock.elapsed())
}

fun PrefsUsageStore(context: Context): UsageStore = StoredUsageStore(PrefsStampStorage(context), QuotaClock.system())

/** Testler icin bellek ici sayac. */
fun MemoryUsageStore(clock: QuotaClock = QuotaClock.jvm()): UsageStore = StoredUsageStore(MemoryStampStorage(), clock)
