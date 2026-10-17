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
import android.util.SizeF
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

        private fun withGoal(ms: Long, goalMs: Long): CharSequence {
            val sb = SpannableStringBuilder(totalText(ms))
            if (goalMs > 0) {
                val st = sb.length
                sb.append("/").append(spaced(goalMs))
                sb.setSpan(ForegroundColorSpan(LEGEND_GRAY), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                sb.setSpan(RelativeSizeSpan(0.5f), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            return sb
        }

        private fun limitText(ms: Long, limitMs: Long): CharSequence {
            val sb = SpannableStringBuilder(spaced(ms))
            val st = sb.length
            sb.append("/").append(spaced(limitMs))
            sb.setSpan(ForegroundColorSpan(0xFFA0A0A6.toInt()), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.8f), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return sb
        }

        private fun legend(label: String, color: Int): CharSequence {
            val sb = SpannableStringBuilder("● $label")
            sb.setSpan(ForegroundColorSpan(color), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(RelativeSizeSpan(0.6f), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return sb
        }

        private fun barBitmap(segs: List<Pair<Float, Int>>): Bitmap {
            val w = 600
            val h = 32
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

        /** "1h 6m" for the tiny 1x1 version. */
        private fun tiny(ms: Long): String {
            val t = ms / 60_000L
            val h = t / 60
            val m = t % 60
            return if (h == 0L) "${m}m" else "${h}h ${m}m"
        }

        private class WData(
            val access: Boolean,
            val total: Long,
            val top: List<AppUsage>,
            val segs: List<Pair<Float, Int>>,
            val goalMs: Long,
            val timers: Map<String, Int>
        )

        private fun loadData(ctx: Context): WData {
            if (!hasAccess(ctx)) return WData(false, 0L, emptyList(), emptyList(), 0L, emptyMap())
            val day = UsageRepository.loadDay(ctx, LocalDate.now())
            val colors = intArrayOf(TEAL, BLUE, PURPLE)
            val timers = Prefs.timers(ctx)
            val top = day.apps.take(3)
            val segs = ArrayList<Pair<Float, Int>>()
            top.forEachIndexed { i, a -> segs.add(a.millis.toFloat() to colors[i]) }
            val others = day.totalMillis - top.sumOf { it.millis }
            if (others > 0) segs.add(others.toFloat() to OTHERS)
            val g = Prefs.goalMinutes(ctx)
            return WData(true, day.totalMillis, top, segs, if (g > 0) g * 60_000L else 0L, timers)
        }

        private fun buildViews(ctx: Context): RemoteViews {
            val d = loadData(ctx)
            val wide = RemoteViews(ctx.packageName, R.layout.widget_wide)
            val compact = RemoteViews(ctx.packageName, R.layout.widget_compact)
            val small = RemoteViews(ctx.packageName, R.layout.widget_small)

            val open = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, HomeActivity::class.java), PendingIntent.FLAG_IMMUTABLE
            )
            val refresh = PendingIntent.getBroadcast(
                ctx, 1,
                Intent(ctx, ScreenTimeWidget::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            for (rv in listOf(wide, compact, small)) rv.setOnClickPendingIntent(R.id.widgetRoot, open)
            wide.setOnClickPendingIntent(R.id.btnRefresh, refresh)
            compact.setOnClickPendingIntent(R.id.btnRefresh, refresh)

            val bar = barBitmap(d.segs)
            wide.setImageViewBitmap(R.id.imgBar, bar)
            compact.setImageViewBitmap(R.id.imgBar, bar)
            wide.setTextViewText(
                R.id.tvStamp,
                SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date()).lowercase()
            )

            if (!d.access) {
                wide.setTextViewText(R.id.tvTotal, "Allow access")
                compact.setTextViewText(R.id.tvTotal, "Allow access")
                small.setTextViewText(R.id.tvTotal, "Allow")
                for (r in intArrayOf(R.id.row1, R.id.row2, R.id.row3)) wide.setViewVisibility(r, View.GONE)
            } else {
                wide.setTextViewText(R.id.tvTotal, withGoal(d.total, d.goalMs))
                compact.setTextViewText(R.id.tvTotal, withGoal(d.total, d.goalMs))
                small.setTextViewText(R.id.tvTotal, tiny(d.total))

                val rows = intArrayOf(R.id.row1, R.id.row2, R.id.row3)
                val names = intArrayOf(R.id.name1, R.id.name2, R.id.name3)
                val times = intArrayOf(R.id.time1, R.id.time2, R.id.time3)
                val colors = intArrayOf(TEAL, BLUE, PURPLE)
                for (i in 0 until 3) {
                    if (i < d.top.size) {
                        wide.setViewVisibility(rows[i], View.VISIBLE)
                        wide.setTextViewText(names[i], legend(d.top[i].label, colors[i]))
                        val lim = d.timers[d.top[i].pkg]
                        wide.setTextViewText(
                            times[i],
                            if (lim != null) limitText(d.top[i].millis, lim * 60_000L) else spaced(d.top[i].millis)
                        )
                    } else {
                        wide.setViewVisibility(rows[i], View.GONE)
                    }
                }
            }

            // Android picks the best fit for the current widget size:
            // tiny = total only, compact = total + bar, wide (4x1) = everything.
            return RemoteViews(
                mapOf(
                    SizeF(40f, 40f) to small,
                    SizeF(110f, 64f) to compact,
                    SizeF(220f, 66f) to wide
                )
            )
        }
    }
}
