package com.novastack.daybyday

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ScheduleConflictTest {
    private val today = LocalDate.of(2026, 11, 16)
    private fun task(id: String, time: String, duration: Int = 0, days: Set<Int> = (1..7).toSet(), created: LocalDate = today) =
        Habit(id = id, name = id, created = created, reminder = time, durationMinutes = duration, days = days)
    @Test fun blocksNineAmInsideEightToNineThirty() {
        val run = task("run", "08:00", 90)
        assertNotNull(ScheduleConflict.find(task("read", "09:00"), listOf(run), today))
        assertNull(ScheduleConflict.find(task("read", "09:30"), listOf(run), today))
    }
    @Test fun duplicateTimesAreBlockedEvenWithoutDuration() {
        assertNotNull(ScheduleConflict.find(task("a", "08:00"), listOf(task("b", "08:00")), today))
    }
    @Test fun adjacentIntervalsAreAllowedButPartialOverlapIsNot() {
        val a = task("a", "08:00", 90)
        assertNull(ScheduleConflict.find(task("b", "09:30", 20), listOf(a), today))
        assertNotNull(ScheduleConflict.find(task("b", "07:50", 20), listOf(a), today))
    }
    @Test fun differentWeekdaysAndAmPmAreAllowed() {
        assertNull(ScheduleConflict.find(task("a", "08:00", 90, setOf(1)), listOf(task("b", "08:00", 90, setOf(2))), today))
        assertNull(ScheduleConflict.find(task("a", "08:00", 90), listOf(task("b", "20:00", 90)), today))
    }
    @Test fun catchesSundayToMondayMidnightSpillover() {
        val night = task("night", "23:30", 120, setOf(7), today.minusDays(1))
        assertNotNull(ScheduleConflict.find(task("morning", "00:30", 0, setOf(1)), listOf(night), today))
        assertNull(ScheduleConflict.find(task("morning", "01:30", 0, setOf(1)), listOf(night), today))
    }
    @Test fun futureStartAndSelfEditsAreHandled() {
        val future = task("future", "08:00", 90, created = today.plusYears(1))
        assertNotNull(ScheduleConflict.find(task("a", "09:00"), listOf(future), today))
        assertNull(ScheduleConflict.find(future, listOf(future), today))
    }
    @Test fun separateWorkTimeStillReservesDuration() {
        val a = task("a", "07:50", 90).copy(plannedStart = "08:00")
        assertNotNull(ScheduleConflict.find(task("b", "09:00"), listOf(a), today))
        assertNull(ScheduleConflict.find(task("b", "09:30"), listOf(a), today))
        assertNotNull(ScheduleConflict.find(task("b", "07:50"), listOf(a), today))
    }
    @Test fun workOnlyAndUntimedHabits() {
        val a = task("a", "08:00", 90).copy(reminder = null, plannedStart = "08:00")
        assertNotNull(ScheduleConflict.find(task("b", "09:00"), listOf(a), today))
        assertNull(ScheduleConflict.find(task("b", "09:00").copy(reminder = null), listOf(a), today))
    }
    @Test fun batchImportValidatorAllowsBoundariesAndDetectsConflicts() {
        val a = task("a", "08:00", 90)
        assertNull(ScheduleConflict.firstIn(listOf(a, task("b", "09:30", 30)), today))
        assertNotNull(ScheduleConflict.firstIn(listOf(a, task("b", "09:00")), today))
        assertNotNull(ScheduleConflict.firstIn(listOf(task("a", "08:00"), task("b", "08:00")), today))
    }
}
