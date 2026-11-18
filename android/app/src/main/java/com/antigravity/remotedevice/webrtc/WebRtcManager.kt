package com.antigravity.remotedevice.webrtc

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.util.Log
import com.antigravity.remotedevice.data.remote.SignalingClient
import com.antigravity.remotedevice.domain.model.GlobalNavTarget
import com.antigravity.remotedevice.domain.model.InputCommand
import com.antigravity.remotedevice.domain.model.InputActionType
import com.antigravity.remotedevice.service.RemoteAccessibilityService
import org.json.JSONObject
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStream
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

class WebRtcManager(
    private val context: Context,
    private val signalingClient: SignalingClient
) {

    companion object {
        private const val TAG = "WebRtcManager"
        private const val VIDEO_TRACK_ID = "ANTIGRAVITY_SCREEN_TRACK"
        private const val STREAM_ID = "ANTIGRAVITY_SCREEN_STREAM"
        private const val DATA_CHANNEL_LABEL = "input-channel"
    }

    private val eglBase: EglBase by lazy { EglBase.create() }
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var videoSource: VideoSource? = null
    private var videoTrack: VideoTrack? = null
    private var screenCapturer: ScreenCapturerAndroid? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var dataChannel: DataChannel? = null

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        try {
            val options = PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(false)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)

            val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
            val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory)
                .createPeerConnectionFactory()
            Log.i(TAG, "PeerConnectionFactory initialized successfully.")
        } catch (e: Throwable) {
            Log.e(TAG, "Error initializing PeerConnectionFactory", e)
        }
    }

    /**
     * Initializes ScreenCapturer with the user-approved MediaProjection Intent.
     * Note: In Android 14+, the resultData token must only be consumed once.
     */
    fun startScreenCapture(resultData: Intent, displayMetrics: DisplayMetrics): Boolean {
        return try {
            screenCapturer = ScreenCapturerAndroid(resultData, object : MediaProjection.Callback() {
                override fun onStop() {
                    super.onStop()
                    Log.w(TAG, "MediaProjection revoked by OS callback")
                    stop()
                }
            })

            surfaceTextureHelper = SurfaceTextureHelper.create("ScreenCaptureThread", eglBase.eglBaseContext)
            videoSource = peerConnectionFactory?.createVideoSource(screenCapturer?.isScreencast ?: true)

            screenCapturer?.initialize(
                surfaceTextureHelper,
                context,
                videoSource?.capturerObserver
            )

            // Ensure width and height are even multiples of 2 for hardware encoder compatibility
            val rawWidth = minOf(displayMetrics.widthPixels, 1080)
            val rawHeight = minOf(displayMetrics.heightPixels, 1920)
            val width = if (rawWidth % 2 != 0) rawWidth - 1 else rawWidth
            val height = if (rawHeight % 2 != 0) rawHeight - 1 else rawHeight
            val fps = 30

            Log.i(TAG, "Starting screen capture at ${width}x${height} @ ${fps}fps")
            screenCapturer?.startCapture(width, height, fps)
            videoTrack = peerConnectionFactory?.createVideoTrack(VIDEO_TRACK_ID, videoSource)
            Log.i(TAG, "Screen capturer and video track created successfully.")
            true
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to start ScreenCapturerAndroid: ${e.message}", e)
            false
        }
    }

    /**
     * Creates PeerConnection and sets up ICE servers and DataChannel.
     */
    fun createPeerConnection(iceServers: List<PeerConnection.IceServer>) {
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED
            bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
            rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
            keyType = PeerConnection.KeyType.ECDSA
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate) {
                Log.d(TAG, "Local ICE candidate gathered: ${candidate.sdp}")
                signalingClient.sendIceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}

            override fun onSignalingChange(state: PeerConnection.SignalingState?) {
                Log.d(TAG, "SignalingState changed: $state")
            }

            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "IceConnectionState changed: $state")
            }

            override fun onIceConnectionReceivingChange(receiving: Boolean) {}

            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                Log.d(TAG, "IceGatheringState changed: $state")
            }

            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onRenegotiationNeeded() {}

            override fun onDataChannel(channel: DataChannel?) {
                channel?.let { setupDataChannel(it) }
            }

            override fun onTrack(transceiver: org.webrtc.RtpTransceiver?) {}
        })

        // Add local screen video track
        videoTrack?.let {
            peerConnection?.addTrack(it, listOf(STREAM_ID))
        }

        // Setup local DataChannel for remote control inputs
        val dcInit = DataChannel.Init().apply {
            ordered = true
            maxRetransmits = 0 // Low latency, drop stale touch events if out of order
        }
        val localDc = peerConnection?.createDataChannel(DATA_CHANNEL_LABEL, dcInit)
        localDc?.let { setupDataChannel(it) }
    }

    private fun setupDataChannel(channel: DataChannel) {
        this.dataChannel = channel
        channel.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}

            override fun onStateChange() {
                Log.i(TAG, "DataChannel state: ${channel.state()}")
            }

            override fun onMessage(buffer: DataChannel.Buffer) {
                val data = buffer.data
                val bytes = ByteArray(data.remaining())
                data.get(bytes)
                val jsonStr = String(bytes, StandardCharsets.UTF_8)
                handleRemoteInputCommand(jsonStr)
            }
        })
    }

    private fun handleRemoteInputCommand(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val action = json.getString("action")

            val command = when (action) {
                "TAP" -> InputCommand(
                    action = InputActionType.TAP,
                    x = json.getDouble("x").toFloat(),
                    y = json.getDouble("y").toFloat(),
                    durationMs = json.optLong("duration_ms", 50L)
                )
                "LONG_PRESS" -> InputCommand(
                    action = InputActionType.LONG_PRESS,
                    x = json.getDouble("x").toFloat(),
                    y = json.getDouble("y").toFloat(),
                    durationMs = json.optLong("duration_ms", 750L)
                )
                "SWIPE", "SCROLL" -> InputCommand(
                    action = if (action == "SWIPE") InputActionType.SWIPE else InputActionType.SCROLL,
                    x = json.getDouble("start_x").toFloat(),
                    y = json.getDouble("start_y").toFloat(),
                    endX = json.getDouble("end_x").toFloat(),
                    endY = json.getDouble("end_y").toFloat(),
                    durationMs = json.optLong("duration_ms", 250L)
                )
                "GLOBAL_NAV" -> {
                    val target = when (json.getString("target")) {
                        "BACK" -> GlobalNavTarget.BACK
                        "HOME" -> GlobalNavTarget.HOME
                        "RECENTS" -> GlobalNavTarget.RECENTS
                        else -> null
                    }
                    InputCommand(action = InputActionType.GLOBAL_NAV, navTarget = target)
                }
                "INPUT_TEXT" -> InputCommand(
                    action = InputActionType.INPUT_TEXT,
                    text = json.getString("text")
                )
                else -> null
            }

            command?.let {
                RemoteAccessibilityService.instance?.executeCommand(it)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling remote input: ${e.message}", e)
        }
    }

    /**
     * Generates and transmits the SDP Offer to the signaling server.
     */
    fun createOffer() {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "false"))
        }

        peerConnection?.createOffer(object : SdpAdapter("createOffer") {
            override fun onCreateSuccess(desc: SessionDescription) {
                super.onCreateSuccess(desc)
                peerConnection?.setLocalDescription(object : SdpAdapter("setLocalDescription") {
                    override fun onSetSuccess() {
                        super.onSetSuccess()
                        Log.i(TAG, "Local SDP Offer set. Sending to signaling server...")
                        signalingClient.sendSdpOffer(desc.description)
                    }
                }, desc)
            }
        }, constraints)
    }

    fun setRemoteAnswer(sdp: String) {
        val desc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
        peerConnection?.setRemoteDescription(SdpAdapter("setRemoteDescription"), desc)
    }

    fun addRemoteIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val iceCand = IceCandidate(sdpMid, sdpMLineIndex, candidate)
        peerConnection?.addIceCandidate(iceCand)
    }

    fun stop() {
        try {
            screenCapturer?.stopCapture()
            screenCapturer?.dispose()
            screenCapturer = null

            surfaceTextureHelper?.dispose()
            surfaceTextureHelper = null

            videoSource?.dispose()
            videoSource = null

            dataChannel?.close()
            dataChannel = null

            peerConnection?.close()
            peerConnection = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping WebRtcManager: ${e.message}", e)
        }
    }

    private open class SdpAdapter(private val tag: String) : org.webrtc.SdpObserver {
        override fun onCreateSuccess(desc: SessionDescription) {
            Log.d(TAG, "$tag onCreateSuccess")
        }

        override fun onSetSuccess() {
            Log.d(TAG, "$tag onSetSuccess")
        }

        override fun onCreateFailure(error: String) {
            Log.e(TAG, "$tag onCreateFailure: $error")
        }

        override fun onSetFailure(error: String) {
            Log.e(TAG, "$tag onSetFailure: $error")
        }
    }
}
