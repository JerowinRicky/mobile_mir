package com.antigravity.remotedevice.domain.model

enum class DeviceStatus {
    UNREGISTERED,
    REGISTERED,
    PAIRING,
    PAIRED,
    CONNECTED,
    REVOKED
}

data class DeviceInfo(
    val id: String,
    val name: String,
    val model: String,
    val androidVersion: String,
    val appVersion: String,
    val publicKeyPem: String,
    val status: DeviceStatus
)

data class GeneratedPin(
    val rawPin: String,
    val formattedPin: String,
    val salt: String,
    val pinHash: String,
    val createdAtMs: Long,
    val expiresAtMs: Long
) {
    val isExpired: Boolean
        get() = System.currentTimeMillis() >= expiresAtMs

    val remainingSeconds: Int
        get() {
            val diff = (expiresAtMs - System.currentTimeMillis()) / 1000
            return if (diff > 0) diff.toInt() else 0
        }
}

sealed class PairingUiState {
    object Idle : PairingUiState()
    object Generating : PairingUiState()
    data class ActivePin(val pin: GeneratedPin) : PairingUiState()
    data class IncomingRequest(
        val sessionId: String,
        val clientIp: String,
        val clientUserAgent: String,
        val userEmail: String
    ) : PairingUiState()
    data class Paired(val userEmail: String) : PairingUiState()
    data class Error(val message: String) : PairingUiState()
}

enum class InputActionType {
    TAP,
    SWIPE,
    SCROLL,
    LONG_PRESS,
    GLOBAL_NAV,
    INPUT_TEXT
}

enum class GlobalNavTarget {
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
    QUICK_SETTINGS,
    LOCK_SCREEN
}

data class InputCommand(
    val action: InputActionType,
    val x: Float = 0f,
    val y: Float = 0f,
    val endX: Float = 0f,
    val endY: Float = 0f,
    val durationMs: Long = 50L,
    val navTarget: GlobalNavTarget? = null,
    val text: String? = null
)
