package com.example.screentime

import android.app.Notification
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Tiny local log of received notifications (package + time). */
class NotifDb private constructor(ctx: Context) :
    SQLiteOpenHelper(ctx.applicationContext, "notifs.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE n (pkg TEXT NOT NULL, ts INTEGER NOT NULL)")
        db.execSQL("CREATE INDEX n_ts ON n(ts)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}

    fun insert(pkg: String, ts: Long) {
        val cv = ContentValues()
        cv.put("pkg", pkg)
        cv.put("ts", ts)
        writableDatabase.insert("n", null, cv)
    }

    fun clearAll() {
        writableDatabase.delete("n", null, null)
    }

    fun prune(olderThan: Long) {
        writableDatabase.delete("n", "ts < ?", arrayOf(olderThan.toString()))
    }

    fun countsByDay(startMs: Long, endMs: Long): Map<LocalDate, Map<String, Int>> {
        val out = HashMap<LocalDate, HashMap<String, Int>>()
        val z = ZoneId.systemDefault()
        readableDatabase.rawQuery(
            "SELECT pkg, ts FROM n WHERE ts >= ? AND ts < ?",
            arrayOf(startMs.toString(), endMs.toString())
        ).use { c ->
            while (c.moveToNext()) {
                val pkg = c.getString(0)
                val day = Instant.ofEpochMilli(c.getLong(1)).atZone(z).toLocalDate()
                val m = out.getOrPut(day) { HashMap() }
                m[pkg] = (m[pkg] ?: 0) + 1
            }
        }
        return out
    }

    companion object {
        @Volatile
        private var inst: NotifDb? = null

        fun get(ctx: Context): NotifDb =
            inst ?: synchronized(this) { inst ?: NotifDb(ctx).also { inst = it } }
    }
}

class NotifLogService : NotificationListenerService() {
    private val seen = HashSet<String>()

    override fun onListenerConnected() {
        try {
            NotifDb.get(this).prune(System.currentTimeMillis() - 90L * 86_400_000L)
        } catch (_: Exception) {
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        if ((n.flags and Notification.FLAG_ONGOING_EVENT) != 0) return
        if (!seen.add(sbn.key)) return          // updates of the same notification
        try {
            NotifDb.get(this).insert(sbn.packageName, System.currentTimeMillis())
        } catch (_: Exception) {
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        seen.remove(sbn.key)
    }
}

object NotifAccess {
    fun enabled(ctx: Context): Boolean {
        val s = Settings.Secure.getString(ctx.contentResolver, "enabled_notification_listeners")
            ?: return false
        return s.contains(ctx.packageName)
    }
}
