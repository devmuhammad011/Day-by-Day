package com.novastack.daybyday

import org.junit.Assert.*
import org.junit.Test

class VoiceRepeatPolicyTest {
    @Test fun noOnceModeAndDefaultThree() {
        assertEquals(3, VoiceRepeatPolicy.count(1))
        assertEquals(3, VoiceRepeatPolicy.count(3))
        assertEquals(7, VoiceRepeatPolicy.count(7))
    }
    @Test fun countModeStopsAtChosenCount() {
        assertTrue(VoiceRepeatPolicy.again(2, 3, false, 1000, 10000))
        assertFalse(VoiceRepeatPolicy.again(3, 3, false, 1000, 10000))
    }
    @Test fun durationModeContinuesBeyondThreeUntilDeadline() {
        assertTrue(VoiceRepeatPolicy.again(5, 3, true, 1000, 10000))
        assertFalse(VoiceRepeatPolicy.again(5, 3, true, 10000, 10000))
    }
    @Test fun nextReminderShortensDurationWithSafetyMargin() {
        assertEquals(29500L, VoiceRepeatPolicy.deadline(1000, 5, 30000))
        assertFalse(VoiceRepeatPolicy.again(1, 3, false, 29500, VoiceRepeatPolicy.deadline(1000, 5, 30000)))
    }
    @Test fun selectedMinuteLimitIsUsedWithoutAnotherReminder() {
        assertEquals(121000L, VoiceRepeatPolicy.deadline(1000, 2, null))
    }
}
