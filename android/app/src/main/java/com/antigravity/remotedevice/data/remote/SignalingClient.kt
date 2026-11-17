package com.antigravity.remotedevice.data.remote

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.TimeUnit

sealed class SignalingEvent {
    data class Connected(val role: String, val sessionId: String) : SignalingEvent()
    data class SdpAnswerReceived(val sdp: String) : SignalingEvent()
    data class IceCandidateReceived(val sdpMid: String, val sdpMLineIndex: Int, val candidate: String) : SignalingEvent()
    data class PeerDisconnected(val role: String) : SignalingEvent()
    data class SessionTerminated(val reason: String) : SignalingEvent()
    data class Error(val message: String) : SignalingEvent()
}

class SignalingClient {

    companion object {
        private const val TAG = "SignalingClient"
    }

    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + Job())

    private val _events = MutableSharedFlow<SignalingEvent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun connect(serverWsUrl: String, sessionId: String, deviceToken: String) {
        val url = "$serverWsUrl/ws/signaling/?session_id=$sessionId&device_token=$deviceToken"
        val request = Request.Builder().url(url).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "Signaling WebSocket connected to $sessionId")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val type = json.optString("type")

                    when (type) {
                        "connection_established" -> {
                            val role = json.optString("role")
                            val sId = json.optString("session_id")
                            scope.launch { _events.emit(SignalingEvent.Connected(role, sId)) }
                        }
                        "sdp_answer" -> {
                            val sdp = json.getString("sdp")
                            scope.launch { _events.emit(SignalingEvent.SdpAnswerReceived(sdp)) }
                        }
                        "ice_candidate" -> {
                            val candObj = json.getJSONObject("candidate")
                            val candidate = candObj.getString("candidate")
                            val sdpMid = candObj.optString("sdpMid", "0")
                            val sdpMLineIndex = candObj.optInt("sdpMLineIndex", 0)
                            scope.launch {
                                _events.emit(SignalingEvent.IceCandidateReceived(sdpMid, sdpMLineIndex, candidate))
                            }
                        }
                        "peer_disconnected" -> {
                            val role = json.optString("role")
                            scope.launch { _events.emit(SignalingEvent.PeerDisconnected(role)) }
                        }
                        "session_terminated" -> {
                            val reason = json.optString("reason", "Terminated")
                            scope.launch { _events.emit(SignalingEvent.SessionTerminated(reason)) }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing signaling message: ${e.message}", e)
                    scope.launch { _events.emit(SignalingEvent.Error(e.message ?: "Parsing error")) }
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "Signaling WebSocket failure: ${t.message}", t)
                scope.launch { _events.emit(SignalingEvent.Error(t.message ?: "Connection failure")) }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.i(TAG, "Signaling WebSocket closed: code=$code, reason=$reason")
            }
        })
    }

    fun sendSdpOffer(sdp: String) {
        val payload = JSONObject().apply {
            put("type", "sdp_offer")
            put("sdp", sdp)
        }
        webSocket?.send(payload.toString())
    }

    fun sendIceCandidate(sdpMid: String, sdpMLineIndex: Int, candidate: String) {
        val candObj = JSONObject().apply {
            put("candidate", candidate)
            put("sdpMid", sdpMid)
            put("sdpMLineIndex", sdpMLineIndex)
        }
        val payload = JSONObject().apply {
            put("type", "ice_candidate")
            put("candidate", candObj)
        }
        webSocket?.send(payload.toString())
    }

    fun disconnect() {
        webSocket?.close(1000, "Client initiated disconnect")
        webSocket = null
    }
}
