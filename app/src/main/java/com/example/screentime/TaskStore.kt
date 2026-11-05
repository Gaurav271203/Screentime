package com.example.screentime

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** A daily habit / to-do. [doneDay] is the epoch day it was last ticked, so it resets itself every morning. */
data class Task(val id: Long, val title: String, val minute: Int, val doneDay: Long) {
    val doneToday: Boolean get() = doneDay == LocalDate.now().toEpochDay()
}

object TaskStore {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("tasks", Context.MODE_PRIVATE)

    fun all(c: Context): List<Task> {
        val raw = sp(c).getString("list", "[]") ?: "[]"
        val out = ArrayList<Task>()
        try {
            val a = JSONArray(raw)
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(Task(o.getLong("id"), o.getString("title"), o.getInt("minute"), o.optLong("doneDay", -1)))
            }
        } catch (_: Exception) {
        }
        return out.sortedBy { it.minute }
    }

    private fun save(c: Context, list: List<Task>) {
        val a = JSONArray()
        for (t in list) {
            a.put(JSONObject().put("id", t.id).put("title", t.title).put("minute", t.minute).put("doneDay", t.doneDay))
        }
        sp(c).edit().putString("list", a.toString()).apply()
    }

    fun add(c: Context, title: String, minute: Int) {
        save(c, all(c) + Task(System.currentTimeMillis(), title, minute, -1))
    }

    fun update(c: Context, id: Long, title: String, minute: Int) {
        save(c, all(c).map { if (it.id == id) it.copy(title = title, minute = minute) else it })
    }

    fun delete(c: Context, id: Long) {
        save(c, all(c).filter { it.id != id })
    }

    fun setDone(c: Context, id: Long, done: Boolean) {
        val today = LocalDate.now().toEpochDay()
        save(c, all(c).map { if (it.id == id) it.copy(doneDay = if (done) today else -1) else it })
    }

    fun pending(c: Context): List<Task> = all(c).filter { !it.doneToday }

    fun fmt(minute: Int): String {
        val h = minute / 60
        val m = minute % 60
        val h12 = if (h % 12 == 0) 12 else h % 12
        return "$h12:" + m.toString().padStart(2, '0') + if (h < 12) " am" else " pm"
    }
}
