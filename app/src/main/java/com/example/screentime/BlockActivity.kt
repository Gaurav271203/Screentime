package com.example.screentime

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast

class BlockActivity : Activity() {

    private lateinit var sw: Switch
    private lateinit var tvGoal: TextView
    private lateinit var tvOverlay: TextView
    private lateinit var tvNotif: TextView
    private var ignore = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_block)
        setupHeader("Block apps")

        sw = findViewById(R.id.swBlock)
        tvGoal = findViewById(R.id.tvBlockGoal)
        tvOverlay = findViewById(R.id.tvOverlayState)
        tvNotif = findViewById(R.id.tvNotifState)

        sw.setOnCheckedChangeListener { _, on ->
            if (ignore) return@setOnCheckedChangeListener
            if (on) {
                if (Prefs.goalMinutes(this) <= 0) {
                    toast("Set a screen time goal first")
                    startActivity(Intent(this, GoalActivity::class.java))
                    setSwitch(false)
                    return@setOnCheckedChangeListener
                }
                if (!Settings.canDrawOverlays(this)) {
                    toast("Allow \"Display over other apps\" for this app, then come back")
                    startActivity(BlockHelper.overlaySettingsIntent(this))
                }
            }
            Prefs.setBlockEnabled(this, on)
            BlockHelper.sync(this)
        }
        findViewById<View>(R.id.rowOverlay).setOnClickListener {
            startActivity(BlockHelper.overlaySettingsIntent(this))
        }
        findViewById<View>(R.id.rowNotif).setOnClickListener {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }
        findViewById<View>(R.id.rowAllowed).setOnClickListener {
            startActivity(Intent(this, AllowedAppsActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        bind()
        BlockHelper.sync(this)
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        bind()
    }

    private fun setSwitch(v: Boolean) {
        ignore = true
        sw.isChecked = v
        ignore = false
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    private fun bind() {
        setSwitch(Prefs.blockEnabled(this))
        val g = Prefs.goalMinutes(this)
        tvGoal.text = if (g > 0) "Daily limit: " + Fmt.spaced(g * 60_000L) + " (from your screen time goal)"
        else "No goal set. Set a screen time goal to use blocking."
        tvOverlay.text = if (Settings.canDrawOverlays(this)) "Allowed" else "Not allowed – tap to allow"
        val n = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        tvNotif.text = if (n) "Allowed" else "Not allowed – tap to allow"
    }
}
