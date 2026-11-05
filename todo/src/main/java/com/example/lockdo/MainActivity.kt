package com.example.lockdo

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    private lateinit var list: LinearLayout
    private lateinit var sub: TextView

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(22f), dp(56f), dp(22f), 0)

        val title = TextView(this)
        title.text = "Today"
        title.setTextColor(Color.WHITE)
        title.textSize = 34f
        title.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        sub = TextView(this)
        sub.setTextColor(0xFF9B9B9F.toInt())
        sub.textSize = 14f
        sub.setPadding(0, dp(2f), 0, dp(18f))

        list = LinearLayout(this)
        list.orientation = LinearLayout.VERTICAL
        list.setPadding(0, 0, 0, dp(110f))
        val scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.addView(list)

        col.addView(title)
        col.addView(sub)
        col.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(col, FrameLayout.LayoutParams(-1, -1))

        val fab = TextView(this)
        fab.text = "+"
        fab.setTextColor(Color.WHITE)
        fab.textSize = 30f
        fab.gravity = Gravity.CENTER
        fab.setBackgroundResource(R.drawable.bg_fab)
        fab.setOnClickListener { edit(null) }
        val lp = FrameLayout.LayoutParams(dp(60f), dp(60f), Gravity.BOTTOM or Gravity.END)
        lp.setMargins(0, 0, dp(24f), dp(36f))
        root.addView(fab, lp)

        setContentView(root)

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, p: Array<out String>, r: IntArray) {
        Notifier.refresh(this)
    }

    override fun onResume() {
        super.onResume()
        bind()
        Notifier.refresh(this)
    }

    private fun bind() {
        val all = Store.all(this)
        val pending = all.filter { !it.doneToday }
        val done = all.filter { it.doneToday }
        sub.text = if (all.isEmpty()) "Tap + to add your first task"
        else "${done.size} of ${all.size} done"
        list.removeAllViews()
        for (t in pending + done) list.addView(row(t))
    }

    private fun row(t: Task): View {
        val r = LinearLayout(this)
        r.orientation = LinearLayout.HORIZONTAL
        r.gravity = Gravity.CENTER_VERTICAL
        r.setBackgroundResource(R.drawable.bg_card)
        r.setPadding(dp(14f), dp(14f), dp(18f), dp(14f))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(10f)
        r.layoutParams = lp

        val box = ImageView(this)
        box.setImageResource(if (t.doneToday) R.drawable.ic_circle_done else R.drawable.ic_circle)
        box.setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
        box.setOnClickListener {
            Store.setDone(this, t.id, !t.doneToday)
            bind()
            Notifier.refresh(this)
        }
        r.addView(box, LinearLayout.LayoutParams(dp(40f), dp(40f)))

        val name = TextView(this)
        name.text = t.title
        name.textSize = 17f
        name.setPadding(dp(10f), 0, dp(8f), 0)
        if (t.doneToday) {
            name.setTextColor(0xFF6E6E73.toInt())
            name.paintFlags = name.paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        } else {
            name.setTextColor(Color.WHITE)
        }
        r.addView(name, LinearLayout.LayoutParams(0, -2, 1f))

        val time = TextView(this)
        time.text = Store.fmt(t.minute)
        time.textSize = 14f
        time.setTextColor(0xFF9B9B9F.toInt())
        r.addView(time)

        name.setOnClickListener { edit(t) }
        time.setOnClickListener { edit(t) }
        return r
    }

    private fun edit(t: Task?) {
        var minute = t?.minute ?: 9 * 60
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(dp(24f), dp(8f), dp(24f), 0)

        val et = EditText(this)
        et.hint = "What do you want to do?"
        et.setText(t?.title ?: "")
        et.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        et.setSingleLine()
        box.addView(et)

        val tb = TextView(this)
        tb.textSize = 17f
        tb.setTextColor(0xFF5B8DEF.toInt())
        tb.setPadding(0, dp(18f), 0, dp(8f))
        fun showTime() { tb.text = "At  " + Store.fmt(minute) }
        showTime()
        tb.setOnClickListener {
            TimePickerDialog(this, { _, h, m -> minute = h * 60 + m; showTime() }, minute / 60, minute % 60, false).show()
        }
        box.addView(tb)

        val b = AlertDialog.Builder(this)
            .setTitle(if (t == null) "New task" else "Edit task")
            .setView(box)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val s = et.text.toString().trim()
                if (s.isNotEmpty()) {
                    if (t == null) Store.add(this, s, minute) else Store.update(this, t.id, s, minute)
                    bind()
                    Notifier.refresh(this)
                }
            }
        if (t != null) {
            b.setNeutralButton("Delete") { _, _ ->
                Store.delete(this, t.id)
                bind()
                Notifier.refresh(this)
            }
        }
        b.show()
    }
}
