package com.example.daybyday

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class HabitTest {
    private val today = LocalDate.of(2026, 11, 16)
    private fun habit(completed: Set<LocalDate> = emptySet(), days: Set<Int> = (1..7).toSet()) =
        Habit(name = "Read", created = today.minusDays(30), completed = completed, days = days)
    @Test fun streakAllowsTodayToBePending() {
        val h = habit(setOf(today.minusDays(1), today.minusDays(2)))
        assertEquals(2, h.streak(today))
        assertEquals(3, h.toggle(today).streak(today))
    }
    @Test fun missedScheduledDayBreaksStreak() { assertEquals(0, habit(setOf(today.minusDays(2))).streak(today)) }
    @Test fun toggleCanUndoCompletion() { val h = habit(); assertEquals(h, h.toggle(today).toggle(today)) }
    @Test fun weekendsDoNotBreakWeekdayStreaks() {
        val friday = today.minusDays(3)
        val h = habit(setOf(friday, friday.minusDays(1)), (1..5).toSet())
        assertEquals(2, h.streak(today))
        assertEquals(3, h.toggle(today).streak(today))
        assertEquals(h, h.toggle(today.minusDays(1)))
    }
    @Test fun nextReminderSkipsCompletedAndUnscheduledDays() {
        val h = habit(setOf(today), setOf(1,3,5)).copy(reminder = "08:00")
        assertEquals(LocalDateTime.of(2026,11,18,8,0), h.nextReminder(today.atTime(7,0)))
    }
    @Test fun reminderMovesToNextWeekWhenTodaysTimeHasPassed() {
        val h = habit(days = setOf(1)).copy(reminder = "08:00")
        assertEquals(today.plusDays(7).atTime(8,0), h.nextReminder(today.atTime(9,0)))
    }
    @Test fun noCompletionBeforeCreation() {
        val h = Habit(name = "New", created = today)
        assertEquals(h, h.toggle(today.minusDays(1)))
    }
}
