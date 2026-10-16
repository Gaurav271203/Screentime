package com.example.screentime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.CheckBox
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Choose which apps stay usable after the daily limit is reached. */
class AllowedAppsActivity : Activity() {

    private val chosen = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_allowed)
        setupHeader("Allowed apps")

        val list = findViewById<LinearLayout>(R.id.list)
        val ctx = applicationContext
        Bg.run(this, {
            val pm = ctx.packageManager
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(intent, 0)
                .map { it.activityInfo.packageName }
                .distinct()
                .map { Apps.get(ctx, it) }
                .sortedBy { it.label.lowercase() }
            Pair(apps, BlockHelper.allowed(ctx))
        }, { (apps, allowed) ->
            chosen.clear()
            chosen.addAll(allowed)
            val inf = LayoutInflater.from(this)
            list.removeAllViews()
            for (a in apps) {
                val row = inf.inflate(R.layout.item_allowed, list, false)
                row.findViewById<ImageView>(R.id.icon).setImageDrawable(a.icon(this))
                row.findViewById<TextView>(R.id.name).text = a.label
                val cb = row.findViewById<CheckBox>(R.id.check)
                cb.isChecked = a.pkg in chosen
                val locked = a.pkg == packageName
                if (locked) cb.isEnabled = false
                cb.setOnCheckedChangeListener { _, on ->
                    if (on) chosen.add(a.pkg) else chosen.remove(a.pkg)
                    Prefs.setAllowedApps(this, chosen)
                }
                row.setOnClickListener { if (!locked) cb.toggle() }
                list.addView(row)
            }
        })
    }
}
