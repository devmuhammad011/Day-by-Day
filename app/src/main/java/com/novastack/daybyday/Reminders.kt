package com.novastack.daybyday

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

object VoiceDiagnostics {
    fun record(context: Context, message: String) {
        context.getSharedPreferences("voice_status", Context.MODE_PRIVATE).edit().putString("message", message).apply()
    }
    fun latest(context: Context) = context.getSharedPreferences("voice_status", Context.MODE_PRIVATE)
        .getString("message", "No voice attempt yet. Use Test voice reminder below.")!!
}

object ReminderScheduler {
    const val CHANNEL = "habit_reminders"
    const val MISSED_CHANNEL = "missed_reminders"
    fun channel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Scheduled habit check-ins" })
        manager.createNotificationChannel(NotificationChannel(MISSED_CHANNEL, "Missed reminders", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Silent notices for reminders Android delivered late"
            setSound(null, null)
        })
    }
    fun notificationsAllowed(context: Context): Boolean {
        val manager = context.getSystemService(NotificationManager::class.java)
        return manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }
    fun exactAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    private fun pending(context: Context, h: Habit, snooze: Boolean = false, dueAt: Long? = null): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction("REMIND")
            .setData(Uri.parse("daybyday://reminder/${Uri.encode(h.id)}/${if (snooze) "snooze" else "regular"}"))
            .putExtra("id", h.id)
        if (dueAt != null) intent.putExtra("dueAt", dueAt).putExtra("snooze", snooze).putExtra("reminderTime", h.reminder)
        return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
    fun cancel(context: Context, h: Habit) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        alarm.cancel(pending(context, h)); alarm.cancel(pending(context, h, true))
    }
    fun schedule(context: Context, h: Habit, snooze: Boolean = false) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        if (!snooze) alarm.cancel(pending(context, h))
        val now = LocalDateTime.now()
        val next = if (snooze) now.plusMinutes(10) else h.nextReminder(now) ?: return
        val millis = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pi = pending(context, h, snooze, millis)
        try {
            if (exactAllowed(context)) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi)
        } catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, millis, pi) }
    }
    fun sync(context: Context, before: List<Habit>, after: List<Habit>) {
        before.forEach { cancel(context, it) }; after.forEach { schedule(context, it) }
    }
    fun quiet(context: Context): Boolean {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val hour = LocalTime.now().hour
        return prefs.getBoolean("quiet", false) && (hour >= 22 || hour < 7)
    }
}

