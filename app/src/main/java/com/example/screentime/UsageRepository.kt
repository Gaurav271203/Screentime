package com.example.screentime

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.content.pm.ApplicationInfo
import android.graphics.drawable.Drawable
import android.os.Process
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.RelativeSizeSpan
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.ceil

// ---------------------------------------------------------------- models

data class AppUsage(
    val pkg: String,
    val label: String,
    val icon: Drawable?,
    val millis: Long
)

data class DayUsage(
    val date: LocalDate,
    val totalMillis: Long,
    val unlocks: Int,
    val apps: List<AppUsage>
)

class AppMeta(
    val pkg: String,
    val label: String,
    private val iconDrawable: Drawable?,
    val category: String
) {
    fun icon(ctx: Context): Drawable? =
        iconDrawable?.constantState?.newDrawable(ctx.resources) ?: iconDrawable
}

// ---------------------------------------------------------------- app metadata

object Apps {
    private val cache = HashMap<String, AppMeta>()
    private var excludedCache: Set<String>? = null

    // Chrome is grouped under Social in the reference app, so do the same.
    private val overrides = mapOf(
        "com.android.chrome" to "Social",
        "com.whatsapp" to "Social",
        "com.instagram.android" to "Social",
        "com.facebook.katana" to "Social",
        "com.snapchat.android" to "Social",
        "org.telegram.messenger" to "Social",
        "com.twitter.android" to "Social",
        "com.google.android.youtube" to "Video",
        "com.google.android.apps.youtube.music" to "Audio",
        "com.spotify.music" to "Audio"
    )

    @Synchronized
    fun get(ctx: Context, pkg: String): AppMeta {
        cache[pkg]?.let { return it }
        val pm = ctx.packageManager
        var label = pkg
        var icon: Drawable? = null
        var info: ApplicationInfo? = null
        try {
            info = pm.getApplicationInfo(pkg, 0)
            label = pm.getApplicationLabel(info).toString()
            icon = pm.getApplicationIcon(info)
        } catch (_: Exception) {
        }
        val meta = AppMeta(pkg, label, icon, categoryOf(pkg, info))
        cache[pkg] = meta
        return meta
    }

    private fun categoryOf(pkg: String, info: ApplicationInfo?): String {
        overrides[pkg]?.let { return it }
        if (info == null) return "Other"
        return when (info.category) {
            ApplicationInfo.CATEGORY_SOCIAL -> "Social"
            ApplicationInfo.CATEGORY_VIDEO -> "Video"
            ApplicationInfo.CATEGORY_GAME -> "Games"
            ApplicationInfo.CATEGORY_AUDIO -> "Audio"
            ApplicationInfo.CATEGORY_IMAGE -> "Photo"
            ApplicationInfo.CATEGORY_NEWS -> "News"
            ApplicationInfo.CATEGORY_MAPS -> "Maps"
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
            else -> "Other"
        }
    }

    /** Packages that should not count as "screen time" (launcher, system UI, this app). */
    @Synchronized
    fun excluded(ctx: Context): Set<String> {
        excludedCache?.let { return it }
        val s = HashSet<String>()
        s.add("com.android.systemui")
        s.add(ctx.packageName)
        try {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            for (ri in ctx.packageManager.queryIntentActivities(home, 0)) {
                s.add(ri.activityInfo.packageName)
            }
        } catch (_: Exception) {
        }
        excludedCache = s
        return s
    }

    fun categoryTotals(ctx: Context, apps: Map<String, Long>): List<Pair<String, Long>> {
        val out = HashMap<String, Long>()
        for ((p, ms) in apps) {
            val c = get(ctx, p).category
            out[c] = (out[c] ?: 0L) + ms
        }
        return out.entries.sortedByDescending { it.value }.map { it.key to it.value }
    }
}

// ---------------------------------------------------------------- usage data

class UsageData {
    private val hours = HashMap<Long, HashMap<String, Long>>()   // local hour index -> pkg -> ms
    private val unlockDays = HashMap<Long, Int>()                // local epoch day -> unlocks
    internal var skip: Set<Long> = emptySet()                    // days restored from saved history

