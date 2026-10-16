package com.example.screentime

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.ZoneId

/** "View all" list for a day: every app with its screen time or notification count. */
class AllAppsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_all_apps)

        val mode = intent.getIntExtra("mode", 0)
        val day = LocalDate.ofEpochDay(intent.getLongExtra("day", LocalDate.now().toEpochDay()))
        setupHeader(if (mode == 0) "Screen time" else "Notifications")
        findViewById<TextView>(R.id.tvDay).text = Dates.full(day)

        val list = findViewById<LinearLayout>(R.id.list)
        val ctx = applicationContext
        Bg.run(this, {
            val m: Map<String, Long> = if (mode == 0) {
                UsageRepository.load(ctx, day, day).appsOnDay(day).filter { it.value >= 1000L }
            } else {
                val z = ZoneId.systemDefault()
                val s = day.atStartOfDay(z).toInstant().toEpochMilli()
                val e = day.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli()
                (NotifDb.get(ctx).countsByDay(s, e)[day] ?: emptyMap()).mapValues { it.value.toLong() }
            }
            val rows = m.entries.sortedByDescending { it.value }.map { Apps.get(ctx, it.key) to it.value }
            rows
        }, { rows ->
            list.removeAllViews()
            val inf = LayoutInflater.from(this)
            if (rows.isEmpty()) {
                val t = TextView(this)
                t.text = "Nothing recorded for this day."
                t.setTextColor(getColor(R.color.text_secondary))
                t.textSize = 16f
                t.setPadding(0, Ui.dpi(this, 12f), 0, Ui.dpi(this, 12f))
                list.addView(t)
            }
            for ((meta, v) in rows) {
                val row = inf.inflate(R.layout.item_app_row, list, false)
                row.findViewById<ImageView>(R.id.icon).setImageDrawable(meta.icon(this))
                row.findViewById<TextView>(R.id.name).text = meta.label
                row.findViewById<TextView>(R.id.time).text = if (mode == 0) Fmt.spaced(v) else v.toString()
                list.addView(row)
            }
        })
    }
}
