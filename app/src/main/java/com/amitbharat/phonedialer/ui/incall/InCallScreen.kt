package com.amitbharat.phonedialer.ui.incall

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amitbharat.phonedialer.recording.CallRecorder
import com.amitbharat.phonedialer.telecom.ActiveCallState
import com.amitbharat.phonedialer.telecom.CallManager
import com.amitbharat.phonedialer.ui.theme.AccentGreen
import com.amitbharat.phonedialer.ui.theme.AccentRed
import com.amitbharat.phonedialer.ui.theme.PhoneDialerTheme
import com.amitbharat.phonedialer.utils.ContactAvatar

class InCallActivity : ComponentActivity() {

    private lateinit var callRecorder: CallRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            @Suppress("DEPRECATION") WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            @Suppress("DEPRECATION") WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            @Suppress("DEPRECATION") WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        callRecorder = CallRecorder(this)

        setContent {
            val callState by CallManager.callState.collectAsState()

            LaunchedEffect(callState.hasCall, callState.callState) {
                if (!callState.hasCall || callState.callState == Call.STATE_DISCONNECTED) {
                    if (callRecorder.isRecording) {
                        callRecorder.stopRecording()
                    }
                    finish()
                }
            }

            PhoneDialerTheme {
                InCallScreen(
                    state = callState,
                    onAnswer = { CallManager.answerCall() },
                    onReject = { CallManager.rejectCall() },
                    onEndCall = { CallManager.disconnectCall() },
                    onMuteToggle = { CallManager.setMuted(!callState.isMuted) },
                    onSpeakerToggle = { CallManager.setSpeakerphoneOn(!callState.isSpeakerOn) },
                    onHoldToggle = { CallManager.toggleHold() },
                    onRecordToggle = {
                        if (callRecorder.isRecording) {
                            callRecorder.stopRecording()
                            CallManager.toggleRecording(false)
                        } else {
                            val ok = callRecorder.startRecording(callState.number)
                            if (ok) CallManager.toggleRecording(true)
                        }
                    },
                    onDtmf = { CallManager.sendDtmfTone(it) }
                )
            }
        }
    }
}

@Composable
fun InCallScreen(
    state: ActiveCallState,
    onAnswer: () -> Unit,
    onReject: () -> Unit,
    onEndCall: () -> Unit,
    onMuteToggle: () -> Unit,
    onSpeakerToggle: () -> Unit,
    onHoldToggle: () -> Unit,
    onRecordToggle: () -> Unit,
    onDtmf: (Char) -> Unit
) {
    val context = LocalContext.current
    val isIncomingRinging = state.callState == Call.STATE_RINGING
    var isKeypadOpen by remember { mutableStateOf(false) }
    var inCallNote by remember { mutableStateOf("") }
    var isNotesOpen by remember { mutableStateOf(false) }

    val durationText = remember(state.callDurationSeconds) {
        val mins = state.callDurationSeconds / 60
        val secs = state.callDurationSeconds % 60
        String.format("%02d:%02d", mins, secs)
    }

    val stateText = when (state.callState) {
        Call.STATE_RINGING -> "Incoming Call…"
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling…"
        Call.STATE_ACTIVE -> durationText
        Call.STATE_HOLDING -> "On Hold ()"
        Call.STATE_DISCONNECTED -> "Call Ended"
        else -> "Calling…"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF131824), Color(0xFF0A0D14), Color(0xFF05070B))
                )
            )
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // 1. Top Carrier & SIM Badge
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.85f),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Jio 5G • HD Voice",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            // 2. Caller Name & Details
            Text(
                text = state.callerName ?: state.number,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Mobile ",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stateText,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.callState == Call.STATE_ACTIVE) AccentGreen else Color(0xFF94A3B8)
            )

            // Live Call Recording Badge
            if (state.isRecording) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentRed.copy(alpha = 0.2f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("?? REC Live Audio", color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(28.dp))

            // 3. Large Contact Photo / Avatar
            ContactAvatar(
                name = state.callerName ?: state.number,
                photoUri = state.photoUri,
                size = 136.dp,
                fontSize = 48.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // 4. In-Call Action Control Panel
            if (isIncomingRinging) {
                // Incoming Call: Swipe/Tap Accept or Decline + Quick SMS
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decline Button
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onReject,
                                modifier = Modifier.size(76.dp).background(AccentRed, CircleShape)
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(38.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Decline", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                        }

                        // Accept Button
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onAnswer,
                                modifier = Modifier.size(76.dp).background(AccentGreen, CircleShape)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Answer", tint = Color.White, modifier = Modifier.size(38.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Answer", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                        }
                    }
                }
            } else {
                // Active / Outgoing In-Call Panel
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF131B2A).copy(alpha = 0.95f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 6 Buttons in 2 Rows
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            InCallBtn(
                                icon = Icons.Default.Dialpad,
                                label = "Keypad",
                                isActive = isKeypadOpen,
                                onClick = { isKeypadOpen = !isKeypadOpen }
                            )
                            InCallBtn(
                                icon = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                                label = "Mute",
                                isActive = state.isMuted,
                                onClick = onMuteToggle
                            )
                            InCallBtn(
                                icon = Icons.Default.VolumeUp,
                                label = "Speaker",
                                isActive = state.isSpeakerOn,
                                onClick = onSpeakerToggle
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            InCallBtn(
                                icon = Icons.Default.Pause,
                                label = "Hold",
                                isActive = state.isHeld,
                                onClick = onHoldToggle
                            )
                            InCallBtn(
                                icon = Icons.Default.FiberManualRecord,
                                label = "Record",
                                isActive = state.isRecording,
                                onClick = onRecordToggle
                            )
                            InCallBtn(
                                icon = Icons.Default.EditNote,
                                label = "Notes",
                                isActive = isNotesOpen,
                                onClick = { isNotesOpen = !isNotesOpen }
                            )
                        }

                        Spacer(Modifier.height(20.dp))

                        // Large Ergonomic End Call Pill Button
                        Button(
                            onClick = onEndCall,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                            shape = RoundedCornerShape(32.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(60.dp)
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White, modifier = Modifier.size(30.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("END CALL", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // In-Call DTMF Keypad Overlay
        AnimatedVisibility(
            visible = isKeypadOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("DTMF Keypad", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = { isKeypadOpen = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    val keypadDigits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
                    keypadDigits.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { digit ->
                                Box(
                                    modifier = Modifier
                                        .size(68.dp, 48.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF1E293B))
                                        .clickable { onDtmf(digit[0]) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(digit, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InCallBtn(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .background(
                    if (isActive) Color.White else Color(0xFF1E293B),
                    CircleShape
                )
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isActive) Color.Black else Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(text = label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.85f), fontWeight = FontWeight.Medium)
    }
}
