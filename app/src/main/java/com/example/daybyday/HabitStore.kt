package com.example.daybyday

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

class HabitStore(context: Context) {
    private val prefs = context.getSharedPreferences("habits", Context.MODE_PRIVATE)
    fun load(): List<Habit> {
        val raw = prefs.getString("items", null) ?: return listOf(
            Habit(name = "Drink water", emoji = "💧"),
            Habit(name = "Move for 20 minutes", emoji = "🏃"),
            Habit(name = "Read a few pages", emoji = "📚")
        )
        val array = JSONArray(raw)
        return (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            val dates = item.getJSONArray("completed")
            Habit(item.getString("id"), item.getString("name"), item.getString("emoji"),
                LocalDate.parse(item.getString("created")),
                (0 until dates.length()).map { LocalDate.parse(dates.getString(it)) }.toSet())
        }
    }
    fun save(habits: List<Habit>) {
        val array = JSONArray()
        habits.forEach { habit -> array.put(JSONObject().apply {
            put("id", habit.id); put("name", habit.name); put("emoji", habit.emoji)
            put("created", habit.created.toString())
            put("completed", JSONArray(habit.completed.sorted().map { it.toString() }))
        }) }
        prefs.edit().putString("items", array.toString()).apply()
    }
}
