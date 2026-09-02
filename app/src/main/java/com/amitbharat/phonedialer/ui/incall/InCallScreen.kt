package com.amitbharat.phonedialer.ui.incall

import android.os.Bundle
import android.telecom.Call
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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

class InCallActivity : ComponentActivity() {

    private lateinit var callRecorder: CallRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        callRecorder = CallRecorder(this)

        setContent {
            val callState by CallManager.callState.collectAsState()

            // When call disconnects, finish activity gracefully
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
    val isIncomingRinging = state.callState == Call.STATE_RINGING

    val durationText = remember(state.callDurationSeconds) {
        val mins = state.callDurationSeconds / 60
        val secs = state.callDurationSeconds % 60
        String.format("%02d:%02d", mins, secs)
    }

    val stateText = when (state.callState) {
        Call.STATE_RINGING -> "Incoming Call…"
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "Dialing…"
        Call.STATE_ACTIVE -> durationText
        Call.STATE_HOLDING -> "On Hold ()"
        Call.STATE_DISCONNECTED -> "Call Ended"
        else -> "Calling…"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B0F19))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        // Large Caller Avatar
        Box(
            modifier = Modifier
                .size(110.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E293B)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = (state.callerName ?: state.number).take(1).uppercase(),
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(Modifier.height(20.dp))

        // Caller Identity
        Text(
            text = state.callerName ?: state.number,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        if (state.callerName != null) {
            Text(
                text = state.number,
                fontSize = 17.sp,
                color = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }

        // Call State / Live Duration
        Text(
            text = stateText,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (state.callState == Call.STATE_ACTIVE) AccentGreen else Color(0xFF94A3B8),
            modifier = Modifier.padding(top = 8.dp)
        )

        if (state.isRecording) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                Text("?? Recording In-Call Audio", color = AccentRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // In-Call Controls or Incoming Action Buttons
        if (isIncomingRinging) {
            // Incoming Call Screen: Accept / Reject
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Reject Button
                IconButton(
                    onClick = onReject,
                    modifier = Modifier.size(72.dp).background(AccentRed, CircleShape)
                ) {
                    Icon(Icons.Default.CallEnd, contentDescription = "Reject", tint = Color.White, modifier = Modifier.size(36.dp))
                }

                // Accept Button
                IconButton(
                    onClick = onAnswer,
                    modifier = Modifier.size(72.dp).background(AccentGreen, CircleShape)
                ) {
                    Icon(Icons.Default.Call, contentDescription = "Accept", tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }
        } else {
            // Active Call Grid Controls (Mute, Speaker, Hold, Record)
            Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    InCallControlButton(
                        icon = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        label = "Mute",
                        isActive = state.isMuted,
                        onClick = onMuteToggle
                    )
                    InCallControlButton(
                        icon = Icons.Default.VolumeUp,
                        label = "Speaker",
                        isActive = state.isSpeakerOn,
                        onClick = onSpeakerToggle
                    )
                    InCallControlButton(
                        icon = Icons.Default.Pause,
                        label = "Hold",
                        isActive = state.isHeld,
                        onClick = onHoldToggle
                    )
                    InCallControlButton(
                        icon = Icons.Default.FiberManualRecord,
                        label = "Record",
                        isActive = state.isRecording,
                        onClick = onRecordToggle
                    )
                }

                Spacer(Modifier.height(28.dp))

                // End Call Red Button
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    IconButton(
                        onClick = onEndCall,
                        modifier = Modifier.size(72.dp).background(AccentRed, CircleShape)
                    ) {
                        Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun InCallControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(60.dp)
                .background(if (isActive) Color.White else Color(0xFF1E293B), CircleShape)
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (isActive) Color.Black else Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(text = label, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
    }
}
