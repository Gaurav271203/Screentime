package com.example.screentime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Choose an app, then set its daily timer. */
class PickAppActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_allowed)
        setupHeader("Choose app")

        val list = findViewById<LinearLayout>(R.id.list)
        val ctx = applicationContext
        Bg.run(this, {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .filter { it != ctx.packageName }
                .map { Apps.get(ctx, it) }
                .sortedBy { it.label.lowercase() }
        }, { apps ->
            val inf = LayoutInflater.from(this)
            val existing = Prefs.timers(this)
            list.removeAllViews()
            for (a in apps) {
                val row = inf.inflate(R.layout.item_app_row, list, false)
                row.findViewById<ImageView>(R.id.icon).setImageDrawable(a.icon(this))
                row.findViewById<TextView>(R.id.name).text = a.label
                row.findViewById<TextView>(R.id.time).text =
                    existing[a.pkg]?.let { Fmt.spaced(it * 60_000L) } ?: ""
                row.setOnClickListener {
                    TimerDialog.show(this, "Timer · " + a.label, existing[a.pkg] ?: 30, false,
                        { m ->
                            if (m > 0) {
                                Prefs.setTimer(this, a.pkg, m)
                                BlockHelper.ensureOverlay(this)
                                BlockHelper.sync(this)
                            }
                            finish()
                        }, { })
                }
                list.addView(row)
            }
        })
    }
}
