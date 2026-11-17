package com.novastack.daybyday

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID

data class Habit(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String = "🌱",
    val created: LocalDate = LocalDate.now(),
    val completed: Set<LocalDate> = emptySet(),
    val days: Set<Int> = (1..7).toSet(),
    val reminder: String? = null,
    val voice: Boolean = false
) {
    fun scheduled(date: LocalDate) = date >= created && date.dayOfWeek.value in days
    fun streak(today: LocalDate): Int {
        var cursor = today
        if (!scheduled(cursor) || cursor !in completed) cursor = cursor.minusDays(1)
        var count = 0
        while (cursor >= created) {
            if (scheduled(cursor)) {
                if (cursor !in completed) break
                count++
            }
            cursor = cursor.minusDays(1)
        }
        return count
    }
    fun toggle(date: LocalDate) = if (!scheduled(date)) this else copy(completed = if (date in completed) completed - date else completed + date)
    fun nextReminder(now: LocalDateTime): LocalDateTime? {
        val time = reminder?.let(LocalTime::parse) ?: return null
        return (0L..7L).map { now.toLocalDate().plusDays(it) }
            .filter { scheduled(it) && it !in completed }
            .map { it.atTime(time) }.firstOrNull { it > now }
    }
}
