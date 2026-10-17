package com.example.screentime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate

/** Lists the per-app daily timers with "used/limit"; tap one to change or delete it. */
class TimersActivity : Activity() {

    private lateinit var list: LinearLayout
    private lateinit var tvEmpty: TextView
    private var used: Map<String, Long> = emptyMap()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_timers)
        setupHeader("App timers")
        list = findViewById(R.id.list)
        tvEmpty = findViewById(R.id.tvEmpty)
        findViewById<View>(R.id.btnAddTimer).setOnClickListener {
            startActivity(Intent(this, PickAppActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        val ctx = applicationContext
        Bg.run(this, {
            val t = LocalDate.now()
            val m = UsageRepository.load(ctx, t, t).appsOnDay(t)
            for (p in Prefs.timers(ctx).keys) Apps.get(ctx, p)
            m
        }, {
            used = it
            bind()
        })
    }

    private fun bind() {
        val timers = Prefs.timers(this)
        list.removeAllViews()
        tvEmpty.visibility = if (timers.isEmpty()) View.VISIBLE else View.GONE
        val inf = LayoutInflater.from(this)
        val light = getColor(R.color.text_secondary)
        val sorted = timers.entries.sortedByDescending {
            (used[it.key] ?: 0L).toFloat() / (it.value * 60_000f)
        }
        for ((pkg, min) in sorted) {
            val meta = Apps.get(this, pkg)
            val row = inf.inflate(R.layout.item_app_row, list, false)
            row.findViewById<ImageView>(R.id.icon).setImageDrawable(meta.icon(this))
            row.findViewById<TextView>(R.id.name).text = meta.label
            row.findViewById<TextView>(R.id.time).text =
                Fmt.withLimit(used[pkg] ?: 0L, min * 60_000L, light)
            row.setOnClickListener {
                TimerDialog.show(this, "Timer · " + meta.label, min, true,
                    { m ->
                        Prefs.setTimer(this, pkg, m)
                        changed()
                    },
                    {
                        Prefs.setTimer(this, pkg, 0)
                        changed()
                    })
            }
            list.addView(row)
        }
    }

    private fun changed() {
        BlockHelper.ensureOverlay(this)
        BlockHelper.sync(this)
        bind()
    }
}
