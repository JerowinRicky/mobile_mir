package com.antigravity.remotedevice.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.antigravity.remotedevice.data.local.KeystoreManager
import com.antigravity.remotedevice.data.local.SecureStorage
import com.antigravity.remotedevice.domain.crypto.PinGenerator
import com.antigravity.remotedevice.domain.model.GeneratedPin
import com.antigravity.remotedevice.service.MediaProjectionService
import com.antigravity.remotedevice.service.RemoteAccessibilityService
import com.antigravity.remotedevice.ui.components.ConsentDialog
import com.antigravity.remotedevice.ui.screens.ActiveSessionScreen
import com.antigravity.remotedevice.ui.screens.DeviceRegistrationScreen
import com.antigravity.remotedevice.ui.screens.PairingScreen
import com.antigravity.remotedevice.ui.screens.PermissionsScreen
import com.antigravity.remotedevice.ui.theme.RemoteDeviceTheme
import com.antigravity.remotedevice.ui.theme.Slate900
import com.antigravity.remotedevice.util.PermissionHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class Screen {
    REGISTRATION,
    PAIRING,
    PERMISSIONS,
    ACTIVE_SESSION
}

class MainActivity : ComponentActivity() {

    private lateinit var keystoreManager: KeystoreManager
    private lateinit var secureStorage: SecureStorage
    private val pinGenerator = PinGenerator(ttlSeconds = 300L)
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private var currentPin by mutableStateOf<GeneratedPin?>(null)
    private var currentScreen by mutableStateOf(Screen.REGISTRATION)
    private var isRegistered by mutableStateOf(false)
    private var isPaired by mutableStateOf(false)
    private var serverUrl by mutableStateOf("http://10.0.2.2:8008")
    private var pairedEmail by mutableStateOf<String?>(null)
    private var showConsentDialog by mutableStateOf(false)
    private var pendingConsentRequest by mutableStateOf<Triple<String, String, String>?>(null)
    private var activePairingSessionId: String? = null
    private var pairingPollJob: Job? = null

    // Permissions state
    private var hasNotificationPermission by mutableStateOf(false)
    private var isAccessibilityEnabled by mutableStateOf(false)
    private var isMediaProjectionActive by mutableStateOf(false)

    // MediaProjection Activity Result Launcher
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                MediaProjectionService.startService(this, result.resultCode, result.data!!)
                isMediaProjectionActive = true
                Toast.makeText(this, "Screen capture consent granted", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                isMediaProjectionActive = false
                Log.e("MainActivity", "Failed to start MediaProjectionService", e)
                Toast.makeText(this, "Failed to start screen capture: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            isMediaProjectionActive = false
            Toast.makeText(this, "Screen capture consent denied", Toast.LENGTH_SHORT).show()
        }
    }

    // Notification Permission Launcher (Android 13+)
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasNotificationPermission = granted
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        keystoreManager = KeystoreManager()
        secureStorage = SecureStorage(this)

        isRegistered = secureStorage.isRegistered()
        isPaired = secureStorage.isPaired()
        serverUrl = secureStorage.getServerUrl()

        if (isPaired) {
            currentScreen = Screen.PERMISSIONS
        } else if (isRegistered) {
            currentScreen = Screen.PAIRING
        }

        setContent {
            RemoteDeviceTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Slate900
                ) {
                    AppNavigation()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissions()
    }

    private fun refreshPermissions() {
        hasNotificationPermission = PermissionHelper.hasNotificationPermission(this)
        isAccessibilityEnabled = RemoteAccessibilityService.isServiceConnected
        isMediaProjectionActive = MediaProjectionService.isRunning
    }

