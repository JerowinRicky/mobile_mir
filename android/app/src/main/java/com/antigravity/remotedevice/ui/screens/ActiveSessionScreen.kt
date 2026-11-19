package com.antigravity.remotedevice.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.antigravity.remotedevice.ui.theme.EmeraldAccent
import com.antigravity.remotedevice.ui.theme.RedDanger
import com.antigravity.remotedevice.ui.theme.Slate700
import com.antigravity.remotedevice.ui.theme.Slate800

@Composable
fun ActiveSessionScreen(
    durationText: String,
    connectedUser: String,
    isStreamConnected: Boolean = true,
    onTerminateSession: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(if (isStreamConnected) EmeraldAccent else Color(0xFFF59E0B), CircleShape)
            )
            Text(
                text = if (isStreamConnected) "Live Remote Session" else "Waiting for Web Client",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Text(
            text = if (isStreamConnected) {
                "Your screen is currently broadcasting to the web dashboard."
            } else {
                "Screen sharing is ready. Go to your browser (http://localhost:3000) and click 'Connect' next to this device."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.LightGray,
            modifier = Modifier.fillMaxWidth()
        )

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Slate800),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (isStreamConnected) "ACTIVE SESSION DURATION" else "WAITING TIME",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    letterSpacing = 2.sp
                )

                Text(
                    text = durationText,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isStreamConnected) EmeraldAccent else Color(0xFFF59E0B)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate700, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "Paired Controller: $connectedUser", color = Color.White, fontSize = 14.sp)
                    Text(
                        text = if (isStreamConnected) "WebRTC Video: Broadcasting" else "WebRTC Video: Waiting for Connection",
                        color = if (isStreamConnected) EmeraldAccent else Color(0xFFF59E0B),
                        fontSize = 13.sp
                    )
                    Text(text = "Channel: Encrypted DTLS-SRTP", color = Color.LightGray, fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onTerminateSession,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = RedDanger)
        ) {
            Icon(
                imageVector = Icons.Default.StopCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.height(24.dp)
            )
            Spacer(modifier = Modifier.padding(4.dp))
            Text("Terminate Remote Session", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}
