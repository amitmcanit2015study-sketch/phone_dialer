package com.amitbharat.phonedialer.ui.incall

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telephony.SmsManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.amitbharat.phonedialer.model.CallLogItem
import com.amitbharat.phonedialer.model.CallType
import com.amitbharat.phonedialer.recording.CallRecorder
import com.amitbharat.phonedialer.repository.CallLogRepository
import com.amitbharat.phonedialer.telecom.ActiveCallState
import com.amitbharat.phonedialer.telecom.CallManager
import com.amitbharat.phonedialer.telecom.DialerInCallService
import com.amitbharat.phonedialer.ui.theme.AccentGreen
import com.amitbharat.phonedialer.ui.theme.AccentRed
import com.amitbharat.phonedialer.ui.theme.PhoneDialerTheme
import com.amitbharat.phonedialer.utils.ContactAvatar
import com.amitbharat.phonedialer.utils.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class InCallActivity : ComponentActivity() {

    private lateinit var callRecorder: CallRecorder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            @Suppress("DEPRECATION") WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
            @Suppress("DEPRECATION") WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            @Suppress("DEPRECATION") WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        handleIntent(intent)

        callRecorder = CallRecorder(this)
        val prefs = PreferencesManager.getInstance(this)
        val callLogRepo = CallLogRepository(this)

        setContent {
            val callState by CallManager.callState.collectAsState()

            LaunchedEffect(callState.hasCall, callState.callState, callState.hasSecondaryCall) {
                if (!callState.hasCall) {
                    finish()
                } else if (callState.callState == Call.STATE_DISCONNECTED && !callState.hasSecondaryCall) {
                    if (callRecorder.isRecording) {
                        val path = callRecorder.stopRecording()
                        if (path != null) {
                            lifecycleScope.launch(Dispatchers.IO) {
                                callLogRepo.addCallLog(
                                    CallLogItem(
                                        number = callState.number,
                                        name = callState.callerName,
                                        callType = if (callState.isIncoming) CallType.INCOMING else CallType.OUTGOING,
                                        timestamp = System.currentTimeMillis(),
                                        duration = callState.callDurationSeconds,
                                        recordingPath = path
                                    )
                                )
                            }
                        }
                    }
                    kotlinx.coroutines.delay(1000)
                    finish()
                } else if (callState.callState == Call.STATE_ACTIVE && prefs.isAutoCallRecordingEnabled() && !callRecorder.isRecording) {
                    val ok = callRecorder.startRecording(callState.number, callState.callerName)
                    if (ok) CallManager.toggleRecording(true)
                }
            }

            PhoneDialerTheme {
                InCallScreen(
                    state = callState,
                    onAnswer = { CallManager.answerCall() },
                    onReject = { CallManager.rejectCall() },
                    onEndCall = {
                        if (callRecorder.isRecording) {
                            val path = callRecorder.stopRecording()
                            if (path != null) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    callLogRepo.addCallLog(
                                        CallLogItem(
                                            number = callState.number,
                                            name = callState.callerName,
                                            callType = if (callState.isIncoming) CallType.INCOMING else CallType.OUTGOING,
                                            timestamp = System.currentTimeMillis(),
                                            duration = callState.callDurationSeconds,
                                            recordingPath = path
                                        )
                                    )
                                }
                            }
                        }
                        CallManager.disconnectCall()
                    },
                    onMuteToggle = { CallManager.setMuted(!callState.isMuted) },
                    onSpeakerToggle = { CallManager.setSpeakerphoneOn(!callState.isSpeakerOn) },
                    onHoldToggle = { CallManager.toggleHold() },
                    onSwapCalls = { CallManager.swapCalls() },
                    onMergeCalls = { CallManager.mergeCalls() },
                    onAddCall = { number -> CallManager.addNewCall(this@InCallActivity, number) },
                    onDisconnectSecondary = { CallManager.disconnectSecondaryCall() },
                    onAnswerWaitingHold = { CallManager.answerCallWaitingAndHold() },
                    onAnswerWaitingEndCurrent = { CallManager.answerCallWaitingAndEndCurrent() },
                    onRejectWaiting = { CallManager.rejectCallWaiting() },
                    onRecordToggle = {
                        if (callRecorder.isRecording) {
                            val path = callRecorder.stopRecording()
                            CallManager.toggleRecording(false)
                            if (path != null) {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    callLogRepo.addCallLog(
                                        CallLogItem(
                                            number = callState.number,
                                            name = callState.callerName,
                                            callType = if (callState.isIncoming) CallType.INCOMING else CallType.OUTGOING,
                                            timestamp = System.currentTimeMillis(),
                                            duration = callState.callDurationSeconds,
                                            recordingPath = path
                                        )
                                    )
                                }
                            }
                        } else {
                            val ok = callRecorder.startRecording(callState.number, callState.callerName)
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == DialerInCallService.ACTION_ANSWER) {
            CallManager.answerCall()
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
    onSwapCalls: () -> Unit,
    onMergeCalls: () -> Unit,
    onAddCall: (String) -> Unit,
    onDisconnectSecondary: () -> Unit,
    onAnswerWaitingHold: () -> Unit,
    onAnswerWaitingEndCurrent: () -> Unit,
    onRejectWaiting: () -> Unit,
    onRecordToggle: () -> Unit,
    onDtmf: (Char) -> Unit,
    onSendQuickSms: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isIncomingRinging = state.callState == Call.STATE_RINGING
    var isKeypadOpen by remember { mutableStateOf(false) }
    var dialedDtmfDigits by remember { mutableStateOf("") }
    var isQuickSmsOpen by remember { mutableStateOf(false) }
    var customSmsText by remember { mutableStateOf("") }
    var isAddCallSheetOpen by remember { mutableStateOf(false) }
    var addCallNumber by remember { mutableStateOf("") }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val durationText = remember(state.callDurationSeconds) {
        val mins = state.callDurationSeconds / 60
        val secs = state.callDurationSeconds % 60
        String.format("%02d:%02d", mins, secs)
    }

    val stateText = when {
        state.isConference -> "Conference Call • $durationText"
        state.callState == Call.STATE_RINGING -> "INCOMING CALL…"
        state.callState == Call.STATE_DIALING || state.callState == Call.STATE_CONNECTING -> "Calling…"
        state.callState == Call.STATE_HOLDING -> "Call on Hold"
        state.callState == Call.STATE_ACTIVE -> durationText
        state.callState == Call.STATE_DISCONNECTED -> "Call Ended"
        else -> "Calling…"
    }

    val displayName = if (state.isConference) "Conference Call" else (state.callerName ?: state.number)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B132B),
                        Color(0xFF1C2541),
                        Color(0xFF0B132B)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(28.dp))

            // Secondary Call Card (when 2 calls exist, e.g. Call on Hold)
            if (state.hasSecondaryCall && !state.hasCallWaiting) {
                val secDurationMins = state.secondaryDurationSeconds / 60
                val secDurationSecs = state.secondaryDurationSeconds % 60
                val secDurationText = String.format("%02d:%02d", secDurationMins, secDurationSecs)
                val secStatus = if (state.secondaryIsHeld) "On Hold • $secDurationText" else "Active • $secDurationText"

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.95f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.45f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .clickable { onSwapCalls() }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ContactAvatar(
                            name = state.secondaryCallerName ?: state.secondaryNumber,
                            photoUri = state.secondaryPhotoUri,
                            size = 38.dp,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = state.secondaryCallerName ?: state.secondaryNumber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = secStatus,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (state.secondaryIsHeld) Color(0xFFF59E0B) else AccentGreen
                            )
                        }

                        // Swap Button
                        Button(
                            onClick = onSwapCalls,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF38BDF8).copy(alpha = 0.25f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Icon(Icons.Default.SwapCalls, contentDescription = "Swap", tint = Color(0xFF38BDF8), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Swap", color = Color(0xFF38BDF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.width(6.dp))

                        // Disconnect Secondary Button
                        IconButton(
                            onClick = onDisconnectSecondary,
                            modifier = Modifier
                                .size(30.dp)
                                .background(AccentRed.copy(alpha = 0.25f), CircleShape)
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = AccentRed, modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            // SIM / Carrier Status Badge
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xFF1E293B).copy(alpha = 0.85f),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.SignalCellularAlt, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (state.isConference) "Conference • HD Audio" else "HD Voice Call",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            // Caller Name & Phone Number
            Text(
                text = displayName,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
            Spacer(Modifier.height(4.dp))
            if (!state.isConference) {
                Text(
                    text = state.number,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.8f)
                )
            } else {
                Text(
                    text = "${state.conferenceParticipantsCount} participants connected",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFA78BFA)
                )
            }
            Spacer(Modifier.height(8.dp))

            // Animated Status Pill
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = when {
                    state.isHeld -> Color(0xFFF59E0B).copy(alpha = 0.25f)
                    isIncomingRinging -> AccentGreen.copy(alpha = 0.25f)
                    state.isConference -> Color(0xFF8B5CF6).copy(alpha = 0.25f)
                    else -> Color(0xFF38BDF8).copy(alpha = 0.25f)
                }
            ) {
                Text(
                    text = stateText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        state.isHeld -> Color(0xFFF59E0B)
                        isIncomingRinging -> AccentGreen
                        state.isConference -> Color(0xFFA78BFA)
                        else -> Color(0xFF38BDF8)
                    },
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp)
                )
            }

            // Recording Indicator
            if (state.isRecording) {
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AccentRed.copy(alpha = 0.35f))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FiberManualRecord, contentDescription = null, tint = AccentRed, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Recording Call…", color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Contact Avatar Image
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = 12.dp,
                    border = androidx.compose.foundation.BorderStroke(2.dp, Color.White.copy(alpha = 0.25f)),
                    color = Color(0xFF1E293B),
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(20.dp))
                ) {
                    ContactAvatar(
                        name = displayName,
                        photoUri = state.photoUri,
                        shape = RoundedCornerShape(20.dp),
                        size = 280.dp,
                        fontSize = 72.sp,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Action Control Panel
            if (isIncomingRinging && !state.hasSecondaryCall) {
                // Initial Incoming Call Screen UI
                Column(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    OutlinedButton(
                        onClick = { isQuickSmsOpen = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Message, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Quick SMS Response", fontWeight = FontWeight.SemiBold)
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decline Button
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onReject,
                                modifier = Modifier
                                    .size(76.dp)
                                    .background(AccentRed, CircleShape)
                            ) {
                                Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(38.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Decline", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }

                        // Answer Button
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = onAnswer,
                                modifier = Modifier
                                    .scale(pulseScale)
                                    .size(76.dp)
                                    .background(AccentGreen, CircleShape)
                            ) {
                                Icon(Icons.Default.Call, contentDescription = "Answer", tint = Color.White, modifier = Modifier.size(38.dp))
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("Answer", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Active / Multi-Call Controls
                Card(
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A).copy(alpha = 0.95f)),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(vertical = 16.dp, horizontal = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Row 1: Keypad, Mute, Speaker, Add Call
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
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                label = "Speaker",
                                isActive = state.isSpeakerOn,
                                onClick = onSpeakerToggle
                            )
                            InCallBtn(
                                icon = Icons.Default.Add,
                                label = "Add Call",
                                isActive = isAddCallSheetOpen,
                                onClick = { isAddCallSheetOpen = true },
                                isEnabled = state.canAddCall
                            )
                        }

                        Spacer(Modifier.height(14.dp))

                        // Row 2: Hold, Swap, Merge, Record
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            InCallBtn(
                                icon = if (state.isHeld) Icons.Default.PlayArrow else Icons.Default.Pause,
                                label = if (state.isHeld) "Resume" else "Hold",
                                isActive = state.isHeld,
                                onClick = onHoldToggle,
                                isEnabled = state.canHold
                            )
                            InCallBtn(
                                icon = Icons.Default.SwapCalls,
                                label = "Swap",
                                isActive = false,
                                onClick = onSwapCalls,
                                isEnabled = state.canSwap
                            )
                            InCallBtn(
                                icon = Icons.AutoMirrored.Filled.CallMerge,
                                label = "Merge",
                                isActive = false,
                                onClick = onMergeCalls,
                                isEnabled = state.canMerge
                            )
                            InCallBtn(
                                icon = Icons.Default.FiberManualRecord,
                                label = "Record",
                                isActive = state.isRecording,
                                onClick = onRecordToggle
                            )
                        }

                        Spacer(Modifier.height(18.dp))

                        Button(
                            onClick = onEndCall,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                            shape = RoundedCornerShape(32.dp),
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(56.dp)
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "End Call", tint = Color.White, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(10.dp))
                            Text("END CALL", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }

        // Call Waiting Overlay (when 2nd call rings during active call)
        if (state.hasCallWaiting) {
            Surface(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = Color(0xFF0F172A),
                shadowElevation = 24.dp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = AccentGreen.copy(alpha = 0.25f),
                            modifier = Modifier.padding(end = 10.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.PhoneCallback, contentDescription = null, tint = AccentGreen, modifier = Modifier.padding(6.dp).size(22.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("INCOMING CALL WAITING", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentGreen)
                            Text(
                                text = state.callWaitingName ?: state.callWaitingNumber,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (state.callWaitingName != null && state.callWaitingNumber.isNotBlank()) {
                                Text(state.callWaitingNumber, fontSize = 13.sp, color = Color.White.copy(alpha = 0.7f))
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Decline
                        Button(
                            onClick = onRejectWaiting,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentRed),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.CallEnd, contentDescription = "Decline", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Decline", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // End Current & Answer
                        OutlinedButton(
                            onClick = onAnswerWaitingEndCurrent,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFF59E0B)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text("End & Answer", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }

                        // Hold Current & Answer
                        Button(
                            onClick = onAnswerWaitingHold,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            shape = RoundedCornerShape(20.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Icon(Icons.Default.Call, contentDescription = "Answer", tint = Color.White, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Hold & Answer", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }

        // Add Call Dialog / Sheet
        AnimatedVisibility(
            visible = isAddCallSheetOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Card(
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                elevation = CardDefaults.cardElevation(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(18.dp)
                        .padding(bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Add Call", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        IconButton(onClick = { isAddCallSheetOpen = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Number input display with Paste & Backspace
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.fillMaxWidth().height(56.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Paste button
                            IconButton(
                                onClick = {
                                    val clip = clipboardManager.getText()?.text?.trim()
                                    if (!clip.isNullOrBlank()) {
                                        val digitsOnly = StringBuilder()
                                        for (c in clip) {
                                            if (c in '0'..'9' || c == '+' || c == '*' || c == '#') digitsOnly.append(c)
                                        }
                                        val dialable = digitsOnly.toString()
                                        if (dialable.isNotBlank()) {
                                            addCallNumber = dialable
                                            Toast.makeText(context, "Pasted: $dialable", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = Color(0xFF38BDF8))
                            }

                            Text(
                                text = if (addCallNumber.isEmpty()) "Enter or paste number…" else addCallNumber,
                                fontSize = if (addCallNumber.isEmpty()) 15.sp else 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (addCallNumber.isEmpty()) Color.Gray else Color.White,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )

                            if (addCallNumber.isNotEmpty()) {
                                IconButton(onClick = { addCallNumber = addCallNumber.dropLast(1) }) {
                                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete", tint = Color.Gray)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Compact Dialpad
                    val dialpadKeys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
                    dialpadKeys.chunked(3).forEach { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            row.forEach { digit ->
                                Box(
                                    modifier = Modifier
                                        .size(68.dp, 48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF1E293B))
                                        .clickable { addCallNumber += digit },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(digit, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // Call Button
                    Button(
                        onClick = {
                            if (addCallNumber.isNotBlank()) {
                                onAddCall(addCallNumber)
                                isAddCallSheetOpen = false
                                addCallNumber = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.fillMaxWidth(0.75f).height(50.dp)
                    ) {
                        Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("CALL", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    }
                }
            }
        }

        // Quick Response Overlay
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
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(18.dp)
                        .padding(bottom = 12.dp),
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
                    modifier = Modifier
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isKeypadOpen = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }

                    if (dialedDtmfDigits.isNotEmpty()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp, horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = dialedDtmfDigits,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1
                            )
                            IconButton(onClick = {
                                if (dialedDtmfDigits.isNotEmpty()) dialedDtmfDigits = dialedDtmfDigits.dropLast(1)
                            }) {
                                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Backspace", tint = Color.White)
                            }
                        }
                    } else {
                        Spacer(Modifier.height(10.dp))
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
                                        .size(72.dp, 52.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color(0xFF1E293B))
                                        .clickable {
                                            dialedDtmfDigits += digit
                                            onDtmf(digit[0])
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(digit, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color.White)
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
    onClick: () -> Unit,
    isEnabled: Boolean = true
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        IconButton(
            onClick = onClick,
            enabled = isEnabled,
            modifier = Modifier
                .size(54.dp)
                .background(
                    if (!isEnabled) Color(0xFF1E293B).copy(alpha = 0.4f)
                    else if (isActive) Color.White
                    else Color(0xFF1E293B),
                    CircleShape
                )
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (!isEnabled) Color.Gray.copy(alpha = 0.5f)
                       else if (isActive) Color.Black
                       else Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            fontSize = 11.sp,
            color = if (!isEnabled) Color.Gray.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.85f),
            fontWeight = FontWeight.Medium
        )
    }
}
