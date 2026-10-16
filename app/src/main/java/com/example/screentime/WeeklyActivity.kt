package com.example.screentime

import android.app.Activity
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.PopupMenu
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.time.temporal.IsoFields
import kotlin.math.abs
import kotlin.math.max

class WeeklyActivity : Activity() {

    class WData(
        val start: LocalDate,
        val usage: UsageData,
        val prevCats: Map<String, Long>
    )

    private var start: LocalDate = Dates.weekStart(LocalDate.now())
    private var page = 0
    private var data: WData? = null

    private lateinit var tvRange: TextView
    private lateinit var btnNext: ImageButton
    private lateinit var tvAvg: TextView
    private lateinit var chart: UsageBarChart
    private lateinit var rowGoal: View
    private lateinit var tvGoalDays: TextView
    private lateinit var cardCat: View
    private lateinit var imgCat: ImageView
    private lateinit var tvCatName: TextView
    private lateinit var tvCatTotal: TextView
    private lateinit var tvCatPrev: TextView
    private lateinit var catApps: LinearLayout
    private lateinit var cardPeak: View
    private lateinit var tvPeakBig: TextView
    private lateinit var tvPeakLabel: TextView
    private lateinit var tvPeakCat: TextView
    private lateinit var heat: HeatmapView
    private lateinit var dots: List<View>
    private lateinit var mostIcons: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_weekly)
        setupHeader("Weekly report") { v ->
            val pm = PopupMenu(this, v)
            pm.menu.add(0, 1, 0, "Screen time goal")
            pm.setOnMenuItemClickListener {
                startActivity(Intent(this, GoalActivity::class.java))
                true
            }
            pm.show()
        }

        tvRange = findViewById(R.id.tvRange)
        btnNext = findViewById(R.id.btnNextWeek)
        tvAvg = findViewById(R.id.tvAvg)
        chart = findViewById(R.id.chart)
        rowGoal = findViewById(R.id.rowGoal)
        tvGoalDays = findViewById(R.id.tvGoalDays)
        cardCat = findViewById(R.id.cardCat)
        imgCat = findViewById(R.id.imgCat)
        tvCatName = findViewById(R.id.tvCatName)
        tvCatTotal = findViewById(R.id.tvCatTotal)
        tvCatPrev = findViewById(R.id.tvCatPrev)
        catApps = findViewById(R.id.catApps)
        cardPeak = findViewById(R.id.cardPeak)
        tvPeakBig = findViewById(R.id.tvPeakBig)
        tvPeakLabel = findViewById(R.id.tvPeakLabel)
        tvPeakCat = findViewById(R.id.tvPeakCat)
        heat = findViewById(R.id.heat)
        dots = listOf(findViewById(R.id.dot1), findViewById(R.id.dot2), findViewById(R.id.dot3))
        mostIcons = findViewById(R.id.mostIcons)

        findViewById<View>(R.id.btnPrevWeek).setOnClickListener { shiftWeek(-1) }
        btnNext.setOnClickListener { shiftWeek(1) }
        chart.onSwipe = { dir -> shiftWeek(dir) }

        val gd = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (abs(vx) > abs(vy) && abs(vx) > 400f) {
                    changePage(if (vx < 0) 1 else -1)
                    return true
                }
                return false
            }
        })
        cardPeak.setOnTouchListener { _, ev -> gd.onTouchEvent(ev) }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun shiftWeek(dir: Int) {
        val target = start.plusDays(7L * dir)
        if (target.isAfter(LocalDate.now())) return
        start = target
        page = 0
        load()
    }

    private fun changePage(dir: Int) {
        val np = page + dir
        if (np < 0 || np >= pageCount) return
        page = np
        bind()
    }

    private var pageCount = 1

    private fun load() {
        val ctx = applicationContext
        val s = start
        Bg.run(this, {
            val usage = UsageRepository.load(ctx, s.minusDays(21), s.plusDays(6))
            val prev = HashMap<String, Long>()
            for (i in 1..21) {
                for ((p, v) in usage.appsOnDay(s.minusDays(i.toLong()))) prev[p] = (prev[p] ?: 0L) + v
            }
            val prevCats = Apps.categoryTotals(ctx, prev).toMap()
            for (p in usage.packages()) Apps.get(ctx, p)
            WData(s, usage, prevCats)
        }, {
            data = it
            bind()
        })
    }

    private fun bind() {
        val d = data ?: return
        val today = LocalDate.now()
        val days = (0..6).map { d.start.plusDays(it.toLong()) }
        val weekNo = d.start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

        tvRange.text = Dates.short(d.start) + " - " + Dates.short(d.start.plusDays(6)) + " (Week " + weekNo + ")"
        val canNext = !d.start.plusDays(7).isAfter(today)
        btnNext.alpha = if (canNext) 1f else 0.3f

        // --- daily average + bar chart + goal
        val totals = days.map { d.usage.totalOnDay(it) }
        val elapsed = days.count { !it.isAfter(today) }.coerceAtLeast(1)
        tvAvg.text = Fmt.big(totals.sum() / elapsed)

        val g = Prefs.goalMinutes(this)
        val goalMs = if (g > 0) g * 60_000f else -1f
        val since = LocalDate.ofEpochDay(Prefs.goalSince(this))
        val red = getColor(R.color.red)
        val blue = getColor(R.color.bar_blue)
        val over = BooleanArray(7) { i ->
            goalMs > 0f && !days[i].isAfter(today) && !days[i].isBefore(since) && totals[i] > goalMs
        }
        val bars = totals.mapIndexed { i, t ->
            UsageBarChart.Bar(floatArrayOf(t.toFloat()), if (over[i]) red else blue)
        }
        val maxV = max((totals.maxOrNull() ?: 0L).toFloat(), goalMs)
        val grid = ChartScale.grid(maxV, ChartScale.TIME_STEPS, 2)
        chart.setData(
            bars, Dates.DAY_LABELS, -1, intArrayOf(blue), grid,
            { v -> Fmt.axisTime(v) }, goalMs, if (g > 0) over else null
        )
        if (g > 0) {
            rowGoal.visibility = View.VISIBLE
            val ok = days.indices.count { i ->
                days[i].isBefore(today) && !days[i].isBefore(since) && totals[i] <= goalMs
            }
            tvGoalDays.text = if (ok == 1) "1 day" else "$ok days"
        } else {
            rowGoal.visibility = View.GONE
        }

        // --- category / apps
        val appTotals = HashMap<String, Long>()
        for (day in days) {
            for ((p, v) in d.usage.appsOnDay(day)) appTotals[p] = (appTotals[p] ?: 0L) + v
        }
        val cats = Apps.categoryTotals(this, appTotals)
        val topApps = appTotals.entries.filter { it.value >= 1000L }
            .sortedByDescending { it.value }.map { it.key to it.value }

        if (cats.isEmpty()) {
            cardCat.visibility = View.GONE
        } else {
            cardCat.visibility = View.VISIBLE
            val (cat, catMs) = cats[0]
            tvCatName.text = cat
            tvCatTotal.text = Fmt.big(catMs)
            imgCat.setImageResource(if (cat == "Social") R.drawable.ic_cat_social else R.drawable.ic_cat_generic)
            val avg = (d.prevCats[cat] ?: 0L) / 3L
            val first = Fmt.spaced(avg)
            val sp = SpannableString("$first used on average for previous 3 weeks")
            sp.setSpan(StyleSpan(Typeface.BOLD), 0, first.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sp.setSpan(ForegroundColorSpan(getColor(R.color.text_primary)), 0, first.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            tvCatPrev.text = sp
            val inCat = topApps.filter { Apps.get(this, it.first).category == cat }.take(3)
            Ui.appRows(this, catApps, inCat.map { Apps.get(this, it.first) to it.second })
        }

        // --- peak usage (3 swipeable pages)
        pageCount = 1 + minOf(2, cats.size)
        if (page >= pageCount) page = 0
        val catName: String? = if (page == 0) null else cats[page - 1].first
        val filter: ((String) -> Boolean)? =
            catName?.let { c -> { p: String -> Apps.get(this, p).category == c } }

        val matrix = Array(7) { r ->
            IntArray(24) { h -> ((d.usage.hourMs(days[r], h, filter) + 59_999L) / 60_000L).toInt() }
        }
        var sum = 0L
        for (r in 0 until 7) for (h in 0 until 24) sum += d.usage.hourMs(days[r], h, filter)
        tvPeakBig.text = Fmt.big(sum)
        if (page == 0) {
            tvPeakLabel.visibility = View.GONE
            tvPeakCat.text = "Total screen time"
        } else {
            tvPeakLabel.visibility = View.VISIBLE
            tvPeakLabel.text = if (page == 1) "Most used category" else "2nd most used category"
            tvPeakCat.text = catName
        }
        heat.setData(matrix)
        dots.forEachIndexed { i, v ->
            v.visibility = if (i < pageCount) View.VISIBLE else View.GONE
            v.backgroundTintList = ColorStateList.valueOf(
                getColor(if (i == page) R.color.text_primary else R.color.pill)
            )
        }

        // --- most used apps
        Ui.appIcons(this, mostIcons, topApps.take(3).map { Apps.get(this, it.first) to it.second })
    }
}
