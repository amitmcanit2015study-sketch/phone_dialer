package com.amitbharat.phonedialer.ui.incall

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telephony.SmsManager
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
import androidx.compose.material.icons.automirrored.filled.Send
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
import com.amitbharat.phonedialer.utils.PreferencesManager

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
        val prefs = PreferencesManager.getInstance(this)

        setContent {
            val callState by CallManager.callState.collectAsState()

            LaunchedEffect(callState.hasCall, callState.callState) {
                if (!callState.hasCall || callState.callState == Call.STATE_DISCONNECTED) {
                    if (callRecorder.isRecording) {
                        callRecorder.stopRecording()
                    }
                    finish()
                } else if (callState.callState == Call.STATE_ACTIVE && prefs.isAutoCallRecordingEnabled() && !callRecorder.isRecording) {
                    val ok = callRecorder.startRecording(callState.number)
                    if (ok) CallManager.toggleRecording(true)
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
                    onDtmf = { CallManager.sendDtmfTone(it) },
                    onSendQuickSms = { msg ->
                        try {
                            val sms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                getSystemService(SmsManager::class.java)
                            } else {
                                @Suppress("DEPRECATION") SmsManager.getDefault()
                            }
                            sms?.sendTextMessage(callState.number, null, msg, null, null)
                            Toast.makeText(this@InCallActivity, "Quick SMS sent", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:${callState.number}")).apply {
                                putExtra("sms_body", msg)
                            }
                            startActivity(intent)
                        }
                        CallManager.rejectCall()
                    }
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
    onDtmf: (Char) -> Unit,
    onSendQuickSms: (String) -> Unit
) {
    val context = LocalContext.current
    val isIncomingRinging = state.callState == Call.STATE_RINGING
    var isKeypadOpen by remember { mutableStateOf(false) }
    var isQuickSmsOpen by remember { mutableStateOf(false) }
    var customSmsText by remember { mutableStateOf("") }

    val durationText = remember(state.callDurationSeconds) {
        val mins = state.callDurationSeconds / 60
        val secs = state.callDurationSeconds % 60
        String.format("%02d:%02d", mins, secs)
    }

    val stateText = when (state.callState) {
        Call.STATE_RINGING -> "Incoming Call…"
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling…"
        Call.STATE_ACTIVE -> durationText
        Call.STATE_HOLDING -> "On Hold"
        Call.STATE_DISCONNECTED -> "Call Ended"
        else -> "Calling…"
    }

    val displayName = state.callerName ?: state.number

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F172A),
                        Color(0xFF090D16),
                        Color(0xFF030508)
                    )
                )
            )
    ) {
        // Main Caller Content Layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))

            // SIM Badge
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

            // Caller Name & Details
            Text(
                text = displayName,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = state.number,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White.copy(alpha = 0.8f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stateText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = if (state.callState == Call.STATE_ACTIVE) AccentGreen else Color(0xFF38BDF8)
            )

            // Live Call Recording Indicator
            if (state.isRecording) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentRed.copy(alpha = 0.3f))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = AccentRed, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Recording Call", color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(28.dp))

            // Contact Photo / Large Initials Hero Avatar
            ContactAvatar(
                name = displayName,
                photoUri = state.photoUri,
                size = 150.dp,
                fontSize = 54.sp
            )

            Spacer(modifier = Modifier.weight(1f))

            // Action Control Panel
            if (isIncomingRinging) {
                // Incoming Call: Quick Response SMS + Accept / Decline
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Quick Message Trigger
                    OutlinedButton(
                        onClick = { isQuickSmsOpen = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        Icon(Icons.Default.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Quick SMS Response")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onReject,
                                modifier = Modifier.size(76.dp).background(AccentRed, CircleShape)
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(38.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Decline", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onAnswer,
                                modifier = Modifier.size(76.dp).background(AccentGreen, CircleShape)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Answer", tint = Color.White, modifier = Modifier.size(38.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Answer", color = Color.White.copy(alpha = 0.9f), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                // Active / Outgoing In-Call Grid
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.92f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 18.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
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
                                icon = Icons.Default.Sms,
                                label = "Quick SMS",
                                isActive = isQuickSmsOpen,
                                onClick = { isQuickSmsOpen = true }
                            )
                        }

                        Spacer(Modifier.height(20.dp))

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

        // Quick Response Sheet Overlay
        AnimatedVisibility(
            visible = isQuickSmsOpen,
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
                    modifier = Modifier.padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Quick SMS Response", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        IconButton(onClick = { isQuickSmsOpen = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    listOf(
                        "Can't talk right now. What's up?",
                        "I'll call you back later.",
                        "On my way, call you soon.",
                        "In a meeting, will message you."
                    ).forEach { preset ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF1E293B),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clickable {
                                    onSendQuickSms(preset)
                                    isQuickSmsOpen = false
                                }
                        ) {
                            Text(
                                text = preset,
                                color = Color.White,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customSmsText,
                            onValueChange = { customSmsText = it },
                            placeholder = { Text("Custom message…", color = Color.Gray) },
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = {
                                if (customSmsText.isNotBlank()) {
                                    onSendQuickSms(customSmsText)
                                    isQuickSmsOpen = false
                                }
                            },
                            modifier = Modifier.size(46.dp).background(AccentGreen, CircleShape)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        // DTMF Keypad Overlay
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
