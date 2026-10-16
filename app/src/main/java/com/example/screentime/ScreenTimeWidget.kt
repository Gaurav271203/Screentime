package com.example.screentime

import android.app.AlarmManager
import android.app.AppOpsManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Process
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.view.View
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.util.Date
import java.util.Locale

class ScreenTimeWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        scheduleNext(context)
        async { render(context, manager, ids) }
    }

    override fun onEnabled(context: Context) {
        scheduleNext(context)
    }

    override fun onDisabled(context: Context) {
        cancelTicks(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_REFRESH || intent.action == ACTION_TICK) {
            scheduleNext(context)
            async { updateAll(context) }
        } else {
            super.onReceive(context, intent)
        }
    }

    private fun async(block: () -> Unit) {
        val pending = goAsync()
        Thread {
            try {
                block()
            } finally {
                pending.finish()
            }
        }.start()
    }

    companion object {
        const val ACTION_REFRESH = "com.example.screentime.ACTION_REFRESH"
        const val ACTION_TICK = "com.example.screentime.ACTION_TICK"
        private const val INTERVAL_MS = 10 * 60 * 1000L

        private fun tickIntent(ctx: Context): PendingIntent = PendingIntent.getBroadcast(
            ctx, 2,
            Intent(ctx, ScreenTimeWidget::class.java).setAction(ACTION_TICK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        /** Inexact alarm (no special permission); re-armed after every update. */
        fun scheduleNext(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.setAndAllowWhileIdle(
                AlarmManager.RTC,
                System.currentTimeMillis() + INTERVAL_MS,
                tickIntent(ctx)
            )
        }

        fun cancelTicks(ctx: Context) {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(tickIntent(ctx))
        }

        private const val TEAL = 0xFF1FCFB5.toInt()
        private const val BLUE = 0xFF3A78F2.toInt()
        private const val PURPLE = 0xFF6C4FD8.toInt()
        private const val OTHERS = 0xFFC8C8CC.toInt()
        private const val TRACK = 0xFF4A4A4F.toInt()
        private const val LEGEND_GRAY = 0xFFB3B3B8.toInt()

        fun updateAll(ctx: Context) {
            // Also saves recent completed days to local history (see UsageRepository.load)
            try {
                val t = LocalDate.now()
                UsageRepository.load(ctx, t.minusDays(9), t)
            } catch (_: Exception) {
            }
            val mgr = AppWidgetManager.getInstance(ctx)
            val ids = mgr.getAppWidgetIds(ComponentName(ctx, ScreenTimeWidget::class.java))
            if (ids.isNotEmpty()) render(ctx, mgr, ids)
        }

        private fun render(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
            val views = buildViews(ctx)
            for (id in ids) mgr.updateAppWidget(id, views)
        }

        private fun hasAccess(ctx: Context): Boolean {
            val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            return ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
            ) == AppOpsManager.MODE_ALLOWED
        }

        private fun spaced(ms: Long): String {
            val totalMin = ms / 60_000L
            val h = totalMin / 60
            val m = totalMin % 60
            return when {
                ms < 60_000L -> "<1 m"
                h == 0L -> "$m m"
                m == 0L -> "$h h"
                else -> "$h h $m m"
            }
        }

        /** "1 h 6 m" with small units next to large digits. */
        private fun totalText(ms: Long): CharSequence {
            val totalMin = ms / 60_000L
            val h = totalMin / 60
            val m = totalMin % 60
            val sb = SpannableStringBuilder()
            fun unit(s: String) {
                val st = sb.length
                sb.append(s)
                sb.setSpan(RelativeSizeSpan(0.5f), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (h > 0) {
                sb.append(h.toString())
                unit(" h ")
            }
            sb.append(m.toString())
            unit(" m")
            return sb
        }

        private fun legend(label: String, color: Int): CharSequence {
            val sb = SpannableStringBuilder("● $label")
            sb.setSpan(ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.6f), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return sb
        }

        private fun barBitmap(segs: List<Pair<Float, Int>>): Bitmap {
            val w = 560
            val h = 40
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            val path = Path().apply {
                addRoundRect(RectF(0f, 0f, w.toFloat(), h.toFloat()), h / 2f, h / 2f, Path.Direction.CW)
            }
            c.clipPath(path)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = TRACK
            c.drawRect(0f, 0f, w.toFloat(), h.toFloat(), p)
            val total = segs.sumOf { it.first.toDouble() }.toFloat()
            if (total > 0f) {
                var x = 0f
                for ((v, col) in segs) {
                    val sw = v / total * w
                    p.color = col
                    c.drawRect(x, 0f, x + sw, h.toFloat(), p)
                    x += sw
                }
            }
            return bmp
        }

        private fun buildViews(ctx: Context): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_screen_time)

            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, HomeActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            rv.setOnClickPendingIntent(R.id.widgetRoot, open)

            val refresh = PendingIntent.getBroadcast(
                ctx, 1,
                Intent(ctx, ScreenTimeWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.btnRefresh, refresh)

            rv.setTextViewText(
                R.id.tvStamp,
                SimpleDateFormat("dd/MM, h:mm a", Locale.getDefault()).format(Date()).lowercase()
            )

            val rows = intArrayOf(R.id.row1, R.id.row2, R.id.row3)
            val names = intArrayOf(R.id.name1, R.id.name2, R.id.name3)
            val times = intArrayOf(R.id.time1, R.id.time2, R.id.time3)

            if (!hasAccess(ctx)) {
                rv.setTextViewText(R.id.tvTotal, "Tap to allow")
                rv.setTextViewTextSize(R.id.tvTotal, android.util.TypedValue.COMPLEX_UNIT_SP, 18f)
                rv.setImageViewBitmap(R.id.imgBar, barBitmap(emptyList()))
                for (r in rows) rv.setViewVisibility(r, View.GONE)
                return rv
            }

            val day = UsageRepository.loadDay(ctx, LocalDate.now())
            rv.setTextViewTextSize(R.id.tvTotal, android.util.TypedValue.COMPLEX_UNIT_SP, 34f)
            rv.setTextViewText(R.id.tvTotal, totalText(day.totalMillis))

            val colors = intArrayOf(TEAL, BLUE, PURPLE)
            val top = day.apps.take(3)
            val segs = ArrayList<Pair<Float, Int>>()
            top.forEachIndexed { i, a -> segs.add(a.millis.toFloat() to colors[i]) }
            val others = day.totalMillis - top.sumOf { it.millis }
            if (others > 0) segs.add(others.toFloat() to OTHERS)
            rv.setImageViewBitmap(R.id.imgBar, barBitmap(segs))

            for (i in 0 until 3) {
                if (i < top.size) {
                    rv.setViewVisibility(rows[i], View.VISIBLE)
                    rv.setTextViewText(names[i], legend(top[i].label, colors[i]))
                    rv.setTextViewText(times[i], spaced(top[i].millis))
                } else {
                    rv.setViewVisibility(rows[i], View.GONE)
                }
            }
            return rv
        }
    }
}
