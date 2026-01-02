package com.restaurant.manageralerts.fcm

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.restaurant.manageralerts.notif.NotificationHelper

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val type = data["type"] ?: return

        when (type) {
            "call" -> {
                val requestId = data["requestId"] ?: return
                val zone = data["zone"] ?: "?"
                val table = data["table"] ?: "?"
                NotificationHelper.showCallNotification(this, requestId, zone, table)
            }
            "dismiss" -> {
                val requestId = data["requestId"] ?: return
                NotificationHelper.cancelByRequestId(this, requestId)
            }
        }
    }

    override fun onNewToken(token: String) {
        // Optional: you can auto re-register to supabase here
        // For POC, user presses subscribe.
    }
}
