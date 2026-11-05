package com.example.screentime

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.ZoneId

object TaskNotifier {
    const val ACTION_DONE = "com.example.lockdo.DONE"
    const val ACTION_REFRESH = "com.example.lockdo.REFRESH"
    private const val CHANNEL = "tasks"
    private const val NOTIF_ID = 1

    val C = intArrayOf(R.id.c1, R.id.c2)
    val T = intArrayOf(R.id.t1, R.id.t2)
    val M = intArrayOf(R.id.m1, R.id.m2)
    val R_ = intArrayOf(R.id.r1, R.id.r2)

    fun doneIntent(c: Context, id: Long): PendingIntent = PendingIntent.getBroadcast(
        c, (id % 1_000_000_000L).toInt(),
        Intent(c, TaskReceiver::class.java).setAction(ACTION_DONE).setData(Uri.parse("task://$id"))
            .putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    fun openIntent(c: Context): PendingIntent = PendingIntent.getActivity(
        c, 0, Intent(c, TasksActivity::class.java), PendingIntent.FLAG_IMMUTABLE
    )

    /** Fills the 4 checkbox rows of a RemoteViews layout with the next tasks. */
    fun fillRows(c: Context, rv: RemoteViews, pending: List<Task>) {
        for (i in 0 until 2) {
            if (i < pending.size) {
                val t = pending[i]
                rv.setViewVisibility(R_[i], View.VISIBLE)
                rv.setTextViewText(T[i], t.title)
                rv.setTextViewText(M[i], TaskStore.fmt(t.minute))
                rv.setOnClickPendingIntent(C[i], doneIntent(c, t.id))
            } else {
                rv.setViewVisibility(R_[i], View.GONE)
            }
        }
    }

    fun headText(all: List<Task>, pending: List<Task>): String = when {
        all.isEmpty() -> "No tasks yet"
        pending.isEmpty() -> "All done for today"
        pending.size > 2 -> "Today · ${pending.size} left (+${pending.size - 2} more in app)"
        else -> "Today · ${pending.size} left"
    }

    fun refresh(ctx: Context) {
        val c = ctx.applicationContext
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val all = TaskStore.all(c)
        val pending = all.filter { !it.doneToday }

        if (all.isEmpty()) {
            nm.cancel(NOTIF_ID)
        } else {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Today's tasks", NotificationManager.IMPORTANCE_LOW).apply {
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setShowBadge(false)
                }
            )
            val head = headText(all, pending)

            val big = RemoteViews(c.packageName, R.layout.task_notif_big)
            big.setTextViewText(R.id.head, head)
            fillRows(c, big, pending)

            val small = RemoteViews(c.packageName, R.layout.task_notif_small)
            val lines = intArrayOf(R.id.l1, R.id.l2)
            for (i in 0 until 2) {
                if (pending.isEmpty()) {
                    small.setViewVisibility(lines[i], if (i == 0) View.VISIBLE else View.GONE)
                    if (i == 0) small.setTextViewText(lines[i], head)
                } else if (i < pending.size) {
                    small.setViewVisibility(lines[i], View.VISIBLE)
                    small.setTextViewText(lines[i], "○  " + TaskStore.fmt(pending[i].minute) + "   " + pending[i].title)
                } else {
                    small.setViewVisibility(lines[i], View.GONE)
                }
            }

            val repost = PendingIntent.getBroadcast(
                c, 9, Intent(c, TaskReceiver::class.java).setAction(ACTION_REFRESH),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val n = Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.task_ic_notif)
                .setStyle(Notification.DecoratedCustomViewStyle())
                .setCustomContentView(small)
                .setCustomBigContentView(big)
                .setContentIntent(openIntent(c))
                .setDeleteIntent(repost)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build()
            try {
                nm.notify(NOTIF_ID, n)
            } catch (_: SecurityException) {
            }
        }
        scheduleMidnight(c)
    }

    /** Ticks reset at midnight, so refresh then. */
    private fun scheduleMidnight(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 2000
        val pi = PendingIntent.getBroadcast(
            c, 10, Intent(c, TaskReceiver::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        am.setAndAllowWhileIdle(AlarmManager.RTC, at, pi)
    }
}
