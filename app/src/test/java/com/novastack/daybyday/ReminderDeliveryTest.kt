package com.novastack.daybyday

import org.junit.Assert.*
import org.junit.Test

class ReminderDeliveryTest {
    private val due = 1000000L
    @Test fun dueAndSlightlyDelayedRemindersCanSpeak() {
        assertTrue(ReminderDelivery.isFresh(due, due))
        assertTrue(ReminderDelivery.isFresh(due, due + 1000))
        assertTrue(ReminderDelivery.isFresh(due, due + ReminderDelivery.VOICE_GRACE_MS))
    }
    @Test fun staleOrPreviousDayRemindersMustNotSpeakOnReopen() {
        assertFalse(ReminderDelivery.isFresh(due, due + ReminderDelivery.VOICE_GRACE_MS + 1))
        assertFalse(ReminderDelivery.isFresh(due, due + 86400000))
    }
    @Test fun missingOrFutureTimestampsMustNotSpeak() {
        assertFalse(ReminderDelivery.isFresh(0, due))
        assertFalse(ReminderDelivery.isFresh(due, due - 1))
    }
    @Test fun elapsedClockCompletionUsesTheSameFreshnessRule() {
        val timer = FocusClock(60000).resume(due)
        val end = timer.anchorMs + timer.remainingMs
        assertTrue(ReminderDelivery.isFresh(end, end + 500))
        assertFalse(ReminderDelivery.isFresh(end, end + 600000))
    }
}
