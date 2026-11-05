package com.example.screentime

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** Floating two-tab switcher (Screen time | Tasks) shown on the two main screens. */
object TabBar {
    fun attach(a: Activity, tasksSelected: Boolean) {
        val d = a.resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()
        val content = a.findViewById<ViewGroup>(android.R.id.content)
        if (content.findViewWithTag<android.view.View>("tabbar") != null) return

        // keep the last bit of content clear of the bar
        content.getChildAt(0)?.let { it.setPadding(it.paddingLeft, it.paddingTop, it.paddingRight, it.paddingBottom + dp(84f)) }

        val bar = LinearLayout(a)
        bar.tag = "tabbar"
        bar.orientation = LinearLayout.HORIZONTAL
        bar.setPadding(dp(5f), dp(5f), dp(5f), dp(5f))
        bar.elevation = dp(8f).toFloat()
        bar.background = GradientDrawable().apply {
            cornerRadius = dp(30f).toFloat()
            setColor(0xFF2C2C2E.toInt())
        }

        fun tab(label: String, selected: Boolean, go: () -> Unit): TextView {
            val t = TextView(a)
            t.text = label
            t.textSize = 14f
            t.gravity = Gravity.CENTER
            t.setTextColor(if (selected) Color.WHITE else 0xFF9B9B9F.toInt())
            t.setPadding(dp(22f), dp(10f), dp(22f), dp(10f))
            if (selected) t.background = GradientDrawable().apply {
                cornerRadius = dp(25f).toFloat()
                setColor(0xFF3A78F2.toInt())
            }
            if (!selected) t.setOnClickListener { go() }
            return t
        }
        bar.addView(tab("Screen time", !tasksSelected) { open(a, HomeActivity::class.java) })
        bar.addView(tab("Tasks", tasksSelected) { open(a, TasksActivity::class.java) })

        val lp = FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
        lp.bottomMargin = dp(22f)
        content.addView(bar, lp)
    }

    private fun open(a: Activity, cls: Class<*>) {
        a.startActivity(Intent(a, cls).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION))
        a.overridePendingTransition(0, 0)
    }
}
