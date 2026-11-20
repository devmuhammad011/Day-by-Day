package com.novastack.daybyday

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter

private val Forest = Color(0xFF061B2A)
private val CardGreen = Color(0xFF112E42)
private val Mint = Color(0xFF00DDD7)
private val Gold = Color(0xFF00E89C)
private val Lilac = Color(0xFF19B691)
private val Muted = Color(0xFFADC6D7)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ReminderScheduler.channel(this)
        setContent { DayByDay(this) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayByDay(activity: MainActivity) {
    val store = remember { HabitStore(activity) }
    val prefs = remember { activity.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE) }
    var guideComplete by remember { mutableStateOf(prefs.getBoolean("guideComplete", false)) }
    if (!guideComplete) {
        WelcomeGuide(onDone = { prefs.edit().putBoolean("guideComplete", true).apply(); guideComplete = true })
        return
    }
    var loadError by remember { mutableStateOf<String?>(null) }
    var habits by remember { mutableStateOf(try { store.load() } catch (_: Exception) { loadError = "Your saved habits could not be read. They have been left untouched. Restore a backup or restart the app."; emptyList() }) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var selected by remember { mutableStateOf(today) }
    var tab by remember { mutableStateOf(0) }
    var headerMenu by remember { mutableStateOf(false) }
    var showAllTasks by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(prefs.getBoolean("dark", true)) }
    var quiet by remember { mutableStateOf(prefs.getBoolean("quiet", false)) }
    var hausa by remember { mutableStateOf(prefs.getBoolean("hausaVoice", false)) }
    var lockTimer by remember { mutableStateOf(prefs.getBoolean("lockTimer", true)) }
    var speakTimerEnd by remember { mutableStateOf(prefs.getBoolean("speakTimerEnd", false)) }
    var sayTime by remember { mutableStateOf(prefs.getBoolean("sayTime", true)) }
    var notifications by remember { mutableStateOf(notificationsAllowed(activity)) }
    var exact by remember { mutableStateOf(ReminderScheduler.exactAllowed(activity)) }
    var editing by remember { mutableStateOf<Habit?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Habit?>(null) }
    var imported by remember { mutableStateOf<List<Habit>?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    fun openExternal(url: String, email: Boolean = false) {
        try {
            val action = if (email) Intent.ACTION_SENDTO else Intent.ACTION_VIEW
            activity.startActivity(Intent(action, Uri.parse(url)))
        } catch (_: android.content.ActivityNotFoundException) {
            message = "No ${if (email) "email app" else "browser"} is available. Open this address on another device: $url"
        } catch (_: SecurityException) {
            message = "Your device blocked opening this address: $url"
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifications = notificationsAllowed(activity) }
    fun save(next: List<Habit>) {
        try {
            check(loadError == null) { "Restore your backup before editing unreadable data." }
            val previousHabits = habits
            store.save(next)
            val activeId = FocusTimer(activity).load()?.habitId
            if (activeId != null && next.none { it.id == activeId }) FocusTimer(activity).clear()
            ReminderScheduler.sync(activity, previousHabits, next)
            habits = next
        } catch (e: Exception) { message = e.message ?: "Could not save changes." }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) try {
            val stream = activity.contentResolver.openOutputStream(uri) ?: error("Cannot open backup file.")
            stream.use { it.write(store.export(habits).toByteArray(Charsets.UTF_8)) }
            message = "Backup saved. Keep it somewhere safe; it contains your habit history."
        } catch (_: Exception) { message = "Could not export backup. Choose another location and try again." }
    }
    val restore = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) try {
            val stream = activity.contentResolver.openInputStream(uri) ?: error("Cannot open file.")
            val bytes = stream.use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (buffer.size() <= 1_048_576) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    buffer.write(chunk, 0, count)
                }
                buffer.toByteArray()
            }
            require(bytes.size <= 1_048_576) { "Backup exceeds 1 MB." }
            imported = store.importBackup(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) { message = "Backup not restored: ${e.message ?: "invalid file"}. Existing data is unchanged." }
    }
    fun refresh() {
        val now = LocalDate.now()
        if (now != today) { today = now; selected = now }
        notifications = notificationsAllowed(activity)
        exact = ReminderScheduler.exactAllowed(activity)
        FocusTimer(activity).load()?.let { session ->
            if (!session.notified && session.clock.remaining(android.os.SystemClock.elapsedRealtime()) == 0L) FocusTimer(activity).finish()
        }
    }
    DisposableEffect(activity) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) {
            refresh()
            if (loadError == null) try { habits = store.load() } catch (_: Exception) { }
            if (loadError == null) habits.forEach { ReminderScheduler.schedule(activity, it) }
        } }
        val saved = activity.getSharedPreferences("habits", android.content.Context.MODE_PRIVATE)
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "items" && loadError == null) try { habits = store.load() } catch (_: Exception) { }
        }
        saved.registerOnSharedPreferenceChangeListener(listener)
        activity.lifecycle.addObserver(observer)
        onDispose { activity.lifecycle.removeObserver(observer); saved.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    LaunchedEffect(Unit) { while (true) { refresh(); delay(30_000) } }
    val existingConflict = remember(habits, today) { ScheduleConflict.firstIn(habits, today) }
    val colors = if (dark) darkColorScheme(primary = Mint, secondary = Gold, background = Forest, surface = CardGreen, onBackground = Color.White, onSurface = Color.White)
        else lightColorScheme(primary = Color(0xFF007F85), secondary = Color(0xFF007D55), background = Color(0xFFF1F8FC), surface = Color.White)
    MaterialTheme(colorScheme = colors) {
        Scaffold(containerColor = colors.background,
            topBar = { TopAppBar(title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(R.drawable.ic_sprout), contentDescription = "DailyStride logo", tint = colors.primary, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.width(8.dp)); Text("DailyStride", fontSize = 25.sp, fontWeight = FontWeight.Bold)
                }
            }, colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background), actions = {
                Box {
                    IconButton(onClick = { headerMenu = true }, modifier = Modifier.semantics { contentDescription = "More options" }) { Text("⋮", fontSize = 30.sp) }
                    DropdownMenu(expanded = headerMenu, onDismissRequest = { headerMenu = false }) {
                        DropdownMenuItem(text = { Text(if (showAllTasks) "Show selected day" else "Show all tasks") }, onClick = { showAllTasks = !showAllTasks; tab = 0; headerMenu = false })
                        DropdownMenuItem(text = { Text("Progress") }, onClick = { tab = 1; headerMenu = false })
                        DropdownMenuItem(text = { Text("Settings & reminders") }, onClick = { tab = 2; headerMenu = false })
                    }
                }
            }) },
            bottomBar = {
                Surface(color = colors.background, shadowElevation = 5.dp) {
                    Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        listOf("Today" to R.drawable.ic_nav_today, "Progress" to R.drawable.ic_nav_progress, "Settings" to R.drawable.ic_nav_settings).forEachIndexed { i, (label, icon) ->
                            val active = tab == i
                            Column(Modifier.weight(1f).background(if (active) colors.primary.copy(alpha = 0.15f) else Color.Transparent, RoundedCornerShape(24.dp))
                                .selectable(selected = active, onClick = { tab = i }, role = Role.Tab).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(painterResource(icon), contentDescription = null, tint = if (active) colors.primary else colors.onBackground.copy(alpha = 0.8f), modifier = Modifier.size(26.dp))
                                Text(label, color = if (active) colors.primary else colors.onBackground.copy(alpha = 0.8f), fontSize = 14.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                            }
                        }
                    }
                }
            }, floatingActionButton = {
                if (tab == 0 && loadError == null) FloatingActionButton(onClick = { editing = null; showEditor = true }, containerColor = colors.primary, contentColor = Forest, shape = CircleShape, modifier = Modifier.size(64.dp).semantics { contentDescription = "Add task" }) { Text("+", fontSize = 40.sp) }
            }
        ) { padding ->
            LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                existingConflict?.let { clash -> item { SurfaceCard {
                    Text("Review overlapping schedules", fontWeight = FontWeight.Bold)
                    Text("An existing schedule overlaps ${clash.habit.name}. Edit its work time, reminder or weekdays to resolve it. Your history is unchanged.")
                } } }
                loadError?.let { error -> item { SurfaceCard { Text(error); Text("Use Settings → Restore backup.") } } }
                if (tab == 0) {
                    item {
                        Text(selected.format(DateTimeFormatter.ofPattern("EEEE, MMM d")), fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp, bottom = 12.dp))
                    }
                    item { DailyOverview(habits, selected, today) }
                    item { WeekSelector(habits, today, selected) { selected = it; showAllTasks = false } }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(if (showAllTasks) "All tasks" else if (selected == today) "Today's tasks" else "Tasks for ${selected.format(DateTimeFormatter.ofPattern("MMM d"))}", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                            TextButton(onClick = { showAllTasks = !showAllTasks }) { Text(if (showAllTasks) "Selected day" else "All tasks", fontSize = 12.sp) }
                        }
                    }
                    val visible = if (showAllTasks) habits else habits.filter { it.scheduled(selected) }
                    if (visible.isEmpty()) item { SurfaceCard { Text(if (habits.isEmpty()) "Your first small step starts here." else "A little breathing room.", fontWeight = FontWeight.Bold); Text("No tasks scheduled for this day. Tap + to add one, or choose All tasks to manage your full list.") } }
                    items(visible, key = { it.id }) { habit ->
                        HabitCard(habit, selected, today, toggle = { save(habits.map { if (it.id == habit.id) it.toggle(selected) else it }) },
                            edit = { editing = habit; showEditor = true }, delete = { deleting = habit })
                    }
                    item {
                        val next = habits.mapNotNull { h -> h.nextReminder(LocalDateTime.now())?.let { h to it } }.minByOrNull { it.second }
                        if (next != null) Text("Next reminder: ${next.first.name} • ${next.second.format(DateTimeFormatter.ofPattern("EEE, h:mm a"))}", color = colors.onBackground.copy(alpha = 0.7f), fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
                    }
                }
                if (tab == 1) {
                    item { Text("Your growth", fontSize = 32.sp, fontWeight = FontWeight.Bold); Text("Consistency is more than a streak.") }
                    item { DailyOverview(habits, today) }
                    item { WeeklyChart(habits, today) }
                    val dates = (0L..29L).map { today.minusDays(it) }
                    val possible = dates.sumOf { d -> habits.count { it.scheduled(d) } }
                    val complete = dates.sumOf { d -> habits.count { it.scheduled(d) && d in it.completed } }
                    item { SurfaceCard { Text("LAST 30 DAYS", color = colors.primary); Text("$complete / $possible check-ins", fontSize = 26.sp, fontWeight = FontWeight.Bold); Text("Missed a day? Your progress still counts. Begin again today.") } }
                    items(habits, key = { it.id }) { h -> SurfaceCard {
                        Text("${h.emoji}  ${h.name}", fontWeight = FontWeight.Bold)
                        Text("${h.streak(today)} scheduled-day streak • ${h.completed.size} lifetime check-ins")
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            (6L downTo 0L).forEach { n ->
                                val d = today.minusDays(n)
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(d.format(DateTimeFormatter.ofPattern("EE")), fontSize = 10.sp)
                                    Text(if (!h.scheduled(d)) "—" else if (d in h.completed) "●" else "○", color = colors.primary, fontSize = 22.sp)
                                }
                            }
                        }
                    } }
                }
                if (tab == 2) {
                    item { Text("Make it yours", fontSize = 32.sp, fontWeight = FontWeight.Bold) }
                    item { SurfaceCard {
                        SettingToggle("Dark ocean theme", "Switch between navy and daylight", dark) { dark = it; prefs.edit().putBoolean("dark", it).apply() }
                    } }
                    item { SurfaceCard {
                        Text("Reminders & voice", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("${if (notifications) "Notifications enabled" else "Notifications are blocked"} • ${if (exact) "Precise timing allowed" else "Timing may be delayed"}", fontSize = 12.sp)
                        Button(onClick = {
                            if (Build.VERSION.SDK_INT >= 33 && !notifications) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            else activity.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, activity.packageName))
                        }) { Text("Notification permission") }
                        if (Build.VERSION.SDK_INT >= 31 && !exact) OutlinedButton(onClick = {
                            try { activity.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${activity.packageName}"))) }
                            catch (_: Exception) { message = "Open phone Settings → Apps → Special app access → Alarms & reminders." }
                        }) { Text("Allow precise reminders") }
                        Text("Enable a time and voice inside each habit. Voice needs notification and precise-alarm access; Android or battery restrictions may still delay delivery.", fontSize = 12.sp)
                        SettingToggle("Timer on lock screen", "Show live countdown and task name; your phone's lock-screen notification settings also apply", lockTimer) {
                            lockTimer = it; prefs.edit().putBoolean("lockTimer", it).apply(); FocusTimer(activity).update()
                        }
                        SettingToggle("Speak when timer ends", "Announce task completion aloud; needs notifications, precise alarms and a working TTS voice", speakTimerEnd) {
                            speakTimerEnd = it; prefs.edit().putBoolean("speakTimerEnd", it).apply()
                        }
                        Text("Tap View timer on its notification to open the large countdown before unlocking. Check-ins still require unlocking. Android controls whether silent notifications appear on your lock screen.", fontSize = 12.sp)
                        SettingToggle("Quiet hours", "No reminders or speech from 10 PM to 7 AM", quiet) { quiet = it; prefs.edit().putBoolean("quiet", it).apply() }
                        SettingToggle("Say the current time", "Include time before the habit name", sayTime) { sayTime = it; prefs.edit().putBoolean("sayTime", it).apply() }
                        SettingToggle("Prefer Hausa voice", "Uses English if your TTS engine lacks Hausa", hausa) { hausa = it; prefs.edit().putBoolean("hausaVoice", it).apply() }
                        OutlinedButton(onClick = {
                            if (!notifications) message = "Allow notifications first to preview the voice."
                            else activity.startForegroundService(Intent(activity, VoiceReminderService::class.java).putExtra("name", if (hausa) "shan ruwa" else "drinking water"))
                        }) { Text("Test voice reminder") }
                        Text("Task names are spoken aloud. Only enable voice for habits you are comfortable hearing in public. Silent mode, quiet hours and active music suppress speech. Your device needs an installed Text-to-Speech voice.", fontSize = 12.sp)
                    } }
                    item { SurfaceCard {
                        Text("Keep your progress safe", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Backups contain habits, schedules and check-in history. They do not contain device preferences. Everything stays on your phone unless you export it.")
                        Button(onClick = { export.launch("day-by-day-${today}.json") }, enabled = loadError == null) { Text("Export backup") }
                        OutlinedButton(onClick = { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }) { Text("Restore backup") }
                        Text("Restoring replaces your current habits after confirmation. Export a backup first.", fontSize = 12.sp)
                    } }
                    item { SurfaceCard {
                        Text("Need a quick tour?", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Replay the guide to habits, schedules, focus timers, voice and backups.")
                        OutlinedButton(onClick = { guideComplete = false }) { Text("Open app guide") }
                    } }
                    item { SurfaceCard {
                        Text("About & support", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("DailyStride by Nova Stack", color = colors.primary)
                        Text("These pages open in your browser and need an internet connection.", fontSize = 12.sp)
                        OutlinedButton(onClick = { openExternal("https://dayly-stride-website.vercel.app/privacy/") }, modifier = Modifier.fillMaxWidth()) { Text("Privacy Policy ↗") }
                        OutlinedButton(onClick = { openExternal("https://dayly-stride-website.vercel.app/terms/") }, modifier = Modifier.fillMaxWidth()) { Text("Terms of Use ↗") }
                        OutlinedButton(onClick = { openExternal("https://dayly-stride-website.vercel.app/support/") }, modifier = Modifier.fillMaxWidth()) { Text("Help & Support ↗") }
                        OutlinedButton(onClick = { openExternal("https://dayly-stride-website.vercel.app/") }, modifier = Modifier.fillMaxWidth()) { Text("Visit DailyStride website ↗") }
                        TextButton(onClick = { openExternal("mailto:mumgloballink@gmail.com?subject=DailyStride%20support", email = true) }) { Text("Email Nova Stack") }
                        Text("mumgloballink@gmail.com", fontSize = 12.sp)
                    } }
                    item { Text("DailyStride • Version 2.8\nSmall steps. Lasting change.", color = colors.primary, modifier = Modifier.padding(vertical = 16.dp)) }
                }
            }
        }
        if (showEditor) HabitEditor(editing, habits, notifications && exact, onDismiss = { showEditor = false }) { name, emoji, days, time, voice, startDate, duration, plannedStart ->
            val previous = editing
            save(if (previous == null) habits + Habit(name = name, emoji = emoji, created = startDate, days = days, reminder = time, voice = voice, durationMinutes = duration, plannedStart = plannedStart)
                else habits.map { if (it.id == previous.id) it.copy(name = name, emoji = emoji, created = startDate, days = days, reminder = time, voice = voice, durationMinutes = duration, plannedStart = plannedStart) else it })
            showEditor = false
            if (time != null && !notifications) message = "Habit saved. Open Settings to allow notifications so reminders can appear."
        }
        deleting?.let { h -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${h.name}?") }, text = { Text("Its check-in history and reminders will be removed. Export a backup first if you want to keep them.") },
            confirmButton = { TextButton(onClick = { save(habits.filterNot { it.id == h.id }); deleting = null }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep habit") } }) }
        imported?.let { next -> AlertDialog(onDismissRequest = { imported = null }, title = { Text("Restore ${next.size} habits?") }, text = { Text("This replaces your current ${habits.size} habits and their history. It cannot be undone without a backup. Voice reminders from the backup will be switched off for privacy; you can enable them individually.") },
            confirmButton = { TextButton(onClick = {
                try {
                    val safe = next.map { it.copy(voice = false) }
                    val previousHabits = habits
                    store.save(safe); FocusTimer(activity).clear(); ReminderScheduler.sync(activity, previousHabits, safe); habits = safe; loadError = null; imported = null; message = "Backup restored."
                } catch (_: Exception) { imported = null; message = "Could not restore backup." }
            }) { Text("Replace and restore") } }, dismissButton = { TextButton(onClick = { imported = null }) { Text("Cancel") } }) }
        message?.let { text -> AlertDialog(onDismissRequest = { message = null }, title = { Text("DailyStride") }, text = { Text(text) }, confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } }) }
    }
}