    internal fun add(z: ZoneId, pkg: String, s: Long, e: Long) {
        var t = s
        while (t < e) {
            val off = z.rules.getOffset(Instant.ofEpochMilli(t)).totalSeconds * 1000L
            val local = t + off
            val hourIdx = Math.floorDiv(local, HOUR)
            val segEnd = minOf(e, (hourIdx + 1) * HOUR - off)
            if (Math.floorDiv(hourIdx, 24L) !in skip) {
                val m = hours.getOrPut(hourIdx) { HashMap() }
                m[pkg] = (m[pkg] ?: 0L) + (segEnd - t)
            }
            t = if (segEnd > t) segEnd else e
        }
    }

    internal fun addUnlock(z: ZoneId, ts: Long) {
        val off = z.rules.getOffset(Instant.ofEpochMilli(ts)).totalSeconds * 1000L
        val day = Math.floorDiv(ts + off, 86_400_000L)
        if (day in skip) return
        unlockDays[day] = (unlockDays[day] ?: 0) + 1
    }

    fun appsOnDay(day: LocalDate): Map<String, Long> {
        val d = day.toEpochDay()
        val out = HashMap<String, Long>()
        for (h in 0 until 24) {
            hours[d * 24 + h]?.forEach { (p, ms) -> out[p] = (out[p] ?: 0L) + ms }
        }
        return out
    }

    fun totalOnDay(day: LocalDate): Long = appsOnDay(day).values.sum()

    fun hourMs(day: LocalDate, hour: Int, filter: ((String) -> Boolean)?): Long {
        val m = hours[day.toEpochDay() * 24 + hour] ?: return 0L
        var sum = 0L
        for ((p, ms) in m) if (filter == null || filter(p)) sum += ms
        return sum
    }

    fun unlocksOnDay(day: LocalDate): Int = unlockDays[day.toEpochDay()] ?: 0

    fun export(day: LocalDate): String? {
        val d = day.toEpochDay()
        val sb = StringBuilder()
        for (h in 0 until 24) {
            hours[d * 24 + h]?.forEach { (p, ms) ->
                if (ms > 0) sb.append(p).append('|').append(h).append('|').append(ms).append('\n')
            }
        }
        val u = unlockDays[d] ?: 0
        if (u > 0) sb.append("U||").append(u).append('\n')
        return if (sb.isEmpty()) null else sb.toString()
    }

    fun importDay(day: LocalDate, s: String) {
        val d = day.toEpochDay()
        for (line in s.split('\n')) {
            if (line.isBlank()) continue
            val p = line.split('|')
            if (p.size != 3) continue
            if (p[0] == "U") {
                unlockDays[d] = p[2].toIntOrNull() ?: 0
                continue
            }
            val h = p[1].toIntOrNull() ?: continue
            val ms = p[2].toLongOrNull() ?: continue
            val m = hours.getOrPut(d * 24 + h) { HashMap() }
            m[p[0]] = (m[p[0]] ?: 0L) + ms
        }
    }

    fun packages(): Set<String> {
        val s = HashSet<String>()
        for (m in hours.values) s.addAll(m.keys)
        return s
    }

    companion object {
        private const val HOUR = 3_600_000L
    }
}

object UsageRepository {

    /** Bump to throw away saved history computed by an older algorithm. */
    private const val HIST_VERSION = 2

    /** Wipes everything saved on this phone (history + notification log). Goal is kept. */
    fun clearSaved(ctx: Context) {
        ctx.getSharedPreferences("hist", Context.MODE_PRIVATE).edit().clear()
            .putInt("v", HIST_VERSION).apply()
        try {
            NotifDb.get(ctx).clearAll()
        } catch (_: Exception) {
        }
    }


