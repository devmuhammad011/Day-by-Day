package com.novastack.daybyday

import org.junit.Assert.*
import org.junit.Test

class FocusClockTest {
    @Test fun readyTimerWaitsForStart() {
        val ready = FocusClock(60000)
        assertFalse(ready.running)
        assertEquals(60000L, ready.remaining(999999))
        assertEquals(59000L, ready.resume(1000000).remaining(1001000))
    }
    @Test fun countdownIncludesTimeAwayFromScreen() {
        val clock = FocusClock(60000).resume(1000)
        assertEquals(15000L, clock.remaining(46000))
        assertEquals(0L, clock.remaining(100000))
    }
    @Test fun pauseFreezesRemainingAndResumeUsesNewAnchor() {
        val paused = FocusClock(60000).resume(1000).pause(21000)
        assertEquals(40000L, paused.remaining(999999))
        assertEquals(35000L, paused.resume(100000).remaining(105000))
    }
    @Test fun repeatedResumeDoesNotRestartTheClock() {
        val clock = FocusClock(10000).resume(100)
        assertEquals(clock, clock.resume(1000))
        assertEquals(9000L, clock.resume(1000).remaining(1100))
    }
    @Test fun cannotResumeCompletedTimer() {
        assertFalse(FocusClock(0).resume(1000).running)
        assertEquals(0L, FocusClock(1).resume(5).remaining(6))
    }
}
