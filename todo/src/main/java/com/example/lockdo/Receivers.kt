package com.example.lockdo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Notifier.ACTION_DONE) {
            val id = intent.getLongExtra("id", -1L)
            if (id >= 0) Store.setDone(context, id, true)
        }
        Notifier.refresh(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.refresh(context)
    }
}
