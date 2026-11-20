package com.novastack.daybyday

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class GuidePage(val label: String, val title: String, val detail: String, val tip: String)
private val guidePages = listOf(
    GuidePage("WELCOME", "Small steps.\nLasting change.", "Build a daily rhythm with habits, thoughtful reminders and focused time. DailyStride brings your next small step into view.", "Your dashboard uses your real check-ins. Start small and let your progress grow."),
    GuidePage("PLAN YOUR DAY", "A little structure.\nRoom to breathe.", "Tap + Add habit. Choose a start date, weekdays and a planned work time. Add a duration to reserve time for the task, and choose the required reminder time.", "Overlapping work or reminders are flagged before saving. The reminder time can be separate from the work start."),
    GuidePage("FOCUS", "One task.\nOne moment.", "Enable Focus timer in a habit and choose hours and minutes. Tap Start on the habit or its reminder to begin. Pause, resume or reset whenever you need. A timer that runs to the end checks in your scheduled habit automatically.", "The timer keeps time with the screen off. View timer on its lock-screen notification opens the full countdown when your phone allows it."),
    GuidePage("REMINDERS & VOICE", "A gentle nudge.\nRight on time.", "Set reminder times in your habits. In Settings, allow notifications and precise reminders. Voice reminders start enabled for new tasks once permissions are allowed; turn them off per task if you prefer. Speak when timer ends is a separate option.", "Voice is optional and needs an installed TTS voice. Quiet hours, silent mode and Do Not Disturb suppress speech. Lock-screen timer names may be visible; you can switch this off in Settings."),
    GuidePage("YOUR PROGRESS", "Keep the wins.\nKeep moving.", "Check off completed habits and explore your weekly progress. Use Settings → Export backup to save your habits and history; Restore backup brings them back.", "No account is required. Keep exported backups private, and save one before reinstalling or changing phones. You can replay this guide in Settings.")
)

@Composable
fun WelcomeGuide(onDone: () -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val item = guidePages[page]
    val mint = Color(0xFF00DDD7)
    val forest = Color(0xFF061B2A)
    BackHandler(enabled = page > 0) { page-- }
    MaterialTheme(colorScheme = darkColorScheme(primary = mint, background = forest, surface = Color(0xFF112E42))) {
        Surface(Modifier.fillMaxSize(), color = forest) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.ic_sprout), contentDescription = null, tint = mint, modifier = Modifier.size(32.dp))
                        Text("DailyStride", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = onDone) { Text("Skip") }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    Spacer(Modifier.height(10.dp))
                    GuideIllustration(page)
                    Text(item.label, color = mint, fontSize = 11.sp, letterSpacing = 2.sp)
                    Text(item.title, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
                    Text(item.detail, fontSize = 16.sp, lineHeight = 24.sp, color = Color(0xFFDFEAF1))
                    Surface(color = Color(0xFF112E42), shape = RoundedCornerShape(20.dp)) {
                        Text(item.tip, Modifier.padding(18.dp), fontSize = 13.sp, lineHeight = 20.sp, color = Color(0xFFADC6D7))
                    }
                    Spacer(Modifier.height(12.dp))
                }
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp).semantics { contentDescription = "Guide page ${page + 1} of ${guidePages.size}" }, horizontalArrangement = Arrangement.Center) {
                    guidePages.indices.forEach { i -> Box(Modifier.padding(horizontal = 4.dp).size(if (page == i) 10.dp else 7.dp).background(if (page == i) mint else Color(0xFF31546B), CircleShape)) }
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (page > 0) OutlinedButton(onClick = { page-- }, modifier = Modifier.height(54.dp)) { Text("Back") }
                    Button(onClick = { if (page == guidePages.lastIndex) onDone() else page++ }, modifier = Modifier.weight(1f).height(54.dp)) {
                        Text(if (page == guidePages.lastIndex) "Start my stride" else "Next", fontSize = 17.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideIllustration(page: Int) {
    Surface(Modifier.fillMaxWidth().heightIn(min = 184.dp), shape = RoundedCornerShape(30.dp), color = Color(0xFF112E42)) {
        when (page) {
            0 -> Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_sprout), contentDescription = "DailyStride sprout logo", tint = Color.Unspecified, modifier = Modifier.size(150.dp))
            }
            1 -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("A PLAN WITH SPACE TO GROW", color = Color(0xFF00DDD7), fontSize = 11.sp)
                Text("8:00 AM   →   Morning run", fontWeight = FontWeight.Bold)
                HorizontalDivider(color = Color(0xFF31546B))
                Text("9:30 AM   →   Read a few pages", fontWeight = FontWeight.Bold)
                Text("Work time + reminder time", color = Color(0xFFADC6D7), fontSize = 12.sp)
            }
            2 -> Box(contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(140.dp)) {
                    val width = 9.dp.toPx()
                    val offset = Offset(width / 2, width / 2)
                    val bounds = Size(size.width - width, size.height - width)
                    drawArc(Color(0xFF31546B), 0f, 360f, false, offset, bounds, style = Stroke(width))
                    drawArc(Color(0xFF00DDD7), -90f, 250f, false, offset, bounds, style = Stroke(width, cap = StrokeCap.Round))
                }
                Text("Focus", fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            3 -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("YOUR REMINDERS, YOUR WAY", color = Color(0xFF00DDD7), fontSize = 11.sp)
                Text("Notification  •  Voice  •  Quiet hours", fontWeight = FontWeight.Bold)
                Text("Start    |    Remind in 10 min", color = Color(0xFF00E89C))
                Text("Choose what works for you.", fontSize = 13.sp)
            }
            else -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("YOUR DATA, YOUR PROGRESS", color = Color(0xFF00DDD7), fontSize = 11.sp)
                Text("✓  Check in\n↗  Review your progress\n↓  Export a backup", fontSize = 19.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}