    fun hasAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    /**
     * Loads usage for [from]..[to] inclusive. Android only keeps roughly a week of detailed
     * events, so completed days are saved locally and restored from there next time.
     */
    fun load(ctx: Context, from: LocalDate, to: LocalDate): UsageData {
        val z = ZoneId.systemDefault()
        val data = UsageData()
        val today = LocalDate.now()
        val sp = ctx.getSharedPreferences("hist", Context.MODE_PRIVATE)
        if (sp.getInt("v", 0) != HIST_VERSION) {
            sp.edit().clear().putInt("v", HIST_VERSION).apply()
        }

        val skip = HashSet<Long>()
        var firstMissing: LocalDate? = null
        var d = from
        while (!d.isAfter(to)) {
            val saved = if (d.isBefore(today)) sp.getString("d" + d.toEpochDay(), null) else null
            if (saved != null) {
                data.importDay(d, saved)
                skip.add(d.toEpochDay())
            } else if (firstMissing == null) {
                firstMissing = d
            }
            d = d.plusDays(1)
        }
        data.skip = skip
        val qFrom = firstMissing ?: return data
        if (qFrom.isAfter(today)) return data

        val start = qFrom.atStartOfDay(z).toInstant().toEpochMilli()
        val end = minOf(to.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli(), System.currentTimeMillis())
        if (end <= start) return data

        val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val excluded = Apps.excluded(ctx)
        val events = usm.queryEvents(start, end)
        val e = UsageEvents.Event()

        // Per package: how many activities are in the foreground, and since when.
        // Time counts while at least one is resumed (so overlapping activities never double count).
        val depth = HashMap<String, Int>()
        val since = HashMap<String, Long>()
        val seen = HashSet<String>()

        fun closeAll(ts: Long) {
            for ((pkg, st) in since) {
                if (ts > st && pkg !in excluded) data.add(z, pkg, st, ts)
            }
            since.clear()
            depth.clear()
        }

        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            val ts = e.timeStamp
            when (e.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    val pkg = e.packageName ?: continue
                    seen.add(pkg)
                    val n = depth[pkg] ?: 0
                    if (n == 0) since[pkg] = ts
                    depth[pkg] = n + 1
                }
                UsageEvents.Event.ACTIVITY_PAUSED -> {
                    val pkg = e.packageName ?: continue
                    val n = depth[pkg] ?: 0
                    if (n <= 0) {
                        // Session began before the window we queried: count from the window start.
                        if (pkg !in seen && pkg !in excluded && ts > start) data.add(z, pkg, start, ts)
                        seen.add(pkg)
                    } else if (n == 1) {
                        val st = since.remove(pkg)
                        depth.remove(pkg)
                        if (st != null && ts > st && pkg !in excluded) data.add(z, pkg, st, ts)
                    } else {
                        depth[pkg] = n - 1
                    }
                }
                // Screen turned off / locked / device shut down: nothing is in use any more.
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN,
                UsageEvents.Event.DEVICE_SHUTDOWN -> closeAll(ts)
                UsageEvents.Event.KEYGUARD_HIDDEN -> data.addUnlock(z, ts)
            }
        }
        // Whatever is still in the foreground right now.
        closeAll(end)

        // Save completed days so they survive Android's event retention limit.
        val ed = sp.edit()
        var sd = qFrom
        while (!sd.isAfter(to) && sd.isBefore(today)) {
            if (sd.toEpochDay() !in skip) {
                data.export(sd)?.let { ed.putString("d" + sd.toEpochDay(), it) }
            }
            sd = sd.plusDays(1)
        }
        ed.apply()
        return data
    }

    /** Used by the home-screen widget. */
    fun loadDay(ctx: Context, date: LocalDate): DayUsage {
        val d = load(ctx, date, date)
        val apps = d.appsOnDay(date).entries
            .filter { it.value >= 1000L }
            .map {
                val m = Apps.get(ctx, it.key)
                AppUsage(it.key, m.label, m.icon(ctx), it.value)
            }
            .sortedByDescending { it.millis }
        return DayUsage(date, apps.sumOf { it.millis }, d.unlocksOnDay(date), apps)
    }
}

// ---------------------------------------------------------------- preferences

object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)

    /** Goal in minutes, or -1 when no goal is set. */
    fun goalMinutes(ctx: Context): Int = sp(ctx).getInt("goal", -1)

    fun goalSince(ctx: Context): Long = sp(ctx).getLong("goalSince", LocalDate.now().toEpochDay())

    fun setGoal(ctx: Context, minutes: Int) {
        val e = sp(ctx).edit()
        if (sp(ctx).getInt("goal", -1) <= 0) e.putLong("goalSince", LocalDate.now().toEpochDay())
        e.putInt("goal", minutes).apply()
    }

    fun blockEnabled(ctx: Context): Boolean = sp(ctx).getBoolean("block", false)

    fun setBlockEnabled(ctx: Context, on: Boolean) {
        sp(ctx).edit().putBoolean("block", on).apply()
    }

    /** Packages the user chose to keep usable after the limit. null = never edited (use defaults). */
    fun allowedApps(ctx: Context): Set<String>? = sp(ctx).getStringSet("allowed", null)?.toSet()

    fun setAllowedApps(ctx: Context, s: Set<String>) {
        sp(ctx).edit().putStringSet("allowed", HashSet(s)).apply()
    }

    fun clearGoal(ctx: Context) {
        sp(ctx).edit().remove("goal").remove("goalSince").apply()
    }
}

// ---------------------------------------------------------------- formatting

