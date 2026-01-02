package com.restaurant.manageralerts.notif

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.restaurant.manageralerts.work.ClaimWorker

class TakenReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TAKEN) return
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return

        // snappy UX: cancel immediately on this device
        NotificationHelper.cancelByRequestId(context, requestId)

        // claim on backend (atomic)
        ClaimWorker.enqueue(context, requestId)
    }

    companion object {
        const val ACTION_TAKEN = "com.restaurant.manageralerts.ACTION_TAKEN"
        const val EXTRA_REQUEST_ID = "requestId"
    }
}
