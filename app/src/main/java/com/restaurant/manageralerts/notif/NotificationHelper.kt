package com.restaurant.manageralerts.notif

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import com.restaurant.manageralerts.R

object NotificationHelper {
    private const val CHANNEL_ID = "manager_calls"

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ch = NotificationChannel(
            CHANNEL_ID,
            "Manager Calls",
            NotificationManager.IMPORTANCE_HIGH
        )
        nm.createNotificationChannel(ch)
    }

    fun showCallNotification(ctx: Context, requestId: String, zone: String, table: String) {
        ensureChannels(ctx)

        val notifId = requestId.hashCode()

        val intent = Intent(ctx, TakenReceiver::class.java).apply {
            action = TakenReceiver.ACTION_TAKEN
            putExtra(TakenReceiver.EXTRA_REQUEST_ID, requestId)
        }

        val takenPending = PendingIntentCompat.getBroadcast(
            ctx,
            notifId,
            intent,
            0,
            true
        )

        val n = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🔔 Se necesita servicio")
            .setContentText("Zona: $zone • Mesa: $table")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .addAction(0, "¡La tomo!", takenPending)
            .build()

        NotificationManagerCompat.from(ctx).notify(notifId, n)
    }

    fun cancelByRequestId(ctx: Context, requestId: String) {
        NotificationManagerCompat.from(ctx).cancel(requestId.hashCode())
    }
}
