package com.example.screentime

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.provider.Telephony
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate
import java.util.concurrent.Executors

/** Starts / stops the blocking service depending on the saved settings. */
object BlockHelper {

    fun sync(ctx: Context) {
        val c = ctx.applicationContext
        val want = Prefs.blockEnabled(c) && Prefs.goalMinutes(c) > 0 && Settings.canDrawOverlays(c)
        try {
            if (want) {
                c.startForegroundService(Intent(c, BlockService::class.java))
            } else {
                c.stopService(Intent(c, BlockService::class.java))
            }
        } catch (_: Exception) {
        }
    }

    fun overlaySettingsIntent(ctx: Context): Intent =
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + ctx.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Apps that stay usable by default: phone, messages, settings, launcher, this app. */
    fun defaultAllowed(ctx: Context): Set<String> {
        val s = HashSet<String>()
        s.add(ctx.packageName)
        s.add("com.android.settings")
        s.add("com.android.systemui")
        s.add("com.android.phone")
        s.add("com.android.incallui")
        s.add("com.android.server.telecom")
        s.add("com.android.emergency")
        s.addAll(Apps.excluded(ctx))
        try {
            val pm = ctx.packageManager
            for (ri in pm.queryIntentActivities(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")), 0)) {
                s.add(ri.activityInfo.packageName)
            }
            Telephony.Sms.getDefaultSmsPackage(ctx)?.let { s.add(it) }
        } catch (_: Exception) {
        }
        return s
    }

    fun allowed(ctx: Context): Set<String> {
        val saved = Prefs.allowedApps(ctx)
        val base = defaultAllowed(ctx)
        // Launcher, system UI and this app are always allowed so you can never get locked out.
        return (saved ?: base) + Apps.excluded(ctx) + ctx.packageName + "com.android.settings" + "com.android.systemui"
    }
}

class BlockService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()

    @Volatile
    private var totalMs = 0L
    private var lastTotalAt = 0L
    private var loading = false

    private var fg: String? = null
    private var lastQuery = 0L
    private var allowed: Set<String> = emptySet()
    private var allowedAt = 0L

    private var wm: WindowManager? = null
    private var overlay: View? = null
    private var overlayPkg: String? = null

    private val tick = object : Runnable {
        override fun run() {
            val goal = Prefs.goalMinutes(this@BlockService)
            if (!Prefs.blockEnabled(this@BlockService) || goal <= 0 ||
                !Settings.canDrawOverlays(this@BlockService)
            ) {
                hideOverlay()
                stopSelf()
                return
            }
            refreshTotal()
            updateForeground()
            val over = totalMs >= goal * 60_000L
            val pkg = fg
            if (over && pkg != null && pkg !in allowedSet()) showOverlay(goal) else hideOverlay()
            handler.postDelayed(this, 700)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val ch = NotificationChannel("block", "App blocking", NotificationManager.IMPORTANCE_MIN)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        val open = PendingIntent.getActivity(
            this, 3, Intent(this, BlockActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, "block")
            .setSmallIcon(R.drawable.ic_bars)
            .setContentTitle("Daily limit blocking is on")
            .setContentText("Apps are blocked once today's screen time goal is reached")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        try {
            startForeground(77, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } catch (_: Exception) {
            stopSelf()
            return START_NOT_STICKY
        }
        lastQuery = System.currentTimeMillis() - 10 * 60_000L
        handler.removeCallbacks(tick)
        handler.post(tick)
        return START_STICKY
    }

    override fun onDestroy() {
        handler.removeCallbacks(tick)
        hideOverlay()
        worker.shutdownNow()
        super.onDestroy()
    }

    private fun allowedSet(): Set<String> {
        val now = System.currentTimeMillis()
        if (now - allowedAt > 30_000L || allowed.isEmpty()) {
            allowed = BlockHelper.allowed(this)
            allowedAt = now
        }
        return allowed
    }

    private fun refreshTotal() {
        val now = System.currentTimeMillis()
        if (loading || now - lastTotalAt < 20_000L) return
        loading = true
        lastTotalAt = now
        worker.execute {
            try {
                val t = LocalDate.now()
                totalMs = UsageRepository.load(applicationContext, t, t).totalOnDay(t)
            } catch (_: Exception) {
            } finally {
                loading = false
            }
        }
    }

    /** Tracks which app is in front by reading only the newest usage events. */
    private fun updateForeground() {
        try {
            val usm = getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val now = System.currentTimeMillis()
            val ev = usm.queryEvents(lastQuery, now)
            val e = UsageEvents.Event()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> fg = e.packageName
                    UsageEvents.Event.ACTIVITY_PAUSED -> if (fg == e.packageName) fg = null
                    UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                    UsageEvents.Event.KEYGUARD_SHOWN -> fg = null
                }
            }
            lastQuery = now - 500L
        } catch (_: Exception) {
        }
    }

    private fun showOverlay(goalMin: Int) {
        if (overlay != null) return
        val view = buildOverlay(goalMin)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        try {
            val w = getSystemService(WINDOW_SERVICE) as WindowManager
            w.addView(view, lp)
            wm = w
            overlay = view
        } catch (_: Exception) {
        }
    }

    private fun hideOverlay() {
        val v = overlay ?: return
        try {
            wm?.removeView(v)
        } catch (_: Exception) {
        }
        overlay = null
    }

    private fun buildOverlay(goalMin: Int): View {
        val d = resources.displayMetrics.density
        fun dp(v: Float) = (v * d).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.gravity = Gravity.CENTER
        root.setBackgroundColor(Color.parseColor("#F5000000"))
        root.setPadding(dp(32f), dp(32f), dp(32f), dp(32f))

        val title = TextView(this)
        title.text = "Daily limit reached"
        title.setTextColor(Color.WHITE)
        title.textSize = 26f
        title.typeface = Fonts.light(this)
        title.gravity = Gravity.CENTER

        val sub = TextView(this)
        sub.text = "You've used " + Fmt.spaced(totalMs) + " today.\nYour goal was " +
            Fmt.spaced(goalMin * 60_000L) + ". Apps are paused until tomorrow."
        sub.setTextColor(Color.parseColor("#B0B0B5"))
        sub.textSize = 15f
        sub.typeface = Fonts.regular(this)
        sub.gravity = Gravity.CENTER
        sub.setPadding(0, dp(14f), 0, dp(28f))

        val btn = Button(this)
        btn.text = "Go to home screen"
        btn.isAllCaps = false
        btn.setTextColor(Color.WHITE)
        btn.textSize = 16f
        btn.typeface = Fonts.medium(this)
        val bg = GradientDrawable()
        bg.cornerRadius = dp(28f).toFloat()
        bg.setColor(Color.parseColor("#3A78F2"))
        btn.background = bg
        btn.setPadding(dp(32f), 0, dp(32f), 0)
        btn.setOnClickListener {
            try {
                startActivity(
                    Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
            }
            fg = null
            hideOverlay()
        }

        root.addView(title)
        root.addView(sub)
        root.addView(btn, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, dp(52f)))
        return root
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) BlockHelper.sync(context)
    }
}
