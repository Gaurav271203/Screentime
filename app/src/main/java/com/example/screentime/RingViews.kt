package com.example.screentime

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.min

/** Big goal ring: green arc = used, track = remaining, red arc = over goal. */
class RingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var frac = 0f
    private var center = ""
    private var thickness = 0.12f

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Fonts.regular(context) }
    private val rect = RectF()

    /** [used] is the fraction of the goal that has been used (can exceed 1). */
    fun setData(used: Float, centerText: String = "", thickness: Float = 0.12f) {
        this.frac = used
        this.center = centerText
        this.thickness = thickness
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = min(width, height).toFloat()
        val stroke = size * thickness
        val pad = stroke / 2f + 1f
        val cx = width / 2f
        val cy = height / 2f
        rect.set(cx - size / 2f + pad, cy - size / 2f + pad, cx + size / 2f - pad, cy + size / 2f - pad)

        track.strokeWidth = stroke
        track.color = context.getColor(R.color.track)
        canvas.drawOval(rect, track)

        arc.strokeWidth = stroke
        if (frac > 0f) {
            arc.color = context.getColor(R.color.green)
            canvas.drawArc(rect, -90f, min(frac, 1f) * 360f, false, arc)
        }
        if (frac > 1f) {
            arc.color = context.getColor(R.color.red)
            canvas.drawArc(rect, -90f, min(frac - 1f, 1f) * 360f, false, arc)
        }

        if (center.isNotEmpty()) {
            text.color = context.getColor(R.color.text_primary)
            text.textSize = size * 0.15f
            canvas.drawText(center, cx, cy - (text.descent() + text.ascent()) / 2f, text)
        }
    }
}

/** One calendar cell: day number, optional progress ring, optional "today" fill. */
class DayRingView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var number = ""
    private var frac = -1f
    private var today = false

    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Fonts.regular(context) }
    private val rect = RectF()

    fun setData(number: String, frac: Float, today: Boolean) {
        this.number = number
        this.frac = frac
        this.today = today
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val d = 38f * resources.displayMetrics.density
        val stroke = d * 0.1f
        val cx = width / 2f
        val cy = height / 2f
        rect.set(cx - d / 2f + stroke / 2f, cy - d / 2f + stroke / 2f, cx + d / 2f - stroke / 2f, cy + d / 2f - stroke / 2f)

        if (today) {
            fill.color = context.getColor(R.color.pill)
            canvas.drawCircle(cx, cy, d / 2f, fill)
        }
        if (frac >= 0f) {
            track.strokeWidth = stroke
            track.color = context.getColor(R.color.track)
            canvas.drawOval(rect, track)
            arc.strokeWidth = stroke
            if (frac > 0f) {
                arc.color = context.getColor(R.color.green)
                canvas.drawArc(rect, -90f, min(frac, 1f) * 360f, false, arc)
            }
            if (frac > 1f) {
                arc.color = context.getColor(R.color.red)
                canvas.drawArc(rect, -90f, min(frac - 1f, 1f) * 360f, false, arc)
            }
        }
        text.color = context.getColor(R.color.text_primary)
        text.textSize = 16f * resources.displayMetrics.density
        canvas.drawText(number, cx, cy - (text.descent() + text.ascent()) / 2f, text)
    }
}

/** Rounded segmented bar (home card). */
class SegmentBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var segs: List<Pair<Float, Int>> = emptyList()
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = android.graphics.Path()
    private val rect = RectF()

    fun setData(segs: List<Pair<Float, Int>>) {
        this.segs = segs
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        rect.set(0f, 0f, w, h)
        path.reset()
        path.addRoundRect(rect, h / 2f, h / 2f, android.graphics.Path.Direction.CW)
        canvas.save()
        canvas.clipPath(path)
        fill.color = context.getColor(R.color.track)
        canvas.drawRect(rect, fill)
        val total = segs.sumOf { it.first.toDouble() }.toFloat()
        if (total > 0f) {
            var x = 0f
            for ((v, c) in segs) {
                val sw = v / total * w
                fill.color = c
                canvas.drawRect(x, 0f, x + sw, h, fill)
                x += sw
            }
        }
        canvas.restore()
    }
}
