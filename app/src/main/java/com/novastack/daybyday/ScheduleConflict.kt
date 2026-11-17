package com.novastack.daybyday

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Weekly schedules repeat indefinitely; a full week plus boundary days covers midnight spillover. */
object ScheduleConflict {
    data class Clash(val habit: Habit, val start: LocalDateTime, val end: LocalDateTime)
    private data class Slot(val start: LocalDateTime, val end: LocalDateTime)
    private fun slots(h: Habit, base: LocalDate): List<Slot> = (-1L..8L).flatMap { offset ->
        val date = base.plusDays(offset)
        if (!h.scheduled(date)) emptyList() else {
            val time = h.plannedStart ?: h.reminder
            val work = time?.let { date.atTime(LocalTime.parse(it)) }?.let { Slot(it, it.plusMinutes(h.durationMinutes.toLong())) }
            val reminder = h.reminder?.let { date.atTime(LocalTime.parse(it)) }?.let { Slot(it, it) }
            listOfNotNull(work, reminder).distinct()
        }
    }
    private fun overlap(a: Slot, b: Slot): Boolean = when {
        a.start == a.end && b.start == b.end -> a.start == b.start
        a.start == a.end -> a.start >= b.start && a.start < b.end
        b.start == b.end -> b.start >= a.start && b.start < a.end
        else -> a.start < b.end && b.start < a.end
    }
    fun find(candidate: Habit, others: List<Habit>, today: LocalDate = LocalDate.now()): Clash? {
        for (other in others) {
            if (other.id == candidate.id) continue
            val base = maxOf(today, candidate.created, other.created)
            for (a in slots(candidate, base)) for (b in slots(other, base)) {
                if (overlap(a, b) && maxOf(a.start, b.start) >= base.atStartOfDay()) return Clash(other, b.start, b.end)
            }
        }
        return null
    }
    fun firstIn(habits: List<Habit>, today: LocalDate = LocalDate.now()): Clash? {
        val base = maxOf(today, habits.maxOfOrNull { it.created } ?: today)
        val entries = habits.flatMap { h -> slots(h, base).map { h to it } }.sortedBy { it.second.start }
        val active = mutableListOf<Pair<Habit, Slot>>()
        for (entry in entries) {
            val current = entry.second
            active.removeAll { (_, old) -> old.end < current.start || (old.end == current.start && old.start != old.end) }
            val conflict = active.firstOrNull { (h, old) -> h.id != entry.first.id && overlap(old, current) }
            if (conflict != null) return Clash(conflict.first, conflict.second.start, conflict.second.end)
            active.add(entry)
        }
        return null
    }
}
