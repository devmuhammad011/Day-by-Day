package com.novastack.daybyday

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class FocusCompletionTest {
    private val day = LocalDate.of(2026, 11, 20)
    private val habit = Habit(name = "Run", created = day)
    @Test fun completedRunningTimerChecksHabit() {
        val updated = FocusCompletion.complete(habit, day, FocusClock(60000).resume(1000), 61000)
        assertTrue(day in updated.completed)
    }
    @Test fun pausedResetAndUnfinishedTimersDoNotCheckHabit() {
        assertEquals(habit, FocusCompletion.complete(habit, day, FocusClock(60000).resume(1000), 60000))
        assertEquals(habit, FocusCompletion.complete(habit, day, FocusClock(60000), 100000))
        assertEquals(habit, FocusCompletion.complete(habit, day, FocusClock(0), 100000))
    }
    @Test fun repeatedFinishNeverUnchecksOrDuplicates() {
        val clock = FocusClock(60000).resume(1000)
        val once = FocusCompletion.complete(habit, day, clock, 61000)
        assertEquals(once, FocusCompletion.complete(once, day, clock, 62000))
        assertEquals(1, once.completed.size)
    }
    @Test fun overnightSessionUsesItsStartDay() {
        val clock = FocusClock(120000).resume(1000)
        val updated = FocusCompletion.complete(habit, day, clock, 121000)
        assertTrue(day in updated.completed)
        assertFalse(day.plusDays(1) in updated.completed)
    }
    @Test fun unscheduledDayIsNotAwarded() {
        val restDay = habit.copy(days = setOf(1))
        assertEquals(restDay, FocusCompletion.complete(restDay, day, FocusClock(1).resume(1), 2))
    }
}
