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
import kotlinx.coroutines.delay
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
    private var serverUrl by mutableStateOf("http://10.0.2.2:8000")
    private var pairedEmail by mutableStateOf<String?>(null)
    private var showConsentDialog by mutableStateOf(false)
    private var pendingConsentRequest by mutableStateOf<Triple<String, String, String>?>(null)

    // Permissions state
    private var hasNotificationPermission by mutableStateOf(false)
    private var isAccessibilityEnabled by mutableStateOf(false)
    private var isMediaProjectionActive by mutableStateOf(false)

    // MediaProjection Activity Result Launcher
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            MediaProjectionService.startService(this, result.resultCode, result.data!!)
            isMediaProjectionActive = true
            Toast.makeText(this, "Screen capture consent granted", Toast.LENGTH_SHORT).show()
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
        val deviceId = secureStorage.getOrCreateDeviceId()
        val deviceToken = secureStorage.getDeviceToken() ?: ""

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
                if (response.isSuccessful) {
                    Log.i("MainActivity", "Pairing initiated on backend")
                }
            } catch (e: Exception) {
                Log.w("MainActivity", "Pairing initiate network warning: ${e.message}")
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
                    showConsentDialog = false
                    isPaired = true
                    pairedEmail = email
                    secureStorage.setPaired(true)
                    currentScreen = Screen.PERMISSIONS
                    Toast.makeText(this, "Web client approved!", Toast.LENGTH_SHORT).show()
                },
                onReject = {
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

                        // Demo fallback simulation after 4 seconds
                        lifecycleScope.launch {
                            delay(4000L)
                            if (!isPaired && pendingConsentRequest == null) {
                                pendingConsentRequest = Triple("admin@remotedevice.internal", "192.168.1.10", "Chrome Desktop")
                                showConsentDialog = true
                            }
                        }
                    },
                    onRevokePairing = {
                        secureStorage.setPaired(false)
                        isPaired = false
                        currentPin = null
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
