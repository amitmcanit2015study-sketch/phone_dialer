package com.amitbharat.phonedialer.telecom

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveCallState(
    val hasCall: Boolean = false,
    val callState: Int = Call.STATE_DISCONNECTED, // RINGING, DIALING, ACTIVE, HOLDING, etc.
    val number: String = "",
    val callerName: String? = null,
    val photoUri: String? = null,
    val carrierLabel: String = "Calling via SIM 1",
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

    private var appContext: Context? = null
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

    fun setCall(call: Call, context: Context? = null) {
        if (context != null) appContext = context.applicationContext
        currentCall?.unregisterCallback(callCallback)
        currentCall = call
        call.registerCallback(callCallback)
        updateStateFromCall(call, appContext)
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

    private fun updateStateFromCall(call: Call, ctx: Context? = null) {
        val handle = call.details?.handle
        val number = handle?.schemeSpecificPart ?: ""
        var callerName = call.details?.callerDisplayName
        var photoUri: String? = _callState.value.photoUri

        // 1. Check in-memory cached contacts from ContactsRepository for instant match
        if (number.isNotBlank()) {
            val cleanIncoming = number.replace(Regex("[^0-9]"), "")
            val cachedContact = com.amitbharat.phonedialer.repository.ContactsRepository.getCachedContacts().find { c ->
                c.numbers.any { n ->
                    val cleanC = n.replace(Regex("[^0-9]"), "")
                    cleanC == cleanIncoming || (cleanC.length >= 10 && cleanIncoming.length >= 10 && cleanC.takeLast(10) == cleanIncoming.takeLast(10))
                }
            }
            if (cachedContact != null) {
                if (callerName.isNullOrBlank() || callerName == number) {
                    callerName = cachedContact.name
                }
                if (photoUri.isNullOrBlank()) {
                    photoUri = cachedContact.photoUri
                }
            }
        }

        // 2. Lookup contact details from PhoneLookup if still missing
        val context = ctx ?: appContext
        if (context != null && number.isNotBlank() && (callerName.isNullOrBlank() || photoUri.isNullOrBlank())) {
            try {
                val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
                val cursor: Cursor? = context.contentResolver.query(
                    uri,
                    arrayOf(
                        ContactsContract.PhoneLookup.DISPLAY_NAME,
                        ContactsContract.PhoneLookup.PHOTO_URI,
                        ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI
                    ),
                    null,
                    null,
                    null
                )
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIdx = it.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        val photoIdx = it.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_URI)
                        val thumbIdx = it.getColumnIndex(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI)
                        if (nameIdx >= 0) {
                            val name = it.getString(nameIdx)
                            if (!name.isNullOrBlank() && callerName.isNullOrBlank()) callerName = name
                        }
                        if (photoIdx >= 0 && photoUri.isNullOrBlank()) {
                            photoUri = it.getString(photoIdx)
                        }
                        if (thumbIdx >= 0 && photoUri.isNullOrBlank()) {
                            photoUri = it.getString(thumbIdx)
                        }
                    }
                }
            } catch (ignored: Exception) {}
        }

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
            callerName = if (!callerName.isNullOrEmpty()) callerName else _callState.value.callerName,
            photoUri = photoUri,
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