private fun notificationsAllowed(activity: MainActivity): Boolean = activity.getSystemService(NotificationManager::class.java).areNotificationsEnabled() &&
    (Build.VERSION.SDK_INT < 33 || activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

@Composable
private fun SurfaceCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun CompletionRing(complete: Int, total: Int, diameter: Dp) {
    val progress = if (total == 0) 0f else complete.toFloat() / total
    val colors = MaterialTheme.colorScheme
    val track = colors.primary.copy(alpha = 0.24f)
    Box(Modifier.size(diameter).semantics { contentDescription = "$complete of $total tasks completed, ${(progress * 100).toInt()} percent" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 15.dp.toPx(); val inset = stroke / 2
            val bounds = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), bounds, style = Stroke(stroke))
            if (progress > 0) drawArc(colors.primary, -90f, progress * 360f, false, Offset(inset, inset), bounds, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("${(progress * 100).toInt()}%", fontSize = if (LocalDensity.current.fontScale > 1.3f) 30.sp else (diameter.value * 0.27f).sp, fontWeight = FontWeight.Bold)
            Text("$complete of $total", fontSize = 15.sp, color = colors.onBackground.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun StreakSummary(streak: Int, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier, color = colors.secondary.copy(alpha = 0.16f), shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(painterResource(R.drawable.ic_streak), contentDescription = null, tint = colors.secondary, modifier = Modifier.size(38.dp))
            Text("$streak days", fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("current streak", fontSize = 13.sp, color = colors.onBackground.copy(alpha = 0.8f))
            Text("Best habit", fontSize = 10.sp, color = colors.onBackground.copy(alpha = 0.6f))
        }
    }
}

@Composable
private fun DailyOverview(habits: List<Habit>, today: LocalDate, streakDate: LocalDate = today) {
    val due = habits.filter { it.scheduled(today) }
    val complete = due.count { today in it.completed }
    val streak = habits.maxOfOrNull { it.streak(streakDate) } ?: 0
    val dividerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        val ringDiameter = (maxWidth * 0.48f).coerceAtMost(190.dp)
        if (maxWidth < 280.dp || LocalDensity.current.fontScale > 1.3f) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                CompletionRing(complete, due.size, 184.dp)
                StreakSummary(streak, Modifier.fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                CompletionRing(complete, due.size, ringDiameter)
                Spacer(Modifier.width(14.dp))
                Box(Modifier.width(1.dp).height(130.dp).background(dividerColor))
                Spacer(Modifier.width(14.dp))
                StreakSummary(streak, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WeekSelector(habits: List<Habit>, today: LocalDate, selected: LocalDate, choose: (LocalDate) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (6L downTo 0L).forEach { n ->
            val date = today.minusDays(n); val active = date == selected
            val due = habits.count { it.scheduled(date) }
            val done = habits.count { it.scheduled(date) && date in it.completed }
            val ratio = if (due == 0) 0f else done.toFloat() / due
            Column(Modifier.weight(1f).background(if (active) colors.primary.copy(alpha = 0.08f) else Color.Transparent, RoundedCornerShape(12.dp))
                .selectable(active, onClick = { choose(date) }, role = Role.Tab)
                .semantics { contentDescription = "$date, $done of $due tasks completed" }
                .padding(vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.height(42.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                    Box(Modifier.width(24.dp).height((5 + 37 * ratio).dp).background(if (done == 0) colors.primary.copy(alpha = 0.3f) else if (active) colors.secondary else colors.secondary.copy(alpha = 0.75f), RoundedCornerShape(4.dp)))
                }
                Text(date.format(DateTimeFormatter.ofPattern("EEE")), fontSize = 11.sp, color = colors.onBackground.copy(alpha = if (active) 1f else 0.7f))
                Text("${date.dayOfMonth}", fontSize = 16.sp, color = if (active) colors.primary else colors.onBackground.copy(alpha = 0.75f), fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun HabitCard(h: Habit, selected: LocalDate, today: LocalDate, toggle: () -> Unit, edit: () -> Unit, delete: () -> Unit) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    var menu by remember(h.id) { mutableStateOf(false) }
    val timeFormat = DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH)
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, colors.primary.copy(alpha = 0.13f)), colors = CardDefaults.cardColors(containerColor = colors.surface)) {
        Column(Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = selected in h.completed, onCheckedChange = { toggle() }, enabled = h.scheduled(selected),
                    colors = CheckboxDefaults.colors(checkedColor = colors.secondary, checkmarkColor = colors.background, uncheckedColor = colors.onSurface.copy(alpha = 0.75f)),
                    modifier = Modifier.semantics { contentDescription = "Complete ${h.name} for $selected" })
                Column(Modifier.weight(1f).padding(vertical = 7.dp)) {
                    Text(h.name, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 23.sp)
                    val details = buildList {
                        if (!h.scheduled(selected)) add("Not scheduled on this day")
                        (h.plannedStart ?: h.reminder)?.let { add(LocalTime.parse(it).format(timeFormat)) }
                        if (h.voice) add("Voice reminder")
                    }
                    if (details.isNotEmpty()) Text(details.joinToString(" • "), fontSize = 11.sp, color = colors.onSurface.copy(alpha = 0.65f))
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.semantics { contentDescription = "Options for ${h.name}" }) { Text("⋮", fontSize = 27.sp, color = colors.onSurface.copy(alpha = 0.8f)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Edit") }, onClick = { menu = false; edit() })
                        DropdownMenuItem(text = { Text("Delete", color = colors.error) }, onClick = { menu = false; delete() })
                    }
                }
            }
            if (h.durationMinutes > 0) {
                TextButton(onClick = { context.startActivity(Intent(context, TimerActivity::class.java).putExtra("habitId", h.id)) }, modifier = Modifier.padding(start = 44.dp)) {
                    Text("▶  Start / open timer · ${h.durationMinutes / 60}h ${h.durationMinutes % 60}m", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun WeeklyChart(habits: List<Habit>, today: LocalDate) {
    SurfaceCard {
        Text("Your weekly rhythm", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Completion on scheduled habits", fontSize = 12.sp)
        Row(Modifier.fillMaxWidth().height(154.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Bottom) {
            (6L downTo 0L).forEach { n ->
                val date = today.minusDays(n)
                val due = habits.count { it.scheduled(date) }; val done = habits.count { it.scheduled(date) && date in it.completed }
                val ratio = if (due == 0) 0f else done.toFloat() / due
                Column(Modifier.weight(1f).semantics { contentDescription = "$date: $done of $due completed" }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (due == 0) "—" else "${(ratio * 100).toInt()}%", fontSize = 9.sp)
                    Box(Modifier.width(20.dp).height((6 + 85 * ratio).dp).background(if (n % 3 == 0L) Mint else if (n % 3 == 1L) Gold else Lilac, RoundedCornerShape(8.dp)))
                    Text(date.format(DateTimeFormatter.ofPattern("EE")), fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingToggle(title: String, detail: String, checked: Boolean, change: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.SemiBold); Text(detail, fontSize = 11.sp) }
        Switch(checked = checked, onCheckedChange = change)
    }
}

@Composable
private fun HabitEditor(habit: Habit?, allHabits: List<Habit>, voiceAllowed: Boolean, onDismiss: () -> Unit, onSave: (String, String, Set<Int>, String?, Boolean, LocalDate, Int, String?) -> Unit) {
    var name by remember { mutableStateOf(habit?.name ?: "") }
    var emoji by remember { mutableStateOf(habit?.emoji ?: "🌱") }
    var days by remember { mutableStateOf(habit?.days ?: (1..7).toSet()) }
    var reminder by remember { mutableStateOf(habit?.reminder != null) }
    val context = LocalContext.current
    var planned by remember { mutableStateOf(habit?.plannedStart != null) }
    var plannedTime by remember { mutableStateOf((habit?.plannedStart ?: habit?.reminder)?.let(LocalTime::parse) ?: LocalTime.of(8, 0)) }
    var startDate by remember { mutableStateOf(habit?.created ?: LocalDate.now()) }
    var time by remember { mutableStateOf(habit?.reminder?.let(LocalTime::parse) ?: LocalTime.now().withSecond(0).withNano(0)) }
    val dateValid = habit?.completed?.none { it < startDate } ?: true
    var voice by remember { mutableStateOf(habit?.voice ?: false) }
    var timed by remember { mutableStateOf((habit?.durationMinutes ?: 0) > 0) }
    var durationHours by remember { mutableStateOf(((habit?.durationMinutes?.takeIf { it > 0 } ?: 25) / 60).toString()) }
    var durationMins by remember { mutableStateOf(((habit?.durationMinutes?.takeIf { it > 0 } ?: 25) % 60).toString()) }
    val h = durationHours.toIntOrNull() ?: -1
    val m = durationMins.toIntOrNull() ?: -1
    val durationValid = h in 0..24 && m in 0..59 && (h * 60 + m) in 1..1440
    val candidate = Habit(id = habit?.id ?: "new-habit-preview", name = name, emoji = emoji, created = startDate, days = days,
        reminder = if (reminder) time.toString() else null, durationMinutes = if (timed && durationValid) h * 60 + m else 0,
        plannedStart = if (planned) plannedTime.toString() else null)
    val clash = ScheduleConflict.find(candidate, allHabits)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (habit == null) "A fresh start" else "Edit your ritual") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, label = { Text("Habit name") }, singleLine = true)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("🌱", "💧", "🏃", "📚", "🧘", "💤").forEach { icon -> Text(icon, fontSize = 23.sp, modifier = Modifier.background(if (emoji == icon) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else Color.Transparent, RoundedCornerShape(8.dp)).clickable { emoji = icon }.padding(4.dp)) }
            }
            Text("Start date", fontWeight = FontWeight.Bold)
            OutlinedButton(onClick = {
                DatePickerDialog(context, { _, year, month, day -> startDate = LocalDate.of(year, month + 1, day) },
                    startDate.year, startDate.monthValue - 1, startDate.dayOfMonth).show()
            }, modifier = Modifier.fillMaxWidth()) {
                Text(startDate.format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy")))
            }
            Text("New habits start today by default. Choose another date if you prefer.", fontSize = 11.sp)
            if (!dateValid) Text("Keep the start date on or before your earliest check-in to preserve your history.", color = MaterialTheme.colorScheme.error)
            Text("Repeat on", fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                (1..7).forEach { day ->
                    val active = day in days
                    Box(Modifier.weight(1f).height(44.dp).background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background, RoundedCornerShape(10.dp))
                        .clickable { days = if (active) days - day else days + day }.semantics { contentDescription = "${DayOfWeek.of(day)} ${if (active) "selected" else "not selected"}" }, contentAlignment = Alignment.Center) {
                        Text(DayOfWeek.of(day).name.take(1), color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onBackground)
                    }
                }
            }
            if (days.isEmpty()) Text("Choose at least one day.", color = MaterialTheme.colorScheme.error)
            SettingToggle("Planned work time", "Reserve a start time independently of the reminder", planned) { planned = it }
            if (planned) {
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, hour, min -> plannedTime = LocalTime.of(hour, min) }, plannedTime.hour, plannedTime.minute, false).show()
                }, modifier = Modifier.fillMaxWidth()) { Text("Start: ${plannedTime.format(DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH))}") }
                if (timed && durationValid) Text("Ends ${plannedTime.plusMinutes((h * 60 + m).toLong()).format(DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH))}${if (plannedTime.toSecondOfDay() + (h * 60 + m) * 60 >= 86400) " (next day)" else ""}", fontSize = 12.sp)
            }
            SettingToggle("Focus timer", "Set how long you want to work", timed) { timed = it }
            if (timed) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(durationHours, { durationHours = it.filter(Char::isDigit).take(2) }, label = { Text("Hours") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(durationMins, { durationMins = it.filter(Char::isDigit).take(2) }, label = { Text("Minutes") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                if (!durationValid) Text("Choose 1 minute to 24 hours. Minutes must be 0–59.", color = MaterialTheme.colorScheme.error)
                Text("Tap Start on the habit or its reminder notification to begin. When the timer runs to the end, it automatically checks in the habit for the day you started. Pause and Reset do not complete it.", fontSize = 11.sp)
            }
            if (!planned && reminder) Text("Without a separate work time, the reminder time reserves the start of your focus duration.", fontSize = 11.sp)
            if (clash != null) {
                val format = DateTimeFormatter.ofPattern("EEE, h:mm a", java.util.Locale.ENGLISH)
                Text("Time conflict with ${clash.habit.name}: ${clash.start.format(format)}${if (clash.end != clash.start) " – ${clash.end.format(format)}" else ""}. Choose another time or different weekdays.", color = MaterialTheme.colorScheme.error)
            }
            SettingToggle("Reminder", "One reminder on scheduled days", reminder) { reminder = it }
            if (reminder) {
                Text("Reminder time", fontWeight = FontWeight.Bold)
                OutlinedButton(onClick = {
                    TimePickerDialog(context, { _, hourOfDay, minute -> time = LocalTime.of(hourOfDay, minute) },
                        time.hour, time.minute, false).show()
                }, modifier = Modifier.fillMaxWidth()) {
                    Text(time.format(DateTimeFormatter.ofPattern("h:mm a", java.util.Locale.ENGLISH)), fontSize = 20.sp)
                }
                Text("Choose AM for morning or PM for afternoon/evening. Reminders repeat on your selected weekdays from the start date.", fontSize = 11.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Speak reminder aloud", Modifier.weight(1f))
                    Switch(voice, { voice = it }, enabled = voiceAllowed || voice)
                }
                Text(if (voiceAllowed) "Voice reads this habit name aloud. Only enable it if that is OK in public." else "Enable notifications and precise reminders in Settings before turning on voice.", fontSize = 11.sp)
            }
        } },
        confirmButton = { TextButton(enabled = name.isNotBlank() && days.isNotEmpty() && dateValid && (!timed || durationValid) && clash == null, onClick = {
            onSave(name.trim(), emoji, days, if (reminder) time.format(DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.ROOT)) else null, reminder && voice, startDate, if (timed) h * 60 + m else 0, if (planned) plannedTime.format(DateTimeFormatter.ofPattern("HH:mm", java.util.Locale.ROOT)) else null)
        }) { Text("Save habit") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
