package com.emir.gym.assistant

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextCleanEmojiTest {
    private val family = "👨‍👩‍👧"               // erkek-kadin-kiz
    private val doctor = "👩‍⚕️"                                  // kadin + asa (meslek)
    private val doctorTone = "👩🏽‍⚕️"                  // ten rengi
    private val heartFire = "❤️‍🔥"
    private val rainbowFlag = "🏳️‍🌈"

    @Test fun joinedEmojiSequencesAreKept() {
        for (e in listOf(family, doctor, doctorTone, heartFire, rainbowFlag)) {
            assertEquals(e, TextClean.clean(e))
            assertEquals("tavuk $e", TextClean.clean("  tavuk $e "))
        }
    }

    @Test fun bareJoinersAreRemoved() {
        assertEquals("ab", TextClean.clean("a‍b"))
        assertEquals("ab", TextClean.clean("a‌b"))
        assertEquals("a", TextClean.clean("‍a‍"))                       // bas / son
        assertEquals("a b", TextClean.clean("a ‍ b"))
        val twice = "👨‍‍👩"                          // art arda: biri kalir
        assertEquals("👨‍👩", TextClean.clean(twice))
        assertEquals("👨a", TextClean.clean("👨‍a"))      // emojiden sonra harf: atilir
        assertEquals("çğıöşüİ", TextClean.clean("ç‌ğıö‍şüİ"))
    }

    @Test fun bidiAndZeroWidthAlwaysRemoved() {
        assertEquals("ab", TextClean.clean("a‪b‫‬‭‮"))
        assertEquals("ab", TextClean.clean("a⁦⁧⁨⁩b"))
        assertEquals("ab", TextClean.clean("a‎‏﻿​b"))
        // emoji arasinda bile yon karakteri atilir, birlestirici korunur
        assertEquals("👨‍👩", TextClean.clean("👨‮‍​👩"))
    }
}

class PreparedCheckTest {
    @Test fun firstMissingReturnsFirstAbsentPath() {
        val present = setOf("/a", "/c")
        assertEquals("/b", PreparedCheck.firstMissing(listOf("/a", "/b", "/c", "/d")) { it in present })
        assertNull(PreparedCheck.firstMissing(listOf("/a", "/c")) { it in present })
        assertNull(PreparedCheck.firstMissing(emptyList()) { false })
    }
}

class ClearRunnerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Test fun runningUntilActionFinishesAndBlocksSecondStart() = runBlocking {
        val gate = CompletableDeferred<Unit>()
        var runs = 0
        val r = ClearRunner(scope, Dispatchers.Default) { runs++; gate.await() }
        assertFalse(r.running.value)
        assertTrue(r.start())
        assertTrue(r.running.value)
        assertFalse(r.start())            // ikinci temizleme baslamaz
        assertFalse(ChatBusy.idle(false, false, false, r.running.value))   // gonderim de baslamaz
        gate.complete(Unit)
        withTimeout(5000) { while (r.running.value) kotlinx.coroutines.delay(5) }
        assertTrue(ChatBusy.idle(false, false, false, r.running.value))
        assertEquals(1, runs)
        assertTrue(r.start())             // bitince yeniden baslayabilir
    }

    @Test fun runningResetsEvenIfActionThrows() = runBlocking {
        val r = ClearRunner(scope, Dispatchers.Default) { throw IllegalStateException("disk") }
        r.start()
        withTimeout(5000) { while (r.running.value) kotlinx.coroutines.delay(5) }
        assertFalse(r.running.value)
    }

    @Test fun clearingBlocksNewWork() {
        assertFalse(ChatBusy.idle(sending = false, testing = false, engineBusy = false, clearing = true))
        assertTrue(ChatBusy.idle(false, false, false, false))
    }
}
