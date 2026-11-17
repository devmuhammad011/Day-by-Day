package com.example.daybyday

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

object ReminderScheduler {
    const val CHANNEL = "habit_reminders"
    fun channel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Scheduled habit check-ins" })
    }
    fun exactAllowed(context: Context): Boolean = Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    private fun pending(context: Context, h: Habit, snooze: Boolean = false): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).setAction("REMIND")
            .setData(Uri.parse("daybyday://reminder/${Uri.encode(h.id)}/${if (snooze) "snooze" else "regular"}"))
            .putExtra("id", h.id)
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
        val pi = pending(context, h, snooze)
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

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = HabitStore(context)
        val habits = try { store.load() } catch (_: Exception) { return }
        if (intent.action in listOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")) {
            habits.forEach { ReminderScheduler.schedule(context, it) }; return
        }
        val habit = habits.find { it.id == intent.getStringExtra("id") } ?: return
        if (intent.action == "SNOOZE") { ReminderScheduler.schedule(context, habit, true); return }
        ReminderScheduler.schedule(context, habit)
        if (!habit.scheduled(LocalDate.now()) || LocalDate.now() in habit.completed || ReminderScheduler.quiet(context)) return
        ReminderScheduler.channel(context)
        val canNotify = Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!canNotify) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val snooze = PendingIntent.getBroadcast(context, 0,
            Intent(context, ReminderReceiver::class.java).setAction("SNOOZE").setData(Uri.parse("daybyday://snooze/${Uri.encode(habit.id)}")).putExtra("id", habit.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, ReminderScheduler.CHANNEL).setSmallIcon(com.example.daybyday.R.drawable.ic_notification)
            .setContentTitle(habit.name).setContentText("Time for a small step. Open Day by Day to check in.")
            .setContentIntent(open).setAutoCancel(true).addAction(Notification.Action.Builder(null, "Snooze 10 min", snooze).build()).build()
        context.getSystemService(NotificationManager::class.java).notify(habit.id, 1, notification)
        if (habit.voice && ReminderScheduler.exactAllowed(context)) {
            try { context.startForegroundService(Intent(context, VoiceReminderService::class.java).putExtra("name", habit.name)) }
            catch (_: Exception) { /* The normal notification remains available if background speech is restricted. */ }
        }
    }
}

class VoiceReminderService : Service(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var focus: AudioFocusRequest? = null
    private val handler = Handler(Looper.getMainLooper())
    private var taskName = ""
    override fun onBind(intent: Intent?) = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ReminderScheduler.channel(this)
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        startForeground(42, Notification.Builder(this, ReminderScheduler.CHANNEL).setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Day by Day voice reminder").setContentText("Speaking your selected habit reminder")
            .setContentIntent(open).setSilent(true).build())
        taskName = intent?.getStringExtra("name") ?: "Your habit"
        val audio = getSystemService(AudioManager::class.java)
        if (ReminderScheduler.quiet(this) || getSystemService(NotificationManager::class.java).currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL || audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || audio.isMusicActive) { stopSelf(); return START_NOT_STICKY }
        if (tts != null) { stopSelf(); return START_NOT_STICKY }
        handler.postDelayed({ stopSelf() }, 25000)
        tts = TextToSpeech(this, this)
        return START_NOT_STICKY
    }
    override fun onInit(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) { stopSelf(); return }
        val prefs = getSharedPreferences("settings", MODE_PRIVATE)
        val hausa = prefs.getBoolean("hausaVoice", false) && engine.setLanguage(Locale.forLanguageTag("ha-NG")) >= TextToSpeech.LANG_AVAILABLE
        if (!hausa && engine.setLanguage(Locale.ENGLISH) < TextToSpeech.LANG_AVAILABLE) { stopSelf(); return }
        val audio = getSystemService(AudioManager::class.java)
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK).setAudioAttributes(attrs).setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) stopSelf()
        }.build()
        focus = request
        if (audio.requestAudioFocus(request) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) { stopSelf(); return }
        engine.setAudioAttributes(attrs)
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) {}
            override fun onDone(id: String?) { handler.post { stopSelf() } }
            @Deprecated("Platform callback") override fun onError(id: String?) { handler.post { stopSelf() } }
        })
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH))
        val prefix = if (prefs.getBoolean("sayTime", true)) if (hausa) "Yanzu karfe $time. " else "It is $time. " else ""
        val text = prefix + if (hausa) "Lokacin $taskName ya yi." else "Time for $taskName."
        if (engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "habit") == TextToSpeech.ERROR) stopSelf()
    }
    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        tts?.stop(); tts?.shutdown()
        focus?.let { getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
}
