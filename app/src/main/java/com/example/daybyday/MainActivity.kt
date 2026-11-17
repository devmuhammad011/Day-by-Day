package com.example.daybyday

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay

private val Green = Color(0xFF316447)
private val Cream = Color(0xFFF7F8F2)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = HabitStore(this)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = Cream, surface = Color.White)) {
                HabitApp(store, lifecycle)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HabitApp(store: HabitStore, lifecycle: Lifecycle) {
    var habits by remember { mutableStateOf(store.load()) }
    var today by remember { mutableStateOf(LocalDate.now()) }
    var selected by remember { mutableStateOf(today) }
    var editing by remember { mutableStateOf<Habit?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Habit?>(null) }
    var history by remember { mutableStateOf(false) }
    fun refreshDate() {
        val date = LocalDate.now()
        if (today != date) { today = date; selected = date }
    }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) refreshDate() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { while (true) { refreshDate(); delay(30_000) } }
    fun update(next: List<Habit>) { store.save(next); habits = next }
    val eligible = habits.filter { !it.created.isAfter(selected) }
    val done = eligible.count { selected in it.completed }
    Scaffold(
        containerColor = Cream,
        topBar = { TopAppBar(title = { Text("day by day", fontWeight = FontWeight.Bold) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Cream),
            actions = { TextButton(onClick = { history = !history }) { Text(if (history) "Daily habits" else "Progress") } }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = { editing = null; showEditor = true },
            containerColor = Green, contentColor = Color.White) { Text("+  New habit") } }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Text(today.format(DateTimeFormatter.ofPattern("EEEE, MMMM d")), color = Green)
                Spacer(Modifier.height(12.dp))
                Text(if (history) "Small steps. Real growth." else "Make time for you.", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text("A little better, every day.", color = Color.DarkGray, modifier = Modifier.padding(top = 6.dp))
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = Green), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(if (history) "LAST 7 DAYS" else if (selected == today) "TODAY’S MOMENTUM" else selected.format(DateTimeFormatter.ofPattern("MMM d")), color = Color.White)
                        val week = (0L..6L).map { today.minusDays(it) }
                        val opportunities = week.sumOf { date -> habits.count { !it.created.isAfter(date) } }
                        val total = week.sumOf { date -> habits.count { date in it.completed } }
                        Text(if (history) "$total of $opportunities completed" else "$done of ${eligible.size} habits complete", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(progress = { if (history) { if (opportunities == 0) 0f else total.toFloat() / opportunities } else { if (eligible.isEmpty()) 0f else done.toFloat() / eligible.size } },
                            modifier = Modifier.fillMaxWidth(), color = Color(0xFFC5E6A5), trackColor = Color(0xFF537E64))
                        Text(if (eligible.isNotEmpty() && done == eligible.size && !history) "You showed up. Enjoy that feeling." else "Every check-in is a step forward.", color = Color.White)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (6L downTo 0L).forEach { offset ->
                        val date = today.minusDays(offset)
                        val active = date == selected
                        Column(Modifier.weight(1f).background(if (active) Green else Color.White, RoundedCornerShape(16.dp))
                            .clickable { selected = date; history = false }.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(date.format(DateTimeFormatter.ofPattern("EE")), fontSize = 11.sp, color = if (active) Color.White else Color.DarkGray)
                            Text("${date.dayOfMonth}", color = if (active) Color.White else Green, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            item { Text(if (history) "Your weekly rhythm" else "Your daily habits", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
            if (habits.isEmpty()) item {
                Text("Your next chapter starts with one small habit. Tap + New habit to begin.", modifier = Modifier.padding(vertical = 20.dp))
            } else if (eligible.isEmpty() && !history) item { Text("No habits existed on this day. Choose a more recent date.") }
            items(if (history) habits else eligible, key = { it.id }) { habit ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(habit.emoji, fontSize = 28.sp, modifier = Modifier.padding(end = 12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(habit.name, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                                Text("${habit.streak(today)} day streak", color = Green, fontSize = 13.sp)
                            }
                            if (!history) Checkbox(checked = selected in habit.completed, onCheckedChange = {
                                update(habits.map { if (it.id == habit.id) it.toggle(selected) else it })
                            })
                        }
                        if (history) Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            (6L downTo 0L).forEach { offset ->
                                val date = today.minusDays(offset)
                                Text(if (date < habit.created) "—" else if (date in habit.completed) "●" else "○",
                                    color = Green, fontSize = 23.sp)
                            }
                        }
                        Row {
                            TextButton(onClick = { editing = habit; showEditor = true }) { Text("Edit") }
                            TextButton(onClick = { deleting = habit }) { Text("Delete", color = Color(0xFF9C3C32)) }
                        }
                    }
                }
            }
        }
    }
    if (showEditor) HabitEditor(editing, onDismiss = { showEditor = false }, onSave = { name, emoji ->
        val previous = editing
        update(if (previous == null) habits + Habit(name = name, emoji = emoji) else habits.map {
            if (it.id == previous.id) it.copy(name = name, emoji = emoji) else it
        })
        showEditor = false
    })
    deleting?.let { habit ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Delete ${habit.name}?") },
            text = { Text("This removes the habit and its check-in history permanently.") },
            confirmButton = { TextButton(onClick = { update(habits.filterNot { it.id == habit.id }); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Keep habit") } })
    }
}

@Composable
private fun HabitEditor(habit: Habit?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(habit?.name ?: "") }
    var emoji by remember { mutableStateOf(habit?.emoji ?: "🌱") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (habit == null) "Start something good" else "Edit habit") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Keep it small. Make it daily.")
                OutlinedTextField(value = name, onValueChange = { name = it.take(60) }, singleLine = true, label = { Text("Habit name") })
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("🌱", "💧", "🏃", "📚", "🧘", "💤").forEach { icon ->
                        Text(icon, fontSize = 24.sp, modifier = Modifier.background(if (emoji == icon) Color(0xFFDDEBD9) else Color.Transparent, RoundedCornerShape(8.dp))
                            .clickable { emoji = icon }.padding(5.dp))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name.trim(), emoji) }, enabled = name.isNotBlank()) { Text("Save habit") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
