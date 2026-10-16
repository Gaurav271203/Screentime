package com.example.screentime

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.NumberPicker
import android.widget.TextView
import java.time.LocalDate

class GoalActivity : Activity() {

    private lateinit var tvRange: TextView
    private lateinit var calendar: LinearLayout
    private lateinit var tvPill: TextView
    private lateinit var tvRemain: TextView
    private lateinit var tvRemainLabel: TextView
    private lateinit var ring: RingView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_goal)
        setupHeader("Screen time goal")

        tvRange = findViewById(R.id.tvRange)
        calendar = findViewById(R.id.calendar)
        tvPill = findViewById(R.id.tvPill)
        tvRemain = findViewById(R.id.tvRemain)
        tvRemainLabel = findViewById(R.id.tvRemainLabel)
        ring = findViewById(R.id.ring)

        tvPill.setOnClickListener { showDialog() }
    }

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        val ctx = applicationContext
        val today = LocalDate.now()
        val start = today.minusDays(27)
        Bg.run(this, {
            val u = UsageRepository.load(ctx, start, today)
            (0..27).map { u.totalOnDay(start.plusDays(it.toLong())) }
        }, { bind(start, today, it) })
    }

    private fun bind(start: LocalDate, today: LocalDate, totals: List<Long>) {
        tvRange.text = Dates.short(start) + " – " + Dates.short(today)

        val g = Prefs.goalMinutes(this)
        val goalMs = if (g > 0) g * 60_000L else -1L
        val since = LocalDate.ofEpochDay(Prefs.goalSince(this))

        // calendar
        calendar.removeAllViews()
        val cells = ArrayList<LocalDate?>()
        repeat(start.dayOfWeek.value - 1) { cells.add(null) }
        var d = start
        while (!d.isAfter(today)) {
            cells.add(d)
            d = d.plusDays(1)
        }
        while (cells.size % 7 != 0) cells.add(null)

        for (r in 0 until cells.size / 7) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            for (c in 0 until 7) {
                val day = cells[r * 7 + c]
                val v = DayRingView(this)
                if (day != null) {
                    val t = totals[(day.toEpochDay() - start.toEpochDay()).toInt()]
                    val frac = if (goalMs > 0 && !day.isBefore(since)) t.toFloat() / goalMs.toFloat() else -1f
                    v.setData(String.format("%02d", day.dayOfMonth), frac, day == today)
                }
                row.addView(v, LinearLayout.LayoutParams(0, Ui.dpi(this, 56f), 1f))
            }
            calendar.addView(row, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // today
        val used = totals.last()
        if (g > 0) {
            tvPill.text = "Goal " + Fmt.spaced(goalMs)
            val remain = goalMs - used
            if (remain >= 0) {
                tvRemain.text = Fmt.big(remain)
                tvRemainLabel.text = "Remaining"
            } else {
                tvRemain.text = Fmt.big(-remain)
                tvRemainLabel.text = "Over goal"
            }
            ring.setData(used.toFloat() / goalMs.toFloat(), Fmt.spaced(used))
        } else {
            tvPill.text = "Set goal"
            tvRemain.text = Fmt.big(used)
            tvRemainLabel.text = "Used today"
            ring.setData(0f, Fmt.spaced(used))
        }
    }

    private fun style(p: NumberPicker) {
        p.setFormatter { String.format("%02d", it) }
        p.setTextColor(getColor(R.color.text_primary))
        p.setTextSize(Ui.dp(this, 36f))
        (p.getChildAt(0) as? EditText)?.filters = emptyArray<InputFilter>()
        try {
            val f = NumberPicker::class.java.getDeclaredField("mSelectionDivider")
            f.isAccessible = true
            f.set(p, ColorDrawable(0))
        } catch (_: Throwable) {
        }
    }

    private fun showDialog() {
        val dlg = Dialog(this)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setContentView(R.layout.dialog_goal)

        val cur = Prefs.goalMinutes(this).let { if (it > 0) it else 90 }
        val ph = dlg.findViewById<NumberPicker>(R.id.pickH)
        val pm = dlg.findViewById<NumberPicker>(R.id.pickM)
        ph.minValue = 0
        ph.maxValue = 23
        ph.value = cur / 60
        pm.minValue = 0
        pm.maxValue = 59
        pm.value = cur % 60
        style(ph)
        style(pm)

        dlg.findViewById<View>(R.id.btnDelete).setOnClickListener {
            Prefs.clearGoal(this)
            dlg.dismiss()
            load()
        }
        dlg.findViewById<View>(R.id.btnCancel).setOnClickListener { dlg.dismiss() }
        dlg.findViewById<View>(R.id.btnDone).setOnClickListener {
            val m = ph.value * 60 + pm.value
            if (m <= 0) Prefs.clearGoal(this) else Prefs.setGoal(this, m)
            dlg.dismiss()
            load()
        }

        dlg.window?.let {
            it.setBackgroundDrawable(ColorDrawable(0))
            it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            it.setGravity(Gravity.BOTTOM)
        }
        dlg.show()
    }
}
