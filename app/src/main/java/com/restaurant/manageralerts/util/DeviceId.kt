package com.restaurant.manageralerts.util

import android.content.Context
import java.util.UUID

object DeviceId {
    fun get(ctx: Context): String {
        val prefs = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
        val existing = prefs.getString("device_id", null)
        if (existing != null) return existing

        val id = UUID.randomUUID().toString()
        prefs.edit().putString("device_id", id).apply()
        return id
    }
}
