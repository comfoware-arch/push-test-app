package com.restaurant.manageralerts

import android.app.Application
import com.restaurant.manageralerts.notif.NotificationHelper

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        NotificationHelper.ensureChannels(this)
    }
}
