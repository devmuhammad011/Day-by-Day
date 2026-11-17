package com.novastack.daybyday

import android.app.*
import android.content.*
import android.os.*
import android.provider.Settings

class FocusTimer(private val context: Context) {
    private val prefs = context.getSharedPreferences("focus_timer", Context.MODE_PRIVATE)
    data class Session(val habitId: String, val name: String, val totalMs: Long, val clock: FocusClock, val notified: Boolean)
    private fun boot() = Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
    fun load(): Session? {
        val id = prefs.getString("id", null) ?: return null
        var clock = FocusClock(prefs.getLong("remaining", 0), prefs.getLong("anchor", 0), prefs.getBoolean("running", false))
        if (clock.running && prefs.getInt("boot", -1) != boot()) {
            // Reboot loses the monotonic anchor. Pause instead of silently starting a new timer.
            clock = FocusClock((prefs.getLong("wallEnd", 0) - System.currentTimeMillis()).coerceIn(0L, clock.remainingMs))
            save(Session(id, prefs.getString("name", "Habit")!!, prefs.getLong("total", 0), clock, false))
        }
        return Session(id, prefs.getString("name", "Habit")!!, prefs.getLong("total", 0), clock, prefs.getBoolean("notified", false))
    }
    private fun save(s: Session) {
        prefs.edit().putString("id", s.habitId).putString("name", s.name).putLong("total", s.totalMs)
            .putLong("remaining", s.clock.remainingMs).putLong("anchor", s.clock.anchorMs).putBoolean("running", s.clock.running)
            .putInt("boot", boot()).putLong("wallEnd", System.currentTimeMillis() + s.clock.remaining(SystemClock.elapsedRealtime()))
            .putBoolean("notified", s.notified).commit()
    }
    private fun alarm() = PendingIntent.getBroadcast(context, 901, Intent(context, FocusReceiver::class.java).setAction("FINISH"), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun notifyManager() = context.getSystemService(NotificationManager::class.java)
    fun start(habit: Habit) {
        require(habit.durationMinutes in 1..1440)
        save(Session(habit.id, habit.name, habit.durationMinutes * 60000L, FocusClock(habit.durationMinutes * 60000L).resume(SystemClock.elapsedRealtime()), false))
        update()
    }
    fun toggle() {
        val s = load() ?: return
        val now = SystemClock.elapsedRealtime()
        save(s.copy(clock = if (s.clock.running) s.clock.pause(now) else s.clock.resume(now)))
        update()
    }
    fun reset() {
        val s = load() ?: return
        save(s.copy(clock = FocusClock(s.totalMs), notified = false)); update()
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
            .setContentText(if (s.clock.running) "Focus timer running • Tap to open" else "Paused • ${remaining / 60000}:${"%02d".format((remaining / 1000) % 60)} remaining")
            .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(if (showOnLockScreen) Notification.VISIBILITY_PUBLIC else Notification.VISIBILITY_SECRET)
            .setSubText("DailyStride • Focus timer")
            .addAction(Notification.Action.Builder(null, "View timer", open).build())
            .addAction(Notification.Action.Builder(null, if (s.clock.running) "Pause" else "Resume", toggle).build())
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
    fun finish() {
        val s = load() ?: return
        if (s.clock.remaining(SystemClock.elapsedRealtime()) > 0) return
        if (s.notified) return
        save(s.copy(clock = FocusClock(0), notified = true))
        notifyManager().cancel(901)
        ReminderScheduler.channel(context)
        val open = PendingIntent.getActivity(context, 902, Intent(context, TimerActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, ReminderScheduler.CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Focus complete: ${s.name}").setContentText("Your session is complete. Open to check in.")
            .setContentIntent(open).setAutoCancel(true).build()
        try { notifyManager().notify(904, notification) } catch (_: SecurityException) { }
        val settings = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (settings.getBoolean("speakTimerEnd", false) && ReminderScheduler.exactAllowed(context) && notifyManager().areNotificationsEnabled()) {
            try {
                context.startForegroundService(Intent(context, VoiceReminderService::class.java)
                    .putExtra("name", s.name).putExtra("completedTimer", true))
            } catch (_: Exception) { /* Completion notification stays available when Android blocks background speech. */ }
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
