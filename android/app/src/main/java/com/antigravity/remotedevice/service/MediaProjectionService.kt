package com.antigravity.remotedevice.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.antigravity.remotedevice.R
import com.antigravity.remotedevice.RemoteApplication
import com.antigravity.remotedevice.data.local.SecureStorage
import com.antigravity.remotedevice.data.remote.SignalingClient
import com.antigravity.remotedevice.data.remote.SignalingEvent
import com.antigravity.remotedevice.ui.MainActivity
import com.antigravity.remotedevice.webrtc.WebRtcManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.webrtc.PeerConnection

class MediaProjectionService : Service() {

    companion object {
        private const val TAG = "MediaProjectionService"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.antigravity.remotedevice.action.START_PROJECTION"
        const val ACTION_STOP = "com.antigravity.remotedevice.action.STOP_PROJECTION"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_RESULT_DATA = "extra_result_data"
        const val EXTRA_SESSION_ID = "extra_session_id"

        var isRunning = false
            private set

        fun startService(context: Context, resultCode: Int, data: Intent, sessionId: String = "") {
            val intent = Intent(context, MediaProjectionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_RESULT_DATA, data)
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, MediaProjectionService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val binder = LocalBinder()
    private var webRtcManager: WebRtcManager? = null
    private var signalingClient: SignalingClient? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    inner class LocalBinder : Binder() {
        fun getService(): MediaProjectionService = this@MediaProjectionService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                try {
                    val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
                    val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(EXTRA_RESULT_DATA)
                    }
                    val sessionId = intent.getStringExtra(EXTRA_SESSION_ID) ?: ""

                    if (resultCode != 0 && resultData != null) {
                        startForegroundWithNotification()
                        val success = initWebRtcAndSignaling(resultData, sessionId)
                        if (success) {
                            isRunning = true
                            Log.i(TAG, "MediaProjectionService started successfully.")
                        } else {
                            Log.w(TAG, "Screen capture setup failed, stopping service.")
                            stopProjection()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                            isRunning = false
                        }
                    } else {
                        Log.w(TAG, "Invalid resultCode ($resultCode) or null resultData")
                        stopSelf()
                    }
                } catch (e: Throwable) {
                    Log.e(TAG, "Exception in onStartCommand ACTION_START", e)
                    stopProjection()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                    isRunning = false
                }
            }
            ACTION_STOP -> {
                stopProjection()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                isRunning = false
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        val disconnectIntent = Intent(this, MediaProjectionService::class.java).apply {
            action = ACTION_STOP
        }
        val disconnectPendingIntent = PendingIntent.getService(
            this,
            0,
            disconnectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, RemoteApplication.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(android.R.drawable.presence_video_online)
            .setOngoing(true)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.action_disconnect),
                disconnectPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun initWebRtcAndSignaling(resultData: Intent, sessionId: String): Boolean {
        return try {
            val secureStorage = SecureStorage(this)
            val deviceToken = secureStorage.getDeviceToken() ?: ""
            val serverUrl = secureStorage.getServerUrl().replace("http://", "ws://").replace("https://", "wss://")

            signalingClient = SignalingClient()
            webRtcManager = WebRtcManager(this, signalingClient!!)

            val metrics = resources.displayMetrics
            val captureStarted = webRtcManager?.startScreenCapture(resultData, metrics) ?: false
            if (!captureStarted) {
                Log.w(TAG, "webRtcManager.startScreenCapture returned false.")
            }

            val iceServers = listOf(
                PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
                PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer()
            )
            webRtcManager?.createPeerConnection(iceServers)

            // Listen for incoming signaling events
            serviceScope.launch {
                signalingClient?.events?.collect { event ->
                    when (event) {
                        is SignalingEvent.Connected -> {
                            Log.i(TAG, "Signaling connected. Creating SDP Offer...")
                            webRtcManager?.createOffer()
                        }
                        is SignalingEvent.SdpAnswerReceived -> {
                            Log.i(TAG, "Remote SDP Answer received.")
                            webRtcManager?.setRemoteAnswer(event.sdp)
                        }
                        is SignalingEvent.IceCandidateReceived -> {
                            webRtcManager?.addRemoteIceCandidate(event.sdpMid, event.sdpMLineIndex, event.candidate)
                        }
                        is SignalingEvent.PeerDisconnected, is SignalingEvent.SessionTerminated -> {
                            Log.w(TAG, "Peer disconnected or session ended.")
                            stopProjection()
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                        is SignalingEvent.Error -> {
                            Log.e(TAG, "Signaling error: ${event.message}")
                        }
                    }
                }
            }

            if (sessionId.isNotEmpty() && deviceToken.isNotEmpty()) {
                signalingClient?.connect(serverUrl, sessionId, deviceToken)
            }
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed in initWebRtcAndSignaling: ${e.message}", e)
            false
        }
    }

    private fun stopProjection() {
        try {
            webRtcManager?.stop()
            webRtcManager = null
            signalingClient?.disconnect()
            signalingClient = null
            isRunning = false
        } catch (e: Throwable) {
            Log.e(TAG, "Error in stopProjection: ${e.message}", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProjection()
    }
}
