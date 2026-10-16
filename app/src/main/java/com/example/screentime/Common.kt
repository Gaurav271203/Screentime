package com.example.screentime

import android.app.Activity
import android.view.View
import android.widget.TextView
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.Executors

object Bg {
    private val ex = Executors.newFixedThreadPool(2)

    fun <T : Any> run(a: Activity, work: () -> T, ui: (T) -> Unit) {
        ex.execute {
            val r: T? = try {
                work()
            } catch (e: Exception) {
                null
            }
            if (r != null) {
                a.runOnUiThread {
                    if (!a.isDestroyed && !a.isFinishing) ui(r)
                }
            }
        }
    }
}

object Dates {
    val DAY_LABELS = listOf("M", "T", "W", "T", "F", "S", "S")

    fun weekStart(d: LocalDate): LocalDate = d.minusDays((d.dayOfWeek.value - 1).toLong())

    fun full(d: LocalDate): String =
        d.format(DateTimeFormatter.ofPattern("EEE, d MMMM", Locale.getDefault()))

    fun short(d: LocalDate): String =
        d.format(DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault()))
}

fun Activity.setupHeader(title: String, menu: ((View) -> Unit)? = null) {
    findViewById<TextView>(R.id.hdrTitle).text = title
    findViewById<View>(R.id.hdrBack).setOnClickListener { finish() }
    val m = findViewById<View>(R.id.hdrMenu)
    if (menu == null) {
        m.visibility = View.GONE
    } else {
        m.setOnClickListener { menu(it) }
    }
}
