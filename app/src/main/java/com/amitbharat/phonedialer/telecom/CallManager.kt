package com.amitbharat.phonedialer.telecom

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import androidx.compose.runtime.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveCallState(
    val hasCall: Boolean = false,
    val callState: Int = Call.STATE_DISCONNECTED, // RINGING, DIALING, ACTIVE, HOLDING, etc.
    val number: String = "",
    val callerName: String? = null,
    val isIncoming: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isHeld: Boolean = false,
    val isRecording: Boolean = false,
    val callDurationSeconds: Long = 0,
    val simSlot: Int = 0
)

object CallManager {

    private var currentCall: Call? = null
    private val _callState = MutableStateFlow(ActiveCallState())
    val callState: StateFlow<ActiveCallState> = _callState.asStateFlow()

    private val mainHandler = Handler(Looper.getMainLooper())
    private var durationTimer: Runnable? = null
    private var currentDuration = 0L

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            updateStateFromCall(call)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            updateStateFromCall(call)
        }
    }

    fun setCall(call: Call) {
        currentCall?.unregisterCallback(callCallback)
        currentCall = call
        call.registerCallback(callCallback)
        updateStateFromCall(call)
    }

    fun clearCall() {
        stopDurationTimer()
        currentCall?.unregisterCallback(callCallback)
        currentCall = null
        _callState.value = ActiveCallState()
    }

    fun answerCall() {
        currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    fun rejectCall() {
        currentCall?.reject(false, null)
    }

    fun disconnectCall() {
        currentCall?.disconnect()
    }

    fun setMuted(muted: Boolean) {
        DialerInCallService.instance?.setMuted(muted)
        _callState.value = _callState.value.copy(isMuted = muted)
    }

    fun setSpeakerphoneOn(on: Boolean) {
        val route = if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        DialerInCallService.instance?.setAudioRoute(route)
        _callState.value = _callState.value.copy(isSpeakerOn = on)
    }

    fun toggleHold() {
        val call = currentCall ?: return
        if (call.state == Call.STATE_HOLDING) {
            call.unhold()
        } else if (call.state == Call.STATE_ACTIVE) {
            call.hold()
        }
    }

    fun sendDtmfTone(digit: Char) {
        currentCall?.playDtmfTone(digit)
        mainHandler.postDelayed({ currentCall?.stopDtmfTone() }, 200)
    }

    fun toggleRecording(isRecording: Boolean) {
        _callState.value = _callState.value.copy(isRecording = isRecording)
    }

    private fun updateStateFromCall(call: Call) {
        val handle = call.details.handle
        val number = handle?.schemeSpecificPart ?: ""
        val callerName = call.details.callerDisplayName
        val isIncoming = call.state == Call.STATE_RINGING

        if (call.state == Call.STATE_ACTIVE && durationTimer == null) {
            startDurationTimer()
        } else if (call.state == Call.STATE_DISCONNECTED) {
            stopDurationTimer()
        }

        _callState.value = _callState.value.copy(
            hasCall = true,
            callState = call.state,
            number = number,
            callerName = if (!callerName.isNullOrEmpty()) callerName else null,
            isIncoming = isIncoming,
            isHeld = call.state == Call.STATE_HOLDING
        )
    }

    private fun startDurationTimer() {
        currentDuration = 0L
        durationTimer = object : Runnable {
            override fun run() {
                currentDuration++
                _callState.value = _callState.value.copy(callDurationSeconds = currentDuration)
                mainHandler.postDelayed(this, 1000)
            }
        }
        mainHandler.post(durationTimer!!)
    }

    private fun stopDurationTimer() {
        durationTimer?.let { mainHandler.removeCallbacks(it) }
        durationTimer = null
    }
}
