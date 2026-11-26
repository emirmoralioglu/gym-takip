package com.emir.gym.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestTimerTest {
    @Test fun remainingRoundsUpAndNeverNegative() {
        assertEquals(60, RestTimer.remainingSeconds(60_000, 0))
        assertEquals(60, RestTimer.remainingSeconds(60_000, 1))   // 59.999 sn -> 60
        assertEquals(1, RestTimer.remainingSeconds(60_000, 59_001))
        assertEquals(0, RestTimer.remainingSeconds(60_000, 60_000))
        assertEquals(0, RestTimer.remainingSeconds(60_000, 99_000)) // ekran kapaliyken sure gecti
    }

    @Test fun formatMinutesSeconds() {
        assertEquals("0:30", RestTimer.format(30))
        assertEquals("2:00", RestTimer.format(120))
        assertEquals("1:05", RestTimer.format(65))
    }

    @Test fun customParsingBounds() {
        assertEquals(45, RestTimer.parseCustom(" 45 "))
        assertEquals(1, RestTimer.parseCustom("1"))
        assertEquals(3600, RestTimer.parseCustom("3600"))
        assertNull(RestTimer.parseCustom("0"))
        assertNull(RestTimer.parseCustom("3601"))
        assertNull(RestTimer.parseCustom("-5"))
        assertNull(RestTimer.parseCustom("abc"))
        assertNull(RestTimer.parseCustom(""))
    }
}
