package com.antigravity.remotedevice.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.remotedevice.ui.theme.BluePrimary
import com.antigravity.remotedevice.ui.theme.EmeraldAccent
import com.antigravity.remotedevice.ui.theme.RedDanger
import com.antigravity.remotedevice.ui.theme.Slate800

@Composable
fun PermissionsScreen(
    hasNotificationPermission: Boolean,
    isAccessibilityEnabled: Boolean,
    isMediaProjectionActive: Boolean,
    onRequestNotificationPermission: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestMediaProjection: () -> Unit,
    onNavigateToActiveSession: () -> Unit,
    onNavigateToPairing: () -> Unit,
    onNavigateToRegistration: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Required Permissions",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            IconButton(onClick = onNavigateToRegistration) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Server Settings",
                    tint = Color.LightGray
                )
            }
        }

        Text(
            text = "To allow remote viewing and control, Android requires explicit user consent for background screen capture and accessibility input dispatch.",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray
        )

        PermissionCard(
            title = "1. Foreground Notifications",
            description = "Required to display an active, persistent notification while your screen is being cast or controlled.",
            isGranted = hasNotificationPermission,
            actionLabel = "Grant Permission",
            onAction = onRequestNotificationPermission
        )

        PermissionCard(
            title = "2. Accessibility Input Service",
            description = if (isAccessibilityEnabled) {
                "Enables authorized tap, swipe, and navigation controls dispatched from your browser dashboard."
            } else {
                "Enables remote tap and swipe control. (Optional for viewing)\n\n⚠️ If Android shows 'Controlled by Restricted Setting': Go to Phone Settings > Apps > Remote Device > tap 3 dots (⋮) in top-right > 'Allow restricted settings'."
            },
            isGranted = isAccessibilityEnabled,
            actionLabel = "Open Accessibility Settings",
            onAction = onOpenAccessibilitySettings
        )

        PermissionCard(
            title = "3. Screen Sharing Consent",
            description = "Starts the MediaProjection recording service. Displays Android's official system confirmation dialog.",
            isGranted = isMediaProjectionActive,
            actionLabel = "Request Screen Capture Consent",
            onAction = onRequestMediaProjection
        )

        Spacer(modifier = Modifier.weight(1f))

        val canStream = hasNotificationPermission && isMediaProjectionActive
        val allPermissionsReady = canStream && isAccessibilityEnabled

        Button(
            onClick = onNavigateToActiveSession,
            enabled = canStream,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allPermissionsReady) EmeraldAccent else Color(0xFFD97706),
                disabledContainerColor = Color.DarkGray
            )
        ) {
            Text(
                text = when {
                    allPermissionsReady -> "All Permissions Ready — Open Session"
                    canStream -> "Start Session (View-Only Mode)"
                    else -> "Enable Screen Sharing & Notifications to Continue"
                },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onNavigateToPairing,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Pairing Screen", color = Color.White, fontSize = 13.sp)
            }
            OutlinedButton(
                onClick = onNavigateToRegistration,
                modifier = Modifier.weight(1f).height(44.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Change Server URL", color = Color.White, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Slate800),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (isGranted) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = if (isGranted) EmeraldAccent else RedDanger,
                        modifier = Modifier.height(18.dp)
                    )
                    Spacer(modifier = Modifier.padding(2.dp))
                    Text(
                        text = if (isGranted) "Granted" else "Required",
                        color = if (isGranted) EmeraldAccent else RedDanger,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                }
            }

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = Color.LightGray
            )

            if (!isGranted) {
                OutlinedButton(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = BluePrimary)
                ) {
                    Text(actionLabel, color = Color.White)
                }
            }
        }
    }
}