    private fun registerDeviceWithBackend() {
        val cleanUrl = serverUrl.trim().removeSuffix("/")
        val deviceId = secureStorage.getOrCreateDeviceId()
        val deviceName = secureStorage.getDeviceName()
        val pubKey = try { keystoreManager.getPublicKeyPem() } catch (e: Exception) { "" }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("hardware_fingerprint", deviceId)
                    put("device_name", deviceName)
                    put("public_key_pem", pubKey)
                }
                val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("$cleanUrl/api/v1/devices/register/")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val resJson = JSONObject(body)
                    val token = resJson.optString("device_token")
                    if (token.isNotEmpty()) {
                        secureStorage.setDeviceToken(token)
                    }
                    val backendDeviceId = resJson.optString("device_id")
                    if (backendDeviceId.isNotEmpty()) {
                        secureStorage.setBackendDeviceId(backendDeviceId)
                    }
                    secureStorage.setRegistered(true)
                    withContext(Dispatchers.Main) {
                        isRegistered = true
                        currentScreen = Screen.PAIRING
                        Toast.makeText(this@MainActivity, "Device registered successfully!", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Registration failed: HTTP ${response.code}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Could not reach server: ${e.message}", Toast.LENGTH_LONG).show()
                    // Still allow navigating for offline testing
                    secureStorage.setRegistered(true)
                    isRegistered = true
                    currentScreen = Screen.PAIRING
                }
            }
        }
    }

    private fun initiatePairingWithBackend(newPin: GeneratedPin) {
        val cleanUrl = serverUrl.trim().removeSuffix("/")
        val deviceId = secureStorage.getBackendDeviceId() ?: secureStorage.getOrCreateDeviceId()
        val deviceToken = secureStorage.getDeviceToken() ?: ""

        pairingPollJob?.cancel()
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("device_id", deviceId)
                    put("device_token", deviceToken)
                    put("pin_hash", newPin.pinHash)
                    put("pin_salt", newPin.salt)
                    put("expires_in", 300)
                }
                val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("$cleanUrl/api/v1/pairing/initiate/")
                    .post(requestBody)
                    .build()

                val response = httpClient.newCall(request).execute()
                val body = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val respJson = JSONObject(body)
                    val sessionId = respJson.optString("pairing_session_id")
                    if (sessionId.isNotEmpty()) {
                        activePairingSessionId = sessionId
                        Log.i("MainActivity", "Pairing initiated with session ID: $sessionId")
                        startPairingStatusPolling(cleanUrl, sessionId, deviceToken)
                    }
                } else {
                    Log.e("MainActivity", "Pairing initiate failed: HTTP ${response.code} $body")
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Server rejected PIN: HTTP ${response.code}", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Pairing initiate network warning: ${e.message}")
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Pairing network error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun startPairingStatusPolling(cleanUrl: String, sessionId: String, deviceToken: String) {
        pairingPollJob?.cancel()
        pairingPollJob = lifecycleScope.launch(Dispatchers.IO) {
            while (isActive && currentPin != null && !currentPin!!.isExpired && !isPaired) {
                try {
                    val request = Request.Builder()
                        .url("$cleanUrl/api/v1/pairing/device-status/$sessionId/?device_token=$deviceToken")
                        .get()
                        .build()
                    val response = httpClient.newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        val json = JSONObject(body)
                        val status = json.optString("status")
                        if (status == "AWAITING_APPROVAL") {
                            val email = json.optString("target_user_email", "Authorized Web User")
                            val ip = json.optString("client_ip", "Web Client")
                            val ua = json.optString("client_user_agent", "Browser")
                            withContext(Dispatchers.Main) {
                                pendingConsentRequest = Triple(email, ip, ua)
                                showConsentDialog = true
                            }
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.d("MainActivity", "Polling device pairing status: ${e.message}")
                }
                delay(1500L)
            }
        }
    }

    private fun approvePairingWithBackend(sessionId: String, approved: Boolean) {
        val cleanUrl = serverUrl.trim().removeSuffix("/")
        val deviceToken = secureStorage.getDeviceToken() ?: ""

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val json = JSONObject().apply {
                    put("pairing_session_id", sessionId)
                    put("device_token", deviceToken)
                    put("approved", approved)
                }
                val requestBody = json.toString().toRequestBody("application/json".toMediaTypeOrNull())
                val request = Request.Builder()
                    .url("$cleanUrl/api/v1/pairing/approve/")
                    .post(requestBody)
                    .build()
                val response = httpClient.newCall(request).execute()
                Log.i("MainActivity", "Pairing approve response code: ${response.code}")
            } catch (e: Exception) {
                Log.e("MainActivity", "Failed to send pairing approval to backend: ${e.message}")
            }
        }
    }

    @Composable
    private fun AppNavigation() {
        // Countdown timer ticker for active PIN
        LaunchedEffect(currentPin) {
            while (currentPin != null && !currentPin!!.isExpired) {
                delay(1000L)
                currentPin = currentPin // trigger recomposition
            }
        }

        if (showConsentDialog && pendingConsentRequest != null) {
            val (email, ip, ua) = pendingConsentRequest!!
            ConsentDialog(
                userEmail = email,
                clientIp = ip,
                clientUserAgent = ua,
                onApprove = {
                    val sessionId = activePairingSessionId
                    if (sessionId != null) {
                        approvePairingWithBackend(sessionId, true)
                    }
                    showConsentDialog = false
                    isPaired = true
                    pairedEmail = email
                    secureStorage.setPaired(true)
                    currentScreen = Screen.PERMISSIONS
                    Toast.makeText(this, "Web client approved!", Toast.LENGTH_SHORT).show()
                },
                onReject = {
                    val sessionId = activePairingSessionId
                    if (sessionId != null) {
                        approvePairingWithBackend(sessionId, false)
                    }
                    showConsentDialog = false
                    pendingConsentRequest = null
                    Toast.makeText(this, "Connection rejected", Toast.LENGTH_SHORT).show()
                }
            )
        }

        when (currentScreen) {
            Screen.REGISTRATION -> {
                val pubKeyPem = remember {
                    try {
                        keystoreManager.getPublicKeyPem().take(120) + "..."
                    } catch (e: Exception) {
                        "EC secp256r1 hardware key initialized"
                    }
                }
                DeviceRegistrationScreen(
                    deviceId = secureStorage.getOrCreateDeviceId(),
                    deviceName = secureStorage.getDeviceName(),
                    serverUrl = serverUrl,
                    isRegistered = isRegistered,
                    publicKeySnippet = pubKeyPem,
                    onServerUrlChange = { newUrl ->
                        serverUrl = newUrl
                        secureStorage.setServerUrl(newUrl)
                    },
                    onRegisterDevice = {
                        registerDeviceWithBackend()
                    },
                    onNavigateToPairing = {
                        currentScreen = Screen.PAIRING
                    }
                )
            }
            Screen.PAIRING -> {
                PairingScreen(
                    pin = currentPin,
                    isPaired = isPaired,
                    pairedUserEmail = pairedEmail,
                    onGeneratePin = {
                        val newPin = pinGenerator.generate()
                        currentPin = newPin
                        initiatePairingWithBackend(newPin)
                    },
                    onRevokePairing = {
                        pairingPollJob?.cancel()
                        secureStorage.setPaired(false)
                        isPaired = false
                        currentPin = null
                        activePairingSessionId = null
                        currentScreen = Screen.PAIRING
                        Toast.makeText(this, "Pairing revoked", Toast.LENGTH_SHORT).show()
                    },
                    onNavigateToPermissions = {
                        currentScreen = Screen.PERMISSIONS
                    }
                )
            }
            Screen.PERMISSIONS -> {
                PermissionsScreen(
                    hasNotificationPermission = hasNotificationPermission,
                    isAccessibilityEnabled = isAccessibilityEnabled,
                    isMediaProjectionActive = isMediaProjectionActive,
                    onRequestNotificationPermission = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    onOpenAccessibilitySettings = {
                        PermissionHelper.openAccessibilitySettings(this)
                    },
                    onRequestMediaProjection = {
                        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
                    },
                    onNavigateToActiveSession = {
                        currentScreen = Screen.ACTIVE_SESSION
                    }
                )
            }
            Screen.ACTIVE_SESSION -> {
                var secondsElapsed by remember { mutableStateOf(0) }
                LaunchedEffect(Unit) {
                    while (true) {
                        delay(1000L)
                        secondsElapsed++
                    }
                }
                val durationText = String.format("%02d:%02d", secondsElapsed / 60, secondsElapsed % 60)

                ActiveSessionScreen(
                    durationText = durationText,
                    connectedUser = pairedEmail ?: "Web Dashboard (Paired)",
                    onTerminateSession = {
                        MediaProjectionService.stopService(this)
                        isMediaProjectionActive = false
                        currentScreen = Screen.PERMISSIONS
                        Toast.makeText(this, "Remote session terminated", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}
