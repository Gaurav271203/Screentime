package com.example.screentime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class TaskReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == TaskNotifier.ACTION_DONE) {
            val id = intent.getLongExtra("id", -1L)
            if (id >= 0) TaskStore.setDone(context, id, true)
        }
        TaskNotifier.refresh(context)
    }
}
