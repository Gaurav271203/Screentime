package com.example.lockdo

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews

class TaskWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        for (id in ids) manager.updateAppWidget(id, build(context))
    }

    companion object {
        fun build(c: Context): RemoteViews {
            val all = Store.all(c)
            val pending = all.filter { !it.doneToday }
            val rv = RemoteViews(c.packageName, R.layout.widget_tasks)
            rv.setTextViewText(R.id.head, Notifier.headText(all, pending))
            Notifier.fillRows(c, rv, pending)
            rv.setOnClickPendingIntent(R.id.head, Notifier.openIntent(c))
            return rv
        }

        fun updateAll(c: Context) {
            val mgr = AppWidgetManager.getInstance(c)
            val ids = mgr.getAppWidgetIds(ComponentName(c, TaskWidget::class.java))
            if (ids.isNotEmpty()) {
                val rv = build(c)
                for (id in ids) mgr.updateAppWidget(id, rv)
            }
        }
    }
}
