package com.novastack.daybyday

import android.app.*
import android.content.*
import android.os.*
import android.provider.Settings
import java.time.LocalDate

class FocusTimer(private val context: Context) {
    private val prefs = context.getSharedPreferences("focus_timer", Context.MODE_PRIVATE)
    data class Session(val habitId: String, val name: String, val totalMs: Long, val clock: FocusClock, val notified: Boolean, val startedOn: LocalDate = LocalDate.now(), val autoChecked: Boolean = false, val completionNote: String = "")
    private fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    fun load(): Session? {
        val id = prefs.getString("id", null) ?: return null
        val startedOn = runCatching { LocalDate.parse(prefs.getString("startedOn", null)) }.getOrDefault(LocalDate.now())
        val checked = prefs.getBoolean("autoChecked", false)
        val note = prefs.getString("completionNote", "") ?: ""
        var clock = FocusClock(prefs.getLong("remaining", 0), prefs.getLong("anchor", 0), prefs.getBoolean("running", false))
        if (clock.running && prefs.getInt("boot", -1) != boot()) {
            // Reboot loses the monotonic anchor. Pause instead of silently starting a new timer.
            clock = FocusClock((prefs.getLong("wallEnd", 0) - System.currentTimeMillis()).coerceIn(0L, clock.remainingMs))
            save(Session(id, prefs.getString("name", "Habit")!!, prefs.getLong("total", 0), clock, false, startedOn, checked, note))
        }
        return Session(id, prefs.getString("name", "Habit")!!, prefs.getLong("total", 0), clock, prefs.getBoolean("notified", false), startedOn, checked, note)
    }
    private fun save(s: Session) {
        prefs.edit().putString("id", s.habitId).putString("name", s.name).putLong("total", s.totalMs)
            .putLong("remaining", s.clock.remainingMs).putLong("anchor", s.clock.anchorMs).putBoolean("running", s.clock.running)
            .putInt("boot", boot()).putLong("wallEnd", System.currentTimeMillis() + s.clock.remaining(SystemClock.elapsedRealtime()))
            .putBoolean("notified", s.notified).putString("startedOn", s.startedOn.toString())
            .putBoolean("autoChecked", s.autoChecked).putString("completionNote", s.completionNote).commit()
    }
    private fun alarm() = PendingIntent.getBroadcast(context, 901, Intent(context, FocusReceiver::class.java).setAction("FINISH"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun notifyManager() = context.getSystemService(NotificationManager::class.java)
    fun prepare(habit: Habit) {
        require(habit.durationMinutes in 1..1440)
        save(Session(habit.id, habit.name, habit.durationMinutes * 60000L, FocusClock(habit.durationMinutes * 60000L), false))
        update()
    }
    fun start(habit: Habit) {
        require(habit.durationMinutes in 1..1440)
        save(Session(habit.id, habit.name, habit.durationMinutes * 60000L, FocusClock(habit.durationMinutes * 60000L).resume(SystemClock.elapsedRealtime()), false))
        update()
    }
    fun toggle() {
        val s = load() ?: return
        val now = SystemClock.elapsedRealtime()
        if (s.clock.running && s.clock.remaining(now) == 0L) { finish(); return }
        save(s.copy(clock = if (s.clock.running) s.clock.pause(now) else s.clock.resume(now), startedOn = if (!s.clock.running && s.clock.remainingMs == s.totalMs) LocalDate.now() else s.startedOn))
        update()
    }
    fun reset() {
        val s = load() ?: return
        save(s.copy(clock = FocusClock(s.totalMs), notified = false, startedOn = LocalDate.now(), autoChecked = false, completionNote = "")); update()
    }
    fun recordCheckIn() {
        val s = load() ?: return
        save(s.copy(autoChecked = true, completionNote = "✓ Habit completed for ${s.startedOn}."))
    }
    fun clear() {
        context.getSystemService(AlarmManager::class.java).cancel(alarm())
        prefs.edit().clear().commit(); notifyManager().cancel(901)
    }
    fun update() {
        val s = load() ?: return
        val remaining = s.clock.remaining(SystemClock.elapsedRealtime())
        val alarms = context.getSystemService(AlarmManager::class.java)
        alarms.cancel(alarm())
        if (remaining == 0L) { finish(); return }
        val showOnLockScreen = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("lockTimer", true)
        notifyManager().createNotificationChannel(NotificationChannel("focus_progress", "Focus timer progress", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Live countdown with pause and resume, including on the lock screen when allowed"
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        val open = PendingIntent.getActivity(context, 902, Intent(context, TimerActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val toggle = PendingIntent.getBroadcast(context, 903, Intent(context, FocusReceiver::class.java).setAction("TOGGLE"), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = Notification.Builder(context, "focus_progress").setSmallIcon(R.drawable.ic_notification).setContentTitle(s.name)
            .setContentText(if (s.clock.running) "Focus timer running • Tap to open" else if (remaining == s.totalMs) "Ready • Tap Start to begin" else "Paused • ${remaining / 60000}:${"%02d".format((remaining / 1000) % 60)} remaining")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(if (showOnLockScreen) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_SECRET)
            .setSubText("DailyStride • Focus timer")
            .addAction(Notification.Action.Builder(null, "View timer", open).build())
            .addAction(Notification.Action.Builder(null, if (s.clock.running) "Pause" else if (remaining == s.totalMs) "Start" else "Resume", toggle).build())
        if (s.clock.running) builder.setWhen(System.currentTimeMillis() + remaining).setUsesChronometer(true).setChronometerCountDown(true)
        try { notifyManager().notify(901, builder.build()) } catch (_: SecurityException) { }
        if (s.clock.running) {
            val at = SystemClock.elapsedRealtime() + remaining
            try {
                if (ReminderScheduler.exactAllowed(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarm())
                else alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarm())
            } catch (_: SecurityException) { alarms.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, at, alarm()) }
        }
    }
    fun finish(announce: Boolean = true) {
        val s = load() ?: return
        if (s.clock.remaining(SystemClock.elapsedRealtime()) > 0) return
        if (s.notified) return
        val nowElapsed = SystemClock.elapsedRealtime()
        val dueElapsed = s.clock.anchorMs + s.clock.remainingMs
        val fresh = s.clock.running && ReminderDelivery.isFresh(dueElapsed, nowElapsed)
        val dueWall = System.currentTimeMillis() - (nowElapsed - dueElapsed).coerceAtLeast(0)
        var checked = false
        val note = if (!s.clock.running) "Session ended after restart. Review your habit before checking in." else try {
            val store = HabitStore(context)
            val habits = store.load()
            val habit = habits.find { it.id == s.habitId }
            when {
                habit == null -> "Session complete. This habit has been removed."
                !habit.scheduled(s.startedOn) -> "Session complete. This habit was not scheduled for ${s.startedOn}."
                else -> {
                    val completed = FocusCompletion.complete(habit, s.startedOn, s.clock, SystemClock.elapsedRealtime())
                    store.save(habits.map { if (it.id == habit.id) completed else it })
                    checked = true
                    // Saving the check-in must not depend on notification/alarm permissions.
                    runCatching { ReminderScheduler.cancel(context, completed) }
                    runCatching { ReminderScheduler.schedule(context, completed) }
                    "✓ Habit completed automatically for ${s.startedOn}."
                }
            }
        } catch (_: Exception) { "Session complete. Could not save the check-in; open the timer to retry." }
        save(s.copy(clock = FocusClock(0), notified = true, autoChecked = checked, completionNote = note))
        notifyManager().cancel(901)
        ReminderScheduler.channel(context)
        val open = PendingIntent.getActivity(context, 902, Intent(context, TimerActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, if (fresh && announce) ReminderScheduler.CHANNEL else ReminderScheduler.MISSED_CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Focus complete: ${s.name}").setContentText(note)
            .setContentIntent(open).setAutoCancel(true).build()
        try { notifyManager().notify(904, notification) } catch (_: SecurityException) { }
        val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (settings.getBoolean("speakTimerEnd", false)) {
            if (fresh && announce) VoicePlayback.start(context, s.name, dueWall, completed = true, token = "focus/${s.habitId}/${s.clock.anchorMs}")
            else VoiceDiagnostics.record(context, "Focus session ended earlier. Completion was processed silently; no old voice alarm was replayed.")
        }
    }
}

class FocusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val timer = FocusTimer(context)
        when (intent.action) {
            "TOGGLE" -> timer.toggle()
            "FINISH" -> timer.finish()
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED -> timer.update()
        }
    }
}
