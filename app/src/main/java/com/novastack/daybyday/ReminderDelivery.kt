package com.novastack.daybyday

object ReminderDelivery {
    const val VOICE_GRACE_MS = 120_000L
    fun isFresh(dueAt: Long, now: Long): Boolean = dueAt > 0 && now >= dueAt && now - dueAt <= VOICE_GRACE_MS
}
