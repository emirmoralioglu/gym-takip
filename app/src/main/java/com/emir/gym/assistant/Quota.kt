package com.emir.gym.assistant

import android.content.Context
import android.content.SharedPreferences

data class QuotaState(val day: Long, val count: Int)

/**
 * Ucretsiz katman korumasi: gunluk istek sayaci. Kullanici sinir girdiyse sayac
 * floor(sinir x 0.99) degerine ulasinca istek ATILMAZ. Tam sayi aritmetigi (0.99 kayan nokta hatasi yok).
 */
object QuotaLogic {
    /** Efektif ust sinir: floor(limit*0.99); limit null = sinirsiz. */
    fun cap(limit: Int?): Int? = limit?.let { (it.toLong() * 99L / 100L).toInt() }

    /** Gun degistiyse sayac sifirlanir. */
    fun roll(state: QuotaState?, today: Long): QuotaState =
        if (state == null || state.day != today) QuotaState(today, 0) else state

    /** Istek atilabilir mi; atilabilirse sayac artmis yeni durum doner. */
    fun tryAcquire(state: QuotaState?, today: Long, limit: Int?): Pair<Boolean, QuotaState> {
        val s = roll(state, today)
        val cap = cap(limit) ?: return true to s.copy(count = s.count + 1)
        return if (s.count < cap) true to s.copy(count = s.count + 1) else false to s
    }
}

interface UsageStore {
    /** Atomik: izin varsa sayaci artirir ve true doner. */
    fun tryAcquire(provider: ProviderId, today: Long, limit: Int?): Boolean
    fun count(provider: ProviderId, today: Long): Int
}

class PrefsUsageStore(context: Context) : UsageStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("assistant_usage", Context.MODE_PRIVATE)

    private fun read(p: ProviderId): QuotaState? {
        val d = prefs.getLong("day_${p.name}", Long.MIN_VALUE)
        return if (d == Long.MIN_VALUE) null else QuotaState(d, prefs.getInt("count_${p.name}", 0))
    }

    @Synchronized
    override fun tryAcquire(provider: ProviderId, today: Long, limit: Int?): Boolean {
        val (ok, next) = QuotaLogic.tryAcquire(read(provider), today, limit)
        // commit: sayac her HTTP istegi oncesi diske yazilir (surec olurse sayim kaybolmasin).
        prefs.edit().putLong("day_${provider.name}", next.day).putInt("count_${provider.name}", next.count).commit()
        return ok
    }

    @Synchronized
    override fun count(provider: ProviderId, today: Long): Int = QuotaLogic.roll(read(provider), today).count
}

/** Testler ve saf kullanim icin bellek ici sayac. */
class MemoryUsageStore : UsageStore {
    private val map = HashMap<ProviderId, QuotaState>()

    @Synchronized
    override fun tryAcquire(provider: ProviderId, today: Long, limit: Int?): Boolean {
        val (ok, next) = QuotaLogic.tryAcquire(map[provider], today, limit)
        map[provider] = next
        return ok
    }

    @Synchronized
    override fun count(provider: ProviderId, today: Long): Int = QuotaLogic.roll(map[provider], today).count
}
