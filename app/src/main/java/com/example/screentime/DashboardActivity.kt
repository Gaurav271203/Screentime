package com.example.screentime

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.ZoneId

class DashboardActivity : Activity() {

    class Week(
        val start: LocalDate,
        val usage: UsageData,
        val notifs: Map<LocalDate, Map<String, Int>>
    )

    private var selected: LocalDate = LocalDate.now()
    private var byCategory = false
    private var week: Week? = null

    private lateinit var tvDate: TextView
    private lateinit var tvCategories: TextView
    private lateinit var timeCard: View
    private lateinit var notifCard: View
    private lateinit var unlockCard: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)
        setupHeader("Dashboard")

        tvDate = findViewById(R.id.tvDate)
        tvCategories = findViewById(R.id.tvCategories)
        tvCategories.setOnClickListener {
            byCategory = !byCategory
            bind()
        }

        val cards = findViewById<LinearLayout>(R.id.cards)
        val inf = LayoutInflater.from(this)
        timeCard = inf.inflate(R.layout.card_series, cards, false)
        notifCard = inf.inflate(R.layout.card_series, cards, false)
        unlockCard = inf.inflate(R.layout.card_series, cards, false)
        cards.addView(timeCard)
        cards.addView(notifCard)
        cards.addView(unlockCard)

        unlockCard.findViewById<View>(R.id.cs_legend).visibility = View.GONE
        unlockCard.findViewById<View>(R.id.cs_divider).visibility = View.GONE
        unlockCard.findViewById<View>(R.id.cs_viewall).visibility = View.GONE

        timeCard.findViewById<View>(R.id.cs_viewall).setOnClickListener { openAll(0) }
        notifCard.findViewById<View>(R.id.cs_viewall).setOnClickListener { openAll(1) }
        notifCard.findViewById<View>(R.id.cs_hint).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        for (c in listOf(timeCard, notifCard, unlockCard)) {
            val chart = c.findViewById<UsageBarChart>(R.id.cs_chart)
            chart.onSelect = { i -> pick(i) }
            chart.onSwipe = { dir -> shiftWeek(dir) }
        }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun openAll(mode: Int) {
        val i = Intent(this, AllAppsActivity::class.java)
        i.putExtra("mode", mode)
        i.putExtra("day", selected.toEpochDay())
        startActivity(i)
    }

    private fun pick(i: Int) {
        val w = week ?: return
        val d = w.start.plusDays(i.toLong())
        if (d.isAfter(LocalDate.now())) return
        selected = d
        bind()
    }

    private fun shiftWeek(dir: Int) {
        val target = selected.plusDays(7L * dir)
        val today = LocalDate.now()
        if (Dates.weekStart(target).isAfter(today)) return
        selected = if (target.isAfter(today)) today else target
        load()
    }

    private fun load() {
        val ctx = applicationContext
        val start = Dates.weekStart(selected)
        Bg.run(this, {
            val usage = UsageRepository.load(ctx, start, start.plusDays(6))
            val z = ZoneId.systemDefault()
            val s = start.atStartOfDay(z).toInstant().toEpochMilli()
            val e = start.plusDays(7).atStartOfDay(z).toInstant().toEpochMilli()
            val notifs = try {
                NotifDb.get(ctx).countsByDay(s, e)
            } catch (ex: Exception) {
                emptyMap()
            }
            for (p in usage.packages()) Apps.get(ctx, p)
            Week(start, usage, notifs)
        }, {
            week = it
            bind()
        })
    }

    private fun bind() {
        val w = week ?: return
        val days = (0..6).map { w.start.plusDays(it.toLong()) }
        val idx = days.indexOf(selected).coerceAtLeast(0)

        tvDate.text = Dates.full(selected)
        tvCategories.text = if (byCategory) "Apps" else "Categories"

        val timeMaps = days.map { w.usage.appsOnDay(it).filter { e -> e.value >= 1000L } }
        val notifMaps = days.map { d -> (w.notifs[d] ?: emptyMap()).mapValues { it.value.toLong() } }

        bindSeries(timeCard, timeMaps, idx, true)
        bindSeries(notifCard, notifMaps, idx, false)
        notifCard.findViewById<View>(R.id.cs_hint).visibility =
            if (NotifAccess.enabled(this)) View.GONE else View.VISIBLE

        // unlocks
        val counts = days.map { w.usage.unlocksOnDay(it) }
        val chart = unlockCard.findViewById<UsageBarChart>(R.id.cs_chart)
        val bars = counts.map { UsageBarChart.Bar(floatArrayOf(it.toFloat()), getColor(R.color.bar_blue)) }
        val grid = ChartScale.grid((counts.maxOrNull() ?: 0).toFloat(), ChartScale.COUNT_STEPS, 0)
        chart.setData(bars, Dates.DAY_LABELS, idx, intArrayOf(getColor(R.color.bar_blue)), grid, { g -> g.toInt().toString() })
        unlockCard.findViewById<TextView>(R.id.cs_big).text = Fmt.bigCount(counts[idx], "unlocks")
    }

    private fun nameOf(key: String): String = if (byCategory) key else Apps.get(this, key).label

    private fun bindSeries(card: View, maps: List<Map<String, Long>>, idx: Int, time: Boolean) {
        val conv: List<Map<String, Long>> = maps.map { m ->
            if (!byCategory) {
                m
            } else {
                val o = HashMap<String, Long>()
                for ((p, v) in m) {
                    val c = Apps.get(this, p).category
                    o[c] = (o[c] ?: 0L) + v
                }
                o
            }
        }
        val sel = conv[idx]
        val top = sel.entries.sortedByDescending { it.value }.take(3).map { it.key }
        val colors = Palette.series(this)

        val bars = conv.map { m ->
            val segs = FloatArray(4)
            var used = 0f
            top.forEachIndexed { i, k ->
                val v = (m[k] ?: 0L).toFloat()
                segs[i] = v
                used += v
            }
            segs[3] = (m.values.sum().toFloat() - used).coerceAtLeast(0f)
            UsageBarChart.Bar(segs)
        }
        val maxV = bars.maxOf { it.segments.sum() }
        val grid = if (time) ChartScale.grid(maxV, ChartScale.TIME_STEPS, 2)
        else ChartScale.grid(maxV, ChartScale.COUNT_STEPS, 0)

        val chart = card.findViewById<UsageBarChart>(R.id.cs_chart)
        chart.setData(bars, Dates.DAY_LABELS, idx, colors, grid, { g ->
            if (time) Fmt.axisTime(g) else g.toInt().toString()
        })

        val total = sel.values.sum()
        card.findViewById<TextView>(R.id.cs_big).text =
            if (time) Fmt.big(total) else Fmt.bigCount(total.toInt(), "notifications")

        val legend = card.findViewById<LinearLayout>(R.id.cs_legend)
        legend.removeAllViews()
        top.forEachIndexed { i, k ->
            val v = sel[k] ?: 0L
            Ui.legendRow(this, legend, colors[i], nameOf(k), if (time) Fmt.spaced(v) else v.toString())
        }
    }
}