object VoicePlayback {
    fun start(context: Context, name: String, dueAt: Long, completed: Boolean = false, preview: Boolean = false, token: String = UUID.randomUUID().toString()) {
        if (!ReminderDelivery.isFresh(dueAt, System.currentTimeMillis())) {
            VoiceDiagnostics.record(context, "Late reminder: voice skipped to avoid replaying an old alarm."); return
        }
        if (!ReminderScheduler.notificationsAllowed(context)) {
            VoiceDiagnostics.record(context, "Voice blocked: allow notifications and the Habit reminders channel."); return
        }
        if (!preview && !ReminderScheduler.exactAllowed(context)) {
            VoiceDiagnostics.record(context, "Voice blocked: allow Alarms & reminders (precise reminders) in Android Settings."); return
        }
        try {
            context.startForegroundService(Intent(context, VoiceReminderService::class.java).putExtra("name", name)
                .putExtra("dueAt", dueAt).putExtra("completedTimer", completed).putExtra("token", token))
        } catch (_: Exception) {
            VoiceDiagnostics.record(context, "Android blocked background voice. Allow precise reminders, check battery restrictions, and try Test voice reminder.")
        }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val habits = try { HabitStore(context).load() } catch (_: Exception) { return }
        if (intent.action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) {
            habits.forEach { ReminderScheduler.schedule(context, it) }; return
        }
        val habit = habits.find { it.id == intent.getStringExtra("id") } ?: return
        if (intent.action == "SNOOZE") { ReminderScheduler.schedule(context, habit, true); return }
        if (intent.action != "REMIND") return
        // Capture the delivered occurrence before updating this PendingIntent for the next day.
        val dueAt = intent.getLongExtra("dueAt", 0)
        val snoozed = intent.getBooleanExtra("snooze", false)
        val originalTime = intent.getStringExtra("reminderTime")
        val now = System.currentTimeMillis()
        val key = habit.id + if (snoozed) "/snooze" else "/regular"
        val deliveries = context.getSharedPreferences("reminder_deliveries", Context.MODE_PRIVATE)
        val lastDue = deliveries.getLong(key, 0)
        ReminderScheduler.schedule(context, habit)
        if (dueAt <= 0 || dueAt > now || originalTime != habit.reminder || dueAt <= lastDue) return
        val date = Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault()).toLocalDate()
        if (!habit.scheduled(date) || date in habit.completed || ReminderScheduler.quiet(context)) return
        deliveries.edit().putLong(key, dueAt).commit()
        ReminderScheduler.channel(context)
        if (!ReminderScheduler.notificationsAllowed(context)) {
            if (habit.voice) VoiceDiagnostics.record(context, "Voice blocked: notifications or the Habit reminders channel are disabled.")
            return
        }
        val fresh = ReminderDelivery.isFresh(dueAt, now)
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val snooze = PendingIntent.getBroadcast(context, 0,
            Intent(context, ReminderReceiver::class.java).setAction("SNOOZE").setData(Uri.parse("daybyday://snooze/${Uri.encode(habit.id)}")).putExtra("id", habit.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val start = PendingIntent.getActivity(context, 0,
            Intent(context, TimerActivity::class.java).setData(Uri.parse("daybyday://timer/${Uri.encode(habit.id)}")).putExtra("habitId", habit.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val time = Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEE, h:mm a", Locale.ENGLISH))
        val builder = Notification.Builder(context, if (fresh) ReminderScheduler.CHANNEL else ReminderScheduler.MISSED_CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (fresh) habit.name else "Missed reminder: ${habit.name}")
            .setContentText(if (fresh) "Time for a small step. Open DailyStride to check in." else "Scheduled for $time. Delivered late by Android; no voice replay.")
            .setContentIntent(open).setAutoCancel(true).addAction(Notification.Action.Builder(null, "Remind in 10 min", snooze).build())
        if (habit.durationMinutes > 0) builder.addAction(Notification.Action.Builder(null, "Start", start).build())
        context.getSystemService(NotificationManager::class.java).notify(habit.id, 1, builder.build())
        if (habit.voice) VoicePlayback.start(context, habit.name, dueAt, token = "$key/$dueAt")
    }
}

class VoiceReminderService : Service(), TextToSpeech.OnInitListener {
    private data class Speech(val name: String, val dueAt: Long, val completed: Boolean, val token: String)
    private var tts: TextToSpeech? = null
    private var ready = false
    private var focus: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private val queue = ArrayDeque<Speech>()
    private val seen = mutableSetOf<String>()
    private var speaking = false
    private var wakeLock: PowerManager.WakeLock? = null
    private val timeout = Runnable {
        VoiceDiagnostics.record(this, "Voice timed out. Check the installed Text-to-Speech engine and downloaded voice data.")
        stopSelf()
    }
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("voice_status", "Voice reminder status", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(42, Notification.Builder(this, "voice_status").setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("DailyStride voice reminder").setContentText("Preparing a spoken reminder")
            .setContentIntent(open).setOnlyAlertOnce(true).build())
        val dueAt = intent?.getLongExtra("dueAt", 0) ?: 0
        if (!ReminderDelivery.isFresh(dueAt, System.currentTimeMillis())) {
            VoiceDiagnostics.record(this, "Late reminder: voice skipped to avoid replaying an old alarm.")
            if (!speaking && queue.isEmpty()) stopSelf()
            return START_NOT_STICKY
        }
        if (wakeLock == null) wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "DailyStride:voice").apply { setReferenceCounted(false) }
        if (wakeLock?.isHeld != true) wakeLock?.acquire(60000L)
        val token = intent?.getStringExtra("token") ?: UUID.randomUUID().toString()
        if (seen.add(token) && queue.size < 8) queue.addLast(Speech(intent?.getStringExtra("name") ?: "Your habit", dueAt, intent?.getBooleanExtra("completedTimer", false) ?: false, token))
        if (tts == null) {
            VoiceDiagnostics.record(this, "Preparing the device Text-to-Speech engine…")
            handler.postDelayed(timeout, 25000)
            tts = TextToSpeech(this, this)
        } else if (ready && !speaking) next()
        return START_NOT_STICKY
    }
    override fun onInit(status: Int) {
        handler.post {
            if (tts == null) return@post
            if (status != TextToSpeech.SUCCESS) {
                VoiceDiagnostics.record(this, "Text-to-Speech engine could not initialize. Choose an engine and install a voice in Android Settings.")
                stopSelf()
            } else { ready = true; next() }
        }
    }
    private fun next() {
        handler.removeCallbacks(timeout)
        val engine = tts ?: return
        if (speaking) return
        while (queue.isNotEmpty()) {
            val speech = queue.removeFirst()
            if (!ReminderDelivery.isFresh(speech.dueAt, System.currentTimeMillis())) {
                VoiceDiagnostics.record(this, "Queued reminder expired; no old voice was replayed."); continue
            }
            val audio = getSystemService(AudioManager::class.java)
            val manager = getSystemService(NotificationManager::class.java)
            val blocked = when {
                !ReminderScheduler.notificationsAllowed(this) -> "Notifications or the Habit reminders channel are disabled."
                ReminderScheduler.quiet(this) -> "Quiet hours are active (10 PM–7 AM)."
                manager.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL -> "Do Not Disturb is active."
                audio.ringerMode != AudioManager.RINGER_MODE_NORMAL -> "The phone is in silent or vibrate mode."
                audio.mode != AudioManager.MODE_NORMAL -> "A call or another communication session is active."
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 -> "Media volume is zero. Raise media volume and try again."
                else -> null
            }
            if (blocked != null) { VoiceDiagnostics.record(this, "Voice skipped: $blocked"); continue }
            val prefs = getSharedPreferences("settings", MODE_PRIVATE)
            val hausa = prefs.getBoolean("hausaVoice", false) && engine.setLanguage(Locale.forLanguageTag("ha-NG")) >= TextToSpeech.LANG_AVAILABLE
            if (!hausa && engine.setLanguage(Locale.ENGLISH) < TextToSpeech.LANG_AVAILABLE) {
                VoiceDiagnostics.record(this, "No usable speech voice is installed. Download an English or Hausa voice in Android Text-to-Speech settings."); continue
            }
            // Navigation-guidance speech uses media volume and can duck other audio instead of silently dropping reminders.
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
            focus?.let { audio.abandonAudioFocusRequest(it) }
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs)
                .setOnAudioFocusChangeListener { change ->
                    if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                        VoiceDiagnostics.record(this, "Voice interrupted by another audio session; it will not replay later.")
                        stopSelf()
                    }
                }.build()
            focus = request
            if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                VoiceDiagnostics.record(this, "Android denied audio focus. Try Test voice reminder when no call is active."); continue
            }
            engine.setAudioAttributes(attrs)
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { handler.post { speaking = false; VoiceDiagnostics.record(this@VoiceReminderService, if (hausa) "Voice spoken successfully (Hausa)." else "Voice spoken successfully (English)."); next() } }
                @Deprecated("Platform callback") override fun onError(id: String?) { handler.post { speaking = false; VoiceDiagnostics.record(this@VoiceReminderService, "Speech engine reported an error. Check its installed voice data."); next() } }
            })
            val time = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
            val prefix = if (prefs.getBoolean("sayTime", true)) if (hausa) "Yanzu karfe $time. " else "It is $time. " else ""
            val text = prefix + if (speech.completed) {
                if (hausa) "Lokacin aikin ${speech.name} ya cika. Ka yi kokari!" else "Your focus session for ${speech.name} is complete. Well done!"
            } else if (hausa) "Lokacin ${speech.name} ya yi." else "Time for ${speech.name}."
            speaking = true
            handler.postDelayed(timeout, 25000)
            if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, speech.token) == TextToSpeech.ERROR) {
                speaking = false; handler.removeCallbacks(timeout)
                VoiceDiagnostics.record(this, "Speech could not start. Check the device's TTS engine and voice downloads.")
                continue
            }
            return
        }
        stopSelf()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        queue.clear(); speaking = false; ready = false
        tts?.stop(); tts?.shutdown(); tts = null
        if (wakeLock?.isHeld == true) wakeLock?.release(); wakeLock = null
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
