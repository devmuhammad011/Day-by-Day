package com.novastack.daybyday

import android.os.Bundle
import android.app.KeyguardManager
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalContext
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.util.Locale

class TimerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val showOnLockScreen = getSharedPreferences("settings", MODE_PRIVATE).getBoolean("lockTimer", true)
        if (android.os.Build.VERSION.SDK_INT >= 27) setShowWhenLocked(showOnLockScreen)
        else if (showOnLockScreen) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED)
        val timer = FocusTimer(this)
        val requested = try { HabitStore(this).load().find { it.id == intent.getStringExtra("habitId") && it.durationMinutes > 0 } } catch (_: Exception) { null }
        val existing = timer.load()
        if (existing != null && !existing.notified && existing.clock.remaining(SystemClock.elapsedRealtime()) == 0L) timer.finish()
        var replacement = false
        if (requested != null) {
            getSystemService(android.app.NotificationManager::class.java).cancel(requested.id, 1)
            if (existing == null || existing.clock.remaining(SystemClock.elapsedRealtime()) == 0L) timer.start(requested)
            else if (existing.habitId != requested.id) replacement = true
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF77D9B0), background = Color(0xFF082E27), surface = Color(0xFF163F36))) {
                var replace by remember { mutableStateOf(replacement) }
                if (replace && requested != null) AlertDialog(onDismissRequest = { replace = false }, title = { Text("Replace your active timer?") },
                    text = { Text("Only one focus timer runs at a time. Starting ${requested.name} will discard the current session.") },
                    confirmButton = { TextButton(onClick = { timer.clear(); timer.start(requested); replace = false }) { Text("Replace timer") } },
                    dismissButton = { TextButton(onClick = { replace = false }) { Text("Keep current") } })
                TimerScreen(timer, onBack = { finish() }, onComplete = {
                    val keyguard = getSystemService(KeyguardManager::class.java)
                    if (keyguard.isKeyguardLocked) {
                        keyguard.requestDismissKeyguard(this, null)
                        return@TimerScreen "Unlock your phone, then tap Retry check-in to save your check-in."
                    }
                    val finishedSession = timer.load()
                    val id = finishedSession?.habitId
                    val store = HabitStore(this)
                    try {
                        val old = store.load()
                        val today = finishedSession?.startedOn ?: LocalDate.now()
                        val h = old.find { it.id == id } ?: return@TimerScreen "This habit was removed. Your timer is complete."
                        if (!h.scheduled(today)) return@TimerScreen "This habit is not scheduled for $today. Session complete; no check-in was changed."
                        val next = old.map { if (it.id == id) it.copy(completed = it.completed + today) else it }
                        store.save(next); timer.recordCheckIn(); ReminderScheduler.sync(this, old, next)
                        "Habit checked in for $today. Well done!"
                    } catch (_: Exception) { "Could not save your check-in. Try again from the habit list." }
                })
            }
        }
    }
}

private fun timeText(ms: Long): String {
    val seconds = (ms.coerceAtLeast(0) + 999) / 1000
    return String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
}

@Composable
private fun TimerScreen(timer: FocusTimer, onBack: () -> Unit, onComplete: () -> String) {
    var session by remember { mutableStateOf(timer.load()) }
    var remaining by remember { mutableLongStateOf(session?.clock?.remaining(SystemClock.elapsedRealtime()) ?: 0) }
    var confirmReset by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val owner = LocalContext.current as ComponentActivity
    var visible by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ -> visible = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        while (true) {
            session = timer.load()
            remaining = session?.clock?.remaining(SystemClock.elapsedRealtime()) ?: 0
            if (session != null && remaining == 0L && session?.notified == false) timer.finish()
            delay(if (session?.clock?.running == true) 32 else 200)
        }
    }
    val s = session
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            TextButton(onClick = onBack, modifier = Modifier.align(Alignment.Start)) { Text("‹ Back to habits") }
            Text("FOCUS SESSION", color = MaterialTheme.colorScheme.primary, letterSpacing = 3.sp, fontSize = 12.sp)
            Text(s?.name ?: "No timer selected", fontSize = 28.sp, fontWeight = FontWeight.Bold)
            if (s != null) {
                Text("SET DURATION  ${timeText(s.totalMs)}", color = Color(0xFFB2CBC1), fontSize = 13.sp)
                Box(Modifier.fillMaxWidth().aspectRatio(1f).semantics { contentDescription = "Remaining ${timeText(remaining)}" }, contentAlignment = Alignment.Center) {
                    val ratio = (remaining.toFloat() / s.totalMs.coerceAtLeast(1)).coerceIn(0f, 1f)
                    Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                        val width = 12.dp.toPx(); val offset = Offset(width / 2, width / 2); val bounds = Size(size.width - width, size.height - width)
                        drawArc(Color(0xFF245248), -90f, 360f, false, offset, bounds, style = Stroke(width))
                        drawArc(Color(0xFF77D9B0), -90f, ratio * 360, false, offset, bounds, style = Stroke(width, cap = StrokeCap.Round))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(timeText(remaining), fontSize = 36.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                        Text(if (remaining == 0L) "SESSION COMPLETE" else if (s.clock.running) "STAY WITH THIS MOMENT" else "PAUSED", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize()) {
                                drawArc(Color(0xFFB59BE8), -90f, (remaining % 1000) / 1000f * 360f, false, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                            }
                            Text(String.format(Locale.ROOT, "%03d", remaining % 1000), fontSize = 17.sp, fontFamily = FontFamily.Monospace)
                        }
                        Text("MILLISECONDS", fontSize = 8.sp)
                    }
                }
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp)).padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("ELAPSED", fontSize = 10.sp); Text(timeText(((s.totalMs - remaining).coerceAtLeast(0) / 1000) * 1000), fontSize = 18.sp, fontFamily = FontFamily.Monospace) }
                    Column { Text("REMAINING", fontSize = 10.sp); Text(timeText(remaining), fontSize = 18.sp, fontFamily = FontFamily.Monospace) }
                }
                if (remaining > 0L) Button(onClick = { timer.toggle(); session = timer.load() }, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (s.clock.running) "Pause session" else "Resume session") }
                else {
                    Text(s.completionNote.ifBlank { "Finishing your session…" }, color = MaterialTheme.colorScheme.primary)
                    if (!s.autoChecked) Button(onClick = { message = onComplete(); session = timer.load() }, modifier = Modifier.fillMaxWidth()) { Text("Retry check-in") }
                }
                OutlinedButton(onClick = { confirmReset = true }, modifier = Modifier.fillMaxWidth()) { Text("Reset timer") }
                Text("View timer from its lock-screen notification. The timer keeps time when the screen is off. Allow notifications and precise alarms for timely completion alerts. After a reboot, it pauses for you to review.", color = Color(0xFFB2CBC1), fontSize = 11.sp)
            } else Text("Choose a habit with a focus duration, then tap Start.")
        }
    }
    if (confirmReset) AlertDialog(onDismissRequest = { confirmReset = false }, title = { Text("Reset this session?") }, text = { Text("Elapsed session time will be discarded. Your habit history is unchanged.") },
        confirmButton = { TextButton(onClick = { timer.reset(); session = timer.load(); confirmReset = false }) { Text("Reset") } }, dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } })
    message?.let { text -> AlertDialog(onDismissRequest = { message = null }, text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }) }
}
