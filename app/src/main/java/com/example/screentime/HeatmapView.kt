package com.example.screentime

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** 7 rows (Mon..Sun) x 24 hour cells. Each cell holds minutes used in that hour (0..60). */
class HeatmapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var m: Array<IntArray> = Array(7) { IntArray(24) }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val dash = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val rect = RectF()
    private val days = arrayOf("M", "T", "W", "T", "F", "S", "S")

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun baseline(p: Paint, cy: Float) = cy - (p.descent() + p.ascent()) / 2f

    fun setData(data: Array<IntArray>) {
        m = data
        invalidate()
    }

    private fun colorFor(min: Int): Int = context.getColor(
        when {
            min <= 0 -> R.color.heat_empty
            min <= 15 -> R.color.heat1
            min <= 30 -> R.color.heat2
            min <= 45 -> R.color.heat3
            else -> R.color.heat4
        }
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val rightW = dp(22f)
        val bottomH = dp(28f)
        val gap = dp(7f)
        val rowsW = width - rightW
        val rowH = (height - bottomH - gap * 6f) / 7f
        val cellW = rowsW / 24f

        for (r in 0 until 7) {
            val top = r * (rowH + gap)
            rect.set(0f, top, rowsW, top + rowH)
            path.reset()
            path.addRoundRect(rect, rowH / 2f, rowH / 2f, Path.Direction.CW)

            canvas.save()
            canvas.clipPath(path)
            fill.color = context.getColor(R.color.heat_row)
            canvas.drawRect(rect, fill)
            val inset = dp(0.6f)
            for (c in 0 until 24) {
                val x = c * cellW
                fill.color = colorFor(m[r][c])
                canvas.drawRect(x + inset, top, x + cellW - inset, top + rowH, fill)
            }
            canvas.restore()

            text.color = context.getColor(R.color.text_secondary)
            text.textSize = dp(12f)
            text.textAlign = Paint.Align.CENTER
            canvas.drawText(days[r], rowsW + rightW / 2f + dp(2f), baseline(text, top + rowH / 2f), text)
        }

        // dashed guides at 6 / 12 / 18
        val bottomOfRows = 7 * rowH + 6 * gap
        dash.color = context.getColor(R.color.text_secondary)
        dash.alpha = 140
        dash.strokeWidth = dp(1f)
        dash.pathEffect = DashPathEffect(floatArrayOf(dp(3f), dp(3f)), 0f)
        for (h in intArrayOf(6, 12, 18)) {
            val x = h * cellW
            canvas.drawLine(x, 0f, x, bottomOfRows, dash)
        }

        // x labels
        text.color = context.getColor(R.color.text_secondary)
        text.textSize = dp(13f)
        val ly = bottomOfRows + bottomH / 2f + dp(2f)
        val by = baseline(text, ly)
        text.textAlign = Paint.Align.LEFT
        canvas.drawText("0", 0f, by, text)
        text.textAlign = Paint.Align.CENTER
        canvas.drawText("6", 6 * cellW, by, text)
        canvas.drawText("12", 12 * cellW, by, text)
        canvas.drawText("18", 18 * cellW, by, text)
        text.textAlign = Paint.Align.RIGHT
        canvas.drawText("24 h", rowsW + dp(4f), by, text)
    }
}
