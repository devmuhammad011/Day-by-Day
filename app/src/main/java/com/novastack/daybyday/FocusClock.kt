package com.novastack.daybyday

/** Monotonic countdown: elapsed time includes device sleep and ignores wall-clock changes. */
data class FocusClock(val remainingMs: Long, val anchorMs: Long = 0, val running: Boolean = false) {
    fun remaining(nowMs: Long): Long = if (running) (remainingMs - (nowMs - anchorMs).coerceAtLeast(0)).coerceAtLeast(0) else remainingMs
    fun pause(nowMs: Long) = FocusClock(remaining(nowMs))
    fun resume(nowMs: Long) = if (running || remainingMs == 0L) this else copy(anchorMs = nowMs, running = true)
}
