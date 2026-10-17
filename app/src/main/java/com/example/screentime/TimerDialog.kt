package com.example.screentime

import android.app.Activity
import android.app.Dialog
import android.graphics.drawable.ColorDrawable
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.TextView

/** Bottom-sheet style hh:mm picker used for per-app timers. */
object TimerDialog {

    fun show(
        a: Activity,
        title: String,
        currentMin: Int,
        canDelete: Boolean,
        onDone: (Int) -> Unit,
        onDelete: () -> Unit
    ) {
        val dlg = Dialog(a)
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dlg.setContentView(R.layout.dialog_goal)
        dlg.findViewById<TextView>(R.id.dlgTitle).text = title

        val ph = dlg.findViewById<NumberPicker>(R.id.pickH)
        val pm = dlg.findViewById<NumberPicker>(R.id.pickM)
        ph.minValue = 0
        ph.maxValue = 23
        ph.value = (currentMin / 60).coerceIn(0, 23)
        pm.minValue = 0
        pm.maxValue = 59
        pm.value = currentMin % 60
        style(a, ph)
        style(a, pm)

        if (!canDelete) {
            dlg.findViewById<View>(R.id.btnDelete).visibility = View.GONE
            dlg.findViewById<View>(R.id.sep1).visibility = View.GONE
        }
        dlg.findViewById<View>(R.id.btnDelete).setOnClickListener {
            dlg.dismiss()
            onDelete()
        }
        dlg.findViewById<View>(R.id.btnCancel).setOnClickListener { dlg.dismiss() }
        dlg.findViewById<View>(R.id.btnDone).setOnClickListener {
            val m = ph.value * 60 + pm.value
            dlg.dismiss()
            onDone(m)
        }

        dlg.window?.let {
            it.setBackgroundDrawable(ColorDrawable(0))
            it.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            it.setGravity(Gravity.BOTTOM)
        }
        dlg.show()
    }

    private fun style(a: Activity, p: NumberPicker) {
        p.setFormatter { String.format("%02d", it) }
        p.setTextColor(a.getColor(R.color.text_primary))
        p.setTextSize(Ui.dp(a, 36f))
        (p.getChildAt(0) as? EditText)?.filters = emptyArray<InputFilter>()
        try {
            val f = NumberPicker::class.java.getDeclaredField("mSelectionDivider")
            f.isAccessible = true
            f.set(p, ColorDrawable(0))
        } catch (_: Throwable) {
        }
    }
}
