package com.novastack.daybyday

import java.time.LocalDate

object FocusCompletion {
    fun complete(habit: Habit, date: LocalDate, clock: FocusClock, nowMs: Long): Habit {
        if (!clock.running || clock.remaining(nowMs) > 0 || !habit.scheduled(date)) return habit
        return habit.copy(completed = habit.completed + date)
    }
}
