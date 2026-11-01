package com.jarvis.ai

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class JarvisApplication : Application() {

    companion object {
        const val CHANNEL_ID_ORB = "jarvis_orb_channel"
        const val CHANNEL_ID_AUTOMATION = "jarvis_automation_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val orbChannel = NotificationChannel(
                CHANNEL_ID_ORB,
                "JARVIS Floating Arc Orb",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the floating JARVIS assistant orb active over your screen"
                setShowBadge(false)
            }

            val automationChannel = NotificationChannel(
                CHANNEL_ID_AUTOMATION,
                "JARVIS Autonomous Engine",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifies when JARVIS performs screen actions or requests confirmations"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.apply {
                createNotificationChannel(orbChannel)
                createNotificationChannel(automationChannel)
            }
        }
    }
}
