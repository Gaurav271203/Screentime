package com.example.screentime

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Week bar chart used on the dashboard and weekly report.
 * Bars can be stacked (several coloured segments) or a single colour.
 */
class UsageBarChart @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /** [segments] are stacked bottom-to-top. If [color] != 0 the whole bar uses that colour. */
    class Bar(val segments: FloatArray, val color: Int = 0)

    private var bars: List<Bar> = emptyList()
    private var labels: List<String> = emptyList()
    private var selected = -1
    private var segColors = IntArray(0)
    private var grid: List<Float> = emptyList()
    private var gridLabel: (Float) -> String = { "" }
    private var goal = -1f
    private var markers: BooleanArray? = null

    var onSelect: ((Int) -> Unit)? = null
    var onSwipe: ((Int) -> Unit)? = null

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val gridText = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val goalPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val xPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val path = Path()
    private val rect = RectF()

    private fun dp(v: Float) = v * resources.displayMetrics.density
    private fun baseline(p: Paint, cy: Float) = cy - (p.descent() + p.ascent()) / 2f

    fun setData(
        bars: List<Bar>,
        labels: List<String>,
        selected: Int,
        segColors: IntArray,
        grid: List<Float>,
        gridLabel: (Float) -> String,
        goal: Float = -1f,
        markers: BooleanArray? = null
    ) {
        this.bars = bars
        this.labels = labels
        this.selected = selected
        this.segColors = segColors
        this.grid = grid
        this.gridLabel = gridLabel
        this.goal = goal
        this.markers = markers
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val n = bars.size
        if (n == 0) return

        val rightW = dp(36f)
        val areaW = width - rightW
        val labelH = dp(34f)
        val markerH = if (markers != null) dp(30f) else 0f
        val top = dp(10f)
        val bottom = height - labelH - markerH
        val plotH = bottom - top
        val scale = max(grid.maxOrNull() ?: 1f, 1f)

        val gridColor = context.getColor(R.color.grid)
        val textSec = context.getColor(R.color.text_secondary)
        val textPri = context.getColor(R.color.text_primary)

        linePaint.color = gridColor
        linePaint.strokeWidth = dp(1f)
        gridText.color = textSec
        gridText.textSize = dp(13f)

        val lineEnd = areaW - dp(2f)
        for (g in grid) {
            val y = bottom - g / scale * plotH
            canvas.drawLine(0f, y, lineEnd, y, linePaint)
            canvas.drawText(gridLabel(g), width.toFloat(), baseline(gridText, y), gridText)
        }
        canvas.drawLine(0f, bottom, lineEnd, bottom, linePaint)

        val slot = areaW / n
        val bw = slot * 0.4f

        for (i in 0 until n) {
            val bar = bars[i]
            val cx = slot * i + slot / 2f
            val total = bar.segments.sum()
            if (total > 0f) {
                var h = total / scale * plotH
                h = min(max(h, dp(4f)), plotH)
                val r = min(bw / 2f, h / 2f)
                rect.set(cx - bw / 2f, bottom - h, cx + bw / 2f, bottom)
                path.reset()
                path.addRoundRect(rect, floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW)
                canvas.save()
                canvas.clipPath(path)
                if (bar.color != 0) {
                    fill.color = bar.color
                    canvas.drawRect(rect, fill)
                } else {
                    var y = bottom
                    for (k in bar.segments.indices) {
                        val sh = bar.segments[k] / total * h
                        if (sh <= 0f) continue
                        fill.color = segColors[min(k, segColors.size - 1)]
                        canvas.drawRect(cx - bw / 2f, y - sh, cx + bw / 2f, y, fill)
                        y -= sh
                    }
                }
                canvas.restore()
            }

            // x-axis label (with a circle behind the selected one)
            val cy = bottom + labelH / 2f
            if (i == selected) {
                fill.color = context.getColor(R.color.pill)
                canvas.drawCircle(cx, cy, dp(13f), fill)
            }
            labelPaint.color = if (i == selected) textPri else textSec
            labelPaint.textSize = dp(13f)
            canvas.drawText(labels.getOrElse(i) { "" }, cx, baseline(labelPaint, cy), labelPaint)

            // "over goal" marker
            if (markers?.getOrNull(i) == true) {
                val my = bottom + labelH + markerH / 2f
                fill.color = context.getColor(R.color.red)
                canvas.drawCircle(cx, my, dp(9f), fill)
                xPaint.color = context.getColor(R.color.white)
                xPaint.strokeWidth = dp(1.6f)
                val d = dp(3.4f)
                canvas.drawLine(cx - d, my - d, cx + d, my + d, xPaint)
                canvas.drawLine(cx - d, my + d, cx + d, my - d, xPaint)
            }
        }

        // goal line
        if (goal > 0f && goal <= scale) {
            val y = bottom - goal / scale * plotH
            goalPaint.color = context.getColor(R.color.red)
            goalPaint.strokeWidth = dp(1.5f)
            goalPaint.pathEffect = DashPathEffect(floatArrayOf(dp(5f), dp(4f)), 0f)
            canvas.drawLine(0f, y, width.toFloat(), y, goalPaint)
        }
    }

    private var downX = 0f

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> downX = event.x
            MotionEvent.ACTION_UP -> {
                val dx = event.x - downX
                if (abs(dx) > dp(60f)) {
                    onSwipe?.invoke(if (dx > 0) -1 else 1)
                } else if (bars.isNotEmpty()) {
                    val areaW = width - dp(36f)
                    val idx = (event.x / (areaW / bars.size)).toInt().coerceIn(0, bars.size - 1)
                    onSelect?.invoke(idx)
                }
                performClick()
            }
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
