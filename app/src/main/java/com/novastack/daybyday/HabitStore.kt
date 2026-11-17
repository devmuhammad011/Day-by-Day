package com.novastack.daybyday

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime

class HabitStore(context: Context) {
    private val prefs = context.getSharedPreferences("habits", Context.MODE_PRIVATE)
    fun load(): List<Habit> {
        val raw = prefs.getString("items", null)
        if (raw != null) return decodeArray(JSONArray(raw))
        val initial = listOf(Habit(name = "Drink water", emoji = "💧"), Habit(name = "Move for 20 minutes", emoji = "🏃"), Habit(name = "Read a few pages", emoji = "📚"))
        save(initial)
        return initial
    }
    fun save(habits: List<Habit>) {
        check(prefs.edit().putString("items", encode(habits).toString()).commit()) { "Could not save habits. Please try again." }
    }
    fun export(habits: List<Habit>): String = JSONObject().put("app", "DayByDay").put("version", 3).put("habits", encode(habits)).toString(2)
    fun importBackup(raw: String): List<Habit> {
        require(raw.toByteArray().size <= 1_048_576) { "Backup is larger than 1 MB." }
        val obj = JSONObject(raw)
        require(obj.optString("app") == "DayByDay" && obj.getInt("version") in 1..3) { "Not a supported Day by Day backup." }
        return decodeArray(obj.getJSONArray("habits"))
    }
    private fun encode(habits: List<Habit>) = JSONArray().apply {
        habits.forEach { h -> put(JSONObject().apply {
            put("id", h.id); put("name", h.name); put("emoji", h.emoji); put("created", h.created.toString())
            put("completed", JSONArray(h.completed.sorted().map { it.toString() })); put("days", JSONArray(h.days.sorted()))
            put("reminder", h.reminder ?: JSONObject.NULL); put("voice", h.voice); put("durationMinutes", h.durationMinutes)
        }) }
    }
    private fun decodeArray(array: JSONArray): List<Habit> {
        require(array.length() <= 1000) { "Too many habits in backup." }
        val result = (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            val id = o.getString("id"); val name = o.getString("name"); val emoji = o.getString("emoji")
            require(id.isNotBlank() && id.length <= 100 && name.isNotBlank() && name.length <= 60 && emoji.length <= 24) { "Invalid habit details." }
            val created = LocalDate.parse(o.getString("created"))
            val dates = o.getJSONArray("completed")
            require(dates.length() <= 40000) { "Too many check-ins." }
            val completed = (0 until dates.length()).map { LocalDate.parse(dates.getString(it)) }.toSet()
            require(completed.all { it >= created }) { "Check-in predates habit creation." }
            val d = o.optJSONArray("days")
            val days = if (d == null) (1..7).toSet() else (0 until d.length()).map { d.getInt(it) }.toSet()
            require(days.isNotEmpty() && days.all { it in 1..7 }) { "Choose at least one valid weekday." }
            val reminder = if (o.isNull("reminder")) null else o.getString("reminder")
            if (reminder != null) LocalTime.parse(reminder)
            val duration = o.optInt("durationMinutes", 0)
            require(duration in 0..1440) { "Duration must be between 1 minute and 24 hours, or disabled." }
            Habit(id, name, emoji, created, completed, days, reminder, o.optBoolean("voice", false), duration)
        }
        require(result.map { it.id }.distinct().size == result.size) { "Duplicate habit IDs." }
        return result
    }
}
