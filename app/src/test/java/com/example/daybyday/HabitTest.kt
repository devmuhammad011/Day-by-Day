package com.example.daybyday

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HabitTest {
    private val today = LocalDate.of(2026, 11, 16)
    @Test fun streakAllowsTodayToBePending() {
        val habit = Habit(name = "Read", completed = setOf(today.minusDays(1), today.minusDays(2)))
        assertEquals(2, habit.streak(today))
        assertEquals(3, habit.toggle(today).streak(today))
    }
    @Test fun missedDayBreaksStreak() {
        assertEquals(0, Habit(name = "Read", completed = setOf(today.minusDays(2))).streak(today))
    }
    @Test fun toggleCanUndoCompletion() {
        val habit = Habit(name = "Read")
        assertEquals(habit, habit.toggle(today).toggle(today))
    }
    @Test fun streakDoesNotCrossGap() {
        val habit = Habit(name = "Read", completed = setOf(today, today.minusDays(1), today.minusDays(3)))
        assertEquals(2, habit.streak(today))
    }
}
