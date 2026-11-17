package com.example.daybyday

import java.time.LocalDate
import java.util.UUID

data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String = "🌱",
    val created: LocalDate = LocalDate.now(),
    val completed: Set<LocalDate> = emptySet()
) {
    fun streak(today: LocalDate): Int {
        var cursor = if (today in completed) today else today.minusDays(1)
        var count = 0
        while (cursor in completed) { count++; cursor = cursor.minusDays(1) }
        return count
    }
    fun toggle(date: LocalDate) = copy(completed = if (date in completed) completed - date else completed + date)
}
