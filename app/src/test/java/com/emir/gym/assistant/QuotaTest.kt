package com.emir.gym.assistant

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val H = 3_600_000L
private const val T0 = 1_800_000_000_000L // keyfi bir an

private class Clock(var wall: Long, var elapsed: Long = 5 * H) {
    fun qc() = QuotaClock({ wall }, { elapsed })
    fun advance(ms: Long) { wall += ms; elapsed += ms }
}

class QuotaLogicTest {
    @Test fun capIsFloorOf99Percent() {
        assertNull(QuotaLogic.cap(null))
        assertEquals(0, QuotaLogic.cap(1)); assertEquals(1, QuotaLogic.cap(2)); assertEquals(99, QuotaLogic.cap(100))
        assertEquals(297, QuotaLogic.cap(300)); assertEquals(990, QuotaLogic.cap(1000))
    }

    @Test fun stopsExactlyAtCapWithinWindow() {
        val c = Clock(T0); val u = StoredUsageStore(MemoryStampStorage(), c.qc())
        var ok = 0
        repeat(150) { if (u.tryAcquire(ProviderId.GEMINI, 100)) ok++; c.advance(1000) }
        assertEquals(99, ok)
        assertEquals(99, u.count(ProviderId.GEMINI))
        assertEquals(0, u.remaining(ProviderId.GEMINI, 100))
    }

    @Test fun freedExactly24HoursAfterTheOldestRequest() {
        val c = Clock(T0); val u = StoredUsageStore(MemoryStampStorage(), c.qc())
        repeat(2) { assertTrue(u.tryAcquire(ProviderId.GEMINI, 3)) } // cap = 2
        assertFalse(u.tryAcquire(ProviderId.GEMINI, 3))
        val reopen = u.reopensAt(ProviderId.GEMINI, 3)!!
        assertEquals(T0 + 24 * H, reopen)
        c.advance(24 * H - 1)
        assertFalse(u.tryAcquire(ProviderId.GEMINI, 3))
        c.advance(1)
        assertTrue(u.tryAcquire(ProviderId.GEMINI, 3))
    }

    @Test fun midnightDoesNotResetTheCounter() {
        // Takvim gunu degisimi (ve saglayici kotasinin sifirlanma saati) sayaci sifirlamaz.
        val dayMs = 24 * H
        val midnight = (T0 / dayMs + 1) * dayMs
        val c = Clock(midnight - 60_000); val u = StoredUsageStore(MemoryStampStorage(), c.qc())
        repeat(2) { assertTrue(u.tryAcquire(ProviderId.GEMINI, 3)) }
        c.advance(120_000) // gece yarisini gec
        assertFalse("yeni takvim gununde de sinir gecerli", u.tryAcquire(ProviderId.GEMINI, 3))
        c.advance(10 * H) // 10:00 (Gemini Pasifik sifirlanmasi gibi) - hala 24 saat dolmadi
        assertFalse(u.tryAcquire(ProviderId.GEMINI, 3))
    }

    @Test fun anyProviderDayIsCoveredBySlidingWindow() {
        // Sagalayici kotasi t=10h'de sifirlansa bile 24 saatlik herhangi bir pencerede en cok cap istek atilir.
        val c = Clock(T0); val u = StoredUsageStore(MemoryStampStorage(), c.qc())
        val times = ArrayList<Long>()
        repeat(200) { if (u.tryAcquire(ProviderId.GEMINI, 10)) times += c.wall; c.advance(30 * 60_000) }
        for (t in times) assertTrue(times.count { it >= t && it < t + 24 * H } <= 9)
    }

    @Test fun clockRewindIsConservative() {
        val c = Clock(T0); val u = StoredUsageStore(MemoryStampStorage(), c.qc())
        repeat(2) { assertTrue(u.tryAcquire(ProviderId.GEMINI, 3)) }
        c.wall -= 30 * H // saat geri alindi, elapsed ilerlemedi
        assertFalse(u.tryAcquire(ProviderId.GEMINI, 3))
        c.advance(60_000)
        assertFalse(u.tryAcquire(ProviderId.GEMINI, 3))
    }

