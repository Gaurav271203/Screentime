package com.example.screentime

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import java.time.LocalDate

class HomeActivity : Activity() {

    private lateinit var cardPermission: View
    private lateinit var cards: View
    private lateinit var tvTotal: TextView
    private lateinit var segBar: SegmentBarView
    private lateinit var legend: LinearLayout
    private lateinit var iconsRow: LinearLayout
    private lateinit var tvGoalPill: TextView
    private lateinit var tvRemain: TextView
    private lateinit var tvRemainLabel: TextView
    private lateinit var ring: RingView
    private lateinit var tvBlockState: TextView

    class HomeData(
        val total: Long,
        val apps: List<Pair<String, Long>>,
        val cats: List<Pair<String, Long>>
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        cardPermission = findViewById(R.id.cardPermission)
        cards = findViewById(R.id.cards)
        tvTotal = findViewById(R.id.tvTotal)
        segBar = findViewById(R.id.segBar)
        legend = findViewById(R.id.legend)
        iconsRow = findViewById(R.id.iconsRow)
        tvGoalPill = findViewById(R.id.tvGoalPill)
        tvRemain = findViewById(R.id.tvRemain)
        tvRemainLabel = findViewById(R.id.tvRemainLabel)
        ring = findViewById(R.id.ring)
        tvBlockState = findViewById(R.id.tvBlockState)

        findViewById<View>(R.id.btnGrant).setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }
        findViewById<ImageButton>(R.id.btnWeekly).setOnClickListener {
            startActivity(Intent(this, WeeklyActivity::class.java))
        }
        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener { v ->
            val pm = PopupMenu(this, v)
            pm.menu.add(0, 1, 0, "Weekly report")
            pm.menu.add(0, 2, 1, "Screen time goal")
            pm.menu.add(0, 3, 2, "Block apps at limit")
            pm.menu.add(0, 4, 3, "Notification access")
            pm.menu.add(0, 5, 4, "Clear saved data")
            pm.setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> startActivity(Intent(this, WeeklyActivity::class.java))
                    2 -> startActivity(Intent(this, GoalActivity::class.java))
                    3 -> startActivity(Intent(this, BlockActivity::class.java))
                    4 -> startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    5 -> confirmClear()
                }
                true
            }
            pm.show()
        }
        findViewById<View>(R.id.cardTotal).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }
        findViewById<View>(R.id.cardMost).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
        }
        findViewById<View>(R.id.cardBlock).setOnClickListener {
            startActivity(Intent(this, BlockActivity::class.java))
        }
        findViewById<View>(R.id.btnClear).setOnClickListener { confirmClear() }
        findViewById<View>(R.id.cardGoal).setOnClickListener {
            startActivity(Intent(this, GoalActivity::class.java))
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("Clear saved data?")
            .setMessage(
                "This resets everything to zero and starts counting again from now. " +
                    "Earlier usage and notification counts are removed from this app " +
                    "(Android's own records are not touched). Your goal and settings are kept."
            )
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Clear") { _, _ ->
                val ctx = applicationContext
                Bg.run(this, {
                    UsageRepository.clearSaved(ctx)
                    ScreenTimeWidget.updateAll(ctx)
                    true
                }, {
                    Toast.makeText(this, "Cleared. Counting from now.", Toast.LENGTH_SHORT).show()
                    refresh()
                })
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
        BlockHelper.sync(this)
        refresh()
        Bg.run(this, { ScreenTimeWidget.updateAll(applicationContext); true }, { })
    }

    private fun refresh() {
        if (!UsageRepository.hasAccess(this)) {
            cardPermission.visibility = View.VISIBLE
            cards.visibility = View.GONE
            return
        }
        cardPermission.visibility = View.GONE
        cards.visibility = View.VISIBLE
        val ctx = applicationContext
        Bg.run(this, {
            val today = LocalDate.now()
            val u = UsageRepository.load(ctx, today, today)
            val map = u.appsOnDay(today).filter { it.value >= 1000L }
            val apps = map.entries.sortedByDescending { it.value }.map { it.key to it.value }
            for (a in apps) Apps.get(ctx, a.first)
            HomeData(apps.sumOf { it.second }, apps, Apps.categoryTotals(ctx, map))
        }, { bind(it) })
    }

    private fun bindBlockState() {
        val g = Prefs.goalMinutes(this)
        tvBlockState.text = when {
            g <= 0 -> "Set a goal to use"
            Prefs.blockEnabled(this) -> "On \u00b7 blocks after " + Fmt.spaced(g * 60_000L)
            else -> "Off"
        }
    }

    private fun bind(d: HomeData) {
        bindBlockState()
        tvTotal.text = Fmt.big(d.total)

        val colors = Palette.series(this)
        val top = d.cats.take(3)
        val segs = ArrayList<Pair<Float, Int>>()
        top.forEachIndexed { i, c -> segs.add(c.second.toFloat() to colors[i]) }
        val rest = d.total - top.sumOf { it.second }
        if (rest > 0) segs.add(rest.toFloat() to colors[3])
        segBar.setData(segs)

        legend.removeAllViews()
        top.forEachIndexed { i, c -> Ui.legendRow(this, legend, colors[i], c.first, Fmt.spaced(c.second)) }

        Ui.appIcons(this, iconsRow, d.apps.take(3).map { Apps.get(this, it.first) to it.second })

        val g = Prefs.goalMinutes(this)
        if (g > 0) {
            val gm = g * 60_000L
            tvGoalPill.text = "Goal " + Fmt.spaced(gm)
            val remain = gm - d.total
            if (remain >= 0) {
                tvRemain.text = Fmt.big(remain)
                tvRemainLabel.text = "Remaining"
            } else {
                tvRemain.text = Fmt.big(-remain)
                tvRemainLabel.text = "Over goal"
            }
            ring.setData(d.total.toFloat() / gm.toFloat())
        } else {
            tvGoalPill.text = "Set goal"
            tvRemain.text = "No goal"
            tvRemainLabel.text = "Tap to set one"
            ring.setData(0f)
        }
    }
}
