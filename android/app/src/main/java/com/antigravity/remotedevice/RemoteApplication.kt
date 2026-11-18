package com.antigravity.remotedevice

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import org.webrtc.PeerConnectionFactory

class RemoteApplication : Application() {

    companion object {
        private const val TAG = "RemoteApplication"
        const val NOTIFICATION_CHANNEL_ID = "remote_device_service_channel"
        lateinit var instance: RemoteApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        initWebRtc()
    }

    private fun initWebRtc() {
        try {
            val options = PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)
            Log.i(TAG, "WebRTC PeerConnectionFactory initialized successfully.")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize WebRTC PeerConnectionFactory", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = getString(R.string.channel_description)
                setShowBadge(true)
                enableVibration(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }
}