object Fmt {
    private const val UNIT = 0.55f

    private fun hm(ms: Long): Pair<Long, Long> {
        val t = ms / 60_000L
        return Pair(t / 60, t % 60)
    }

    /** "48 m", "1 h 45 m" */
    fun spaced(ms: Long): String {
        if (ms in 1 until 60_000L) return "<1 m"
        val (h, m) = hm(ms)
        return when {
            h == 0L -> "$m m"
            m == 0L -> "$h h"
            else -> "$h h $m m"
        }
    }

    fun axisTime(v: Float): String {
        val min = Math.round(v / 60_000f)
        return if (min % 60 == 0) "${min / 60} h" else "$min m"
    }

    /** "1 h 35 m" with small units next to large digits. */
    fun big(ms: Long): CharSequence {
        val (h, m) = hm(ms)
        val sb = SpannableStringBuilder()
        if (h > 0) {
            sb.append(h.toString())
            small(sb, " h ")
        }
        sb.append(m.toString())
        small(sb, " m")
        return sb
    }

    /** "26 notifications" with a small word. */
    fun bigCount(n: Int, word: String): CharSequence {
        val sb = SpannableStringBuilder(n.toString())
        small(sb, " $word")
        return sb
    }

    private fun small(sb: SpannableStringBuilder, s: String) {
        val st = sb.length
        sb.append(s)
        sb.setSpan(RelativeSizeSpan(UNIT), st, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    }
}

// ---------------------------------------------------------------- chart scale / palette / ui helpers

object ChartScale {
    val TIME_STEPS: List<Float> =
        listOf(15f, 30f, 60f, 120f, 180f, 240f, 360f, 720f).map { it * 60_000f }
    val COUNT_STEPS: List<Float> = listOf(10f, 20f, 50f, 100f, 200f, 500f, 1000f)

    fun grid(max: Float, steps: List<Float>, defaultIdx: Int): List<Float> {
        if (max <= 0f) return listOf(steps[defaultIdx], steps[defaultIdx] * 2f)
        val s = steps.firstOrNull { ceil(max / it) <= 3f } ?: steps.last()
        val n = ceil(max / s).toInt().coerceAtLeast(1)
        return (1..n).map { it * s }
    }
}

object Palette {
    /** teal, blue, purple, light-grey (everything else) */
    fun series(ctx: Context): IntArray = intArrayOf(
        ctx.getColor(R.color.c1), ctx.getColor(R.color.c2),
        ctx.getColor(R.color.c3), ctx.getColor(R.color.c4)
    )
}

object Ui {
    fun dp(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density
    fun dpi(ctx: Context, v: Float): Int = (dp(ctx, v) + 0.5f).toInt()

    fun legendRow(ctx: Context, parent: LinearLayout, color: Int, name: String, value: String) {
        val row = LayoutInflater.from(ctx).inflate(R.layout.item_legend, parent, false)
        row.findViewById<View>(R.id.dot).backgroundTintList = ColorStateList.valueOf(color)
        row.findViewById<TextView>(R.id.legName).text = name
        row.findViewById<TextView>(R.id.legValue).text = value
        parent.addView(row)
    }

    /** Horizontal row of app icons with name and time underneath. */
    fun appIcons(ctx: Context, parent: LinearLayout, items: List<Pair<AppMeta, Long>>) {
        parent.removeAllViews()
        val inf = LayoutInflater.from(ctx)
        for ((meta, ms) in items) {
            val v = inf.inflate(R.layout.item_app_icon, parent, false)
            v.findViewById<ImageView>(R.id.icon).setImageDrawable(meta.icon(ctx))
            v.findViewById<TextView>(R.id.name).text = meta.label
            v.findViewById<TextView>(R.id.time).text = Fmt.spaced(ms)
            parent.addView(v)
        }
    }

    /** Vertical list rows: icon, name, time. */
    fun appRows(ctx: Context, parent: LinearLayout, items: List<Pair<AppMeta, Long>>) {
        parent.removeAllViews()
        val inf = LayoutInflater.from(ctx)
        for ((meta, ms) in items) {
            val v = inf.inflate(R.layout.item_app_row, parent, false)
            v.findViewById<ImageView>(R.id.icon).setImageDrawable(meta.icon(ctx))
            v.findViewById<TextView>(R.id.name).text = meta.label
            v.findViewById<TextView>(R.id.time).text = Fmt.spaced(ms)
            parent.addView(v)
        }
    }
}