    @Test fun clockForwardJumpDoesNotFreeRequests() {
        val c = Clock(T0); val u = StoredUsageStore(MemoryStampStorage(), c.qc())
        repeat(2) { assertTrue(u.tryAcquire(ProviderId.GEMINI, 3)) }
        c.wall += 30 * H // duvar saati sicradi; elapsed yalniz 1 dk ilerledi
        c.elapsed += 60_000
        assertFalse("elapsed'a gore kayit hala taze", u.tryAcquire(ProviderId.GEMINI, 3))
    }

    @Test fun rebootUsesWallClockAge() {
        val c = Clock(T0, elapsed = 50 * H); val u = StoredUsageStore(MemoryStampStorage(), c.qc())
        repeat(2) { assertTrue(u.tryAcquire(ProviderId.GEMINI, 3)) }
        c.wall += 25 * H; c.elapsed = 1000 // yeniden acilis: elapsed kuculdu
        assertTrue(u.tryAcquire(ProviderId.GEMINI, 3))
    }

    @Test fun providersAreIndependent() {
        val c = Clock(T0); val u = StoredUsageStore(MemoryStampStorage(), c.qc())
        assertTrue(u.tryAcquire(ProviderId.GEMINI, 2)); assertFalse(u.tryAcquire(ProviderId.GEMINI, 2))
        assertTrue(u.tryAcquire(ProviderId.ANTHROPIC, 2))
        assertEquals(1, u.count(ProviderId.GEMINI)); assertEquals(0, u.count(ProviderId.OPENAI_COMPAT))
    }

    @Test fun unlimitedAlwaysAllowedButStorageStaysBounded() {
        val st = MemoryStampStorage(); val c = Clock(T0); val u = StoredUsageStore(st, c.qc())
        repeat(QuotaLogic.MAX_KEPT + 500) { assertTrue(u.tryAcquire(ProviderId.GEMINI, null)); c.advance(1) }
        assertEquals(QuotaLogic.MAX_KEPT, QuotaLogic.decode(st.read(ProviderId.GEMINI)).size)
        assertNull(u.remaining(ProviderId.GEMINI, null)); assertNull(u.reopensAt(ProviderId.GEMINI, null))
    }

    @Test fun oldRecordsArePrunedOnWrite() {
        val st = MemoryStampStorage(); val c = Clock(T0); val u = StoredUsageStore(st, c.qc())
        repeat(5) { u.tryAcquire(ProviderId.GEMINI, 100) }
        c.advance(25 * H)
        u.tryAcquire(ProviderId.GEMINI, 100)
        assertEquals(1, QuotaLogic.decode(st.read(ProviderId.GEMINI)).size)
    }

    @Test fun uiHelpers() {
        val c = Clock(T0); val u = StoredUsageStore(MemoryStampStorage(), c.qc())
        assertEquals(1, u.remaining(ProviderId.GEMINI, 2).let { it }) // cap=1
        assertNull(u.oldestExpiry(ProviderId.GEMINI))
        u.tryAcquire(ProviderId.GEMINI, 2)
        c.advance(H)
        assertEquals(0, u.remaining(ProviderId.GEMINI, 2))
        assertEquals(T0 + 24 * H, u.oldestExpiry(ProviderId.GEMINI))
        assertEquals(T0 + 24 * H, u.reopensAt(ProviderId.GEMINI, 2))
        assertNull(u.reopensAt(ProviderId.GEMINI, 100))
    }

    @Test fun failedWriteRejectsTheRequest() {
        val failing = object : StampStorage {
            override fun read(provider: ProviderId): String? = null
            override fun write(provider: ProviderId, value: String) = false
        }
        val u = StoredUsageStore(failing, Clock(T0).qc())
        assertFalse("yazilamayan sayac = istek atilmaz", u.tryAcquire(ProviderId.GEMINI, 100))
        assertFalse(u.tryAcquire(ProviderId.GEMINI, null))
    }

    @Test fun corruptStorageDecodesSafely() {
        assertEquals(emptyList<Stamp>(), QuotaLogic.decode("bozuk,1:x,:"))
        assertEquals(listOf(Stamp(1, 2)), QuotaLogic.decode("1:2,xx"))
    }
}
