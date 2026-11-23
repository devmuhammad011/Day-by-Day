package com.novastack.daybyday

object VoiceRepeatPolicy {
    fun count(value: Int) = value.coerceIn(3, 999)
    fun deadline(now: Long, minutes: Int?, nextReminder: Long?): Long {
        val desired = now + (minutes?.coerceIn(1, 1440) ?: 1440) * 60000L
        return minOf(desired, nextReminder?.minus(500) ?: Long.MAX_VALUE)
    }
    fun again(completed: Int, repeats: Int, timed: Boolean, now: Long, endAt: Long): Boolean =
        now < endAt && (timed || completed < count(repeats))
}
