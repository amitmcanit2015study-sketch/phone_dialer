package com.amitbharat.phonedialer.telecom

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CopyOnWriteArrayList

data class ActiveCallState(
    val hasCall: Boolean = false,
    val callState: Int = Call.STATE_DISCONNECTED, // RINGING, DIALING, ACTIVE, HOLDING, etc.
    val number: String = "",
    val callerName: String? = null,
    val photoUri: String? = null,
    val carrierLabel: String = "HD Voice Call",
    val isIncoming: Boolean = false,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val isHeld: Boolean = false,
    val isRecording: Boolean = false,
    val callDurationSeconds: Long = 0,
    val simSlot: Int = 0,

    // Multi-call fields:
    val hasSecondaryCall: Boolean = false,
    val secondaryNumber: String = "",
    val secondaryCallerName: String? = null,
    val secondaryPhotoUri: String? = null,
    val secondaryCallState: Int = Call.STATE_DISCONNECTED,
    val secondaryIsHeld: Boolean = false,
    val secondaryDurationSeconds: Long = 0,

    // Conference Call flags:
    val isConference: Boolean = false,
    val conferenceParticipantsCount: Int = 0,

    // Capabilities:
    val canHold: Boolean = true,
    val canSwap: Boolean = false,
    val canMerge: Boolean = false,
    val canAddCall: Boolean = true,

    // Call Waiting (incoming call while another call is active):
    val hasCallWaiting: Boolean = false,
    val callWaitingNumber: String = "",
    val callWaitingName: String? = null,
    val callWaitingPhotoUri: String? = null
)

object CallManager {

    private val activeCalls = CopyOnWriteArrayList<Call>()
    var primaryCall: Call? = null
        private set
    var secondaryCall: Call? = null
        private set

    private val _callState = MutableStateFlow(ActiveCallState())
    val callState: StateFlow<ActiveCallState> = _callState.asStateFlow()

    private var appContext: Context? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var durationTimer: Runnable? = null
    private var primaryDuration = 0L
    private var secondaryDuration = 0L

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            updateStateFromCalls()
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            updateStateFromCalls()
        }

        override fun onConferenceableCallsChanged(call: Call, conferenceableCalls: MutableList<Call>?) {
            super.onConferenceableCallsChanged(call, conferenceableCalls)
            updateStateFromCalls()
        }

        override fun onChildrenChanged(call: Call, children: MutableList<Call>?) {
            super.onChildrenChanged(call, children)
            updateStateFromCalls()
        }
    }

    fun hasCalls(): Boolean = activeCalls.isNotEmpty()

    fun setCall(call: Call, context: Context? = null) {
        addCall(call, context)
    }

    fun addCall(call: Call, context: Context? = null) {
        if (context != null) appContext = context.applicationContext
        if (!activeCalls.contains(call)) {
            activeCalls.add(call)
            call.registerCallback(callCallback)
        }

        if (primaryCall == null) {
            primaryCall = call
        } else if (call != primaryCall) {
            // If primary call exists and this new call is incoming (Call Waiting)
            if (call.state == Call.STATE_RINGING) {
                secondaryCall = call
            } else if (primaryCall?.state == Call.STATE_HOLDING || primaryCall?.state == Call.STATE_DISCONNECTED) {
                secondaryCall = primaryCall
                primaryCall = call
            } else {
                secondaryCall = call
            }
        }

        updateStateFromCalls()
    }

    fun removeCall(call: Call) {
        call.unregisterCallback(callCallback)
        activeCalls.remove(call)

        if (call == primaryCall) {
            primaryCall = secondaryCall
            secondaryCall = activeCalls.firstOrNull { it != primaryCall }
            // If newly promoted primary call was held, we can update state; user can resume or auto-resume
        } else if (call == secondaryCall) {
            secondaryCall = activeCalls.firstOrNull { it != primaryCall }
        }

        if (activeCalls.isEmpty()) {
            clearCall()
        } else {
            updateStateFromCalls()
        }
    }

    fun clearCall() {
        stopDurationTimer()
        for (c in activeCalls) {
            try {
                c.unregisterCallback(callCallback)
            } catch (ignored: Exception) {}
        }
        activeCalls.clear()
        primaryCall = null
        secondaryCall = null
        _callState.value = ActiveCallState()
    }

    fun answerCall() {
        val waiting = secondaryCall
        if (waiting != null && waiting.state == Call.STATE_RINGING) {
            answerCallWaitingAndHold()
        } else {
            primaryCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }
    }

    fun answerCallWaitingAndHold() {
        val waiting = secondaryCall ?: return
        primaryCall?.hold()
        waiting.answer(VideoProfile.STATE_AUDIO_ONLY)
        // Swap so newly answered call becomes primary
        secondaryCall = primaryCall
        primaryCall = waiting
        updateStateFromCalls()
    }

    fun answerCallWaitingAndEndCurrent() {
        val waiting = secondaryCall ?: return
        primaryCall?.disconnect()
        waiting.answer(VideoProfile.STATE_AUDIO_ONLY)
        primaryCall = waiting
        secondaryCall = null
        updateStateFromCalls()
    }

    fun rejectCall() {
        val waiting = secondaryCall
        if (waiting != null && waiting.state == Call.STATE_RINGING) {
            waiting.reject(false, null)
        } else {
            primaryCall?.reject(false, null)
        }
    }

    fun rejectCallWaiting() {
        secondaryCall?.reject(false, null)
    }

    fun disconnectCall() {
        if (primaryCall != null) {
            primaryCall?.disconnect()
        } else {
            activeCalls.firstOrNull()?.disconnect()
        }
    }

    fun disconnectSecondaryCall() {
        secondaryCall?.disconnect()
    }

    fun disconnectAllCalls() {
        for (call in activeCalls) {
            try {
                call.disconnect()
            } catch (ignored: Exception) {}
        }
    }

    fun updateAudioState(isMuted: Boolean, isSpeakerOn: Boolean) {
        _callState.value = _callState.value.copy(
            isMuted = isMuted,
            isSpeakerOn = isSpeakerOn
        )
    }

    fun setMuted(muted: Boolean) {
        DialerInCallService.instance?.setMuted(muted)
        _callState.value = _callState.value.copy(isMuted = muted)
        DialerInCallService.instance?.updateOngoingNotification()
    }

    fun setSpeakerphoneOn(on: Boolean) {
        val route = if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE
        DialerInCallService.instance?.setAudioRoute(route)
        _callState.value = _callState.value.copy(isSpeakerOn = on)
        DialerInCallService.instance?.updateOngoingNotification()
    }

    fun toggleHold() {
        val call = primaryCall ?: return
        if (call.state == Call.STATE_HOLDING) {
            call.unhold()
        } else if (call.state == Call.STATE_ACTIVE) {
            call.hold()
        }
        updateStateFromCalls()
    }

    fun swapCalls() {
        val primary = primaryCall ?: return
        val secondary = secondaryCall ?: return

        // If primary is active and secondary is held, swap states
        if (primary.state == Call.STATE_ACTIVE && secondary.state == Call.STATE_HOLDING) {
            primary.hold()
            secondary.unhold()
        } else if (primary.state == Call.STATE_HOLDING && secondary.state == Call.STATE_ACTIVE) {
            secondary.hold()
            primary.unhold()
        } else if (primary.state == Call.STATE_ACTIVE) {
            primary.hold()
            secondary.unhold()
        } else {
            primary.unhold()
        }

        // Swap primary and secondary references
        primaryCall = secondary
        secondaryCall = primary

        // Swap durations
        val tempDur = primaryDuration
        primaryDuration = secondaryDuration
        secondaryDuration = tempDur

        updateStateFromCalls()
    }

    fun mergeCalls() {
        val primary = primaryCall ?: return
        val secondary = secondaryCall ?: return

        try {
            // Android Telecom API to merge two calls into a single conference call
            primary.conference(secondary)
        } catch (e: Exception) {
            try {
                secondary.conference(primary)
            } catch (e2: Exception) {
                try {
                    primary.mergeConference()
                } catch (e3: Exception) {
                    e3.printStackTrace()
                }
            }
        }
        updateStateFromCalls()
    }

    fun addNewCall(context: Context, number: String, simSlot: Int = 0) {
        // Hold current call if active
        if (primaryCall?.state == Call.STATE_ACTIVE) {
            primaryCall?.hold()
        }
        TelecomHelper.makeCall(context, number, simSlot)
    }

    fun sendDtmfTone(digit: Char) {
        primaryCall?.playDtmfTone(digit)
        mainHandler.postDelayed({ primaryCall?.stopDtmfTone() }, 200)
    }

    fun toggleRecording(isRecording: Boolean) {
        _callState.value = _callState.value.copy(isRecording = isRecording)
    }

    private fun resolveContact(number: String, fallbackName: String?, fallbackPhoto: String?): Pair<String?, String?> {
        var callerName = fallbackName
        var photoUri = fallbackPhoto

        // 1. Cached contacts match
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

        // 2. PhoneLookup query fallback
        val context = appContext
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
        return Pair(callerName, photoUri)
    }

    private fun updateStateFromCalls() {
        if (activeCalls.isEmpty() && primaryCall == null) {
            clearCall()
            return
        }

        val primary = primaryCall ?: activeCalls.firstOrNull()
        if (primary == null) {
            clearCall()
            return
        }

        val primaryNumber = primary.details?.handle?.schemeSpecificPart ?: ""
        val (primaryCallerName, primaryPhotoUri) = resolveContact(
            primaryNumber,
            primary.details?.callerDisplayName ?: _callState.value.callerName,
            _callState.value.photoUri
        )

        // Check if primary or any call is conference
        val isConference = activeCalls.any {
            it.details?.hasProperty(Call.Details.PROPERTY_CONFERENCE) == true ||
            (it.children != null && it.children.isNotEmpty())
        }

        // Secondary Call info
        val secondary = secondaryCall ?: activeCalls.find { it != primary }
        val hasSecondary = secondary != null
        val secondaryNumber = secondary?.details?.handle?.schemeSpecificPart ?: ""
        val (secondaryName, secondaryPhoto) = if (hasSecondary) {
            resolveContact(secondaryNumber, secondary?.details?.callerDisplayName, null)
        } else {
            Pair(null, null)
        }

        val isIncoming = primary.state == Call.STATE_RINGING
        val isHeld = primary.state == Call.STATE_HOLDING

        // Call Waiting: secondary call is ringing while primary is active or held
        val hasCallWaiting = secondary != null && secondary.state == Call.STATE_RINGING

        // Capabilities:
        // Can Swap: 2 calls and at least one is held or active
        val canSwap = hasSecondary && !isConference && !hasCallWaiting &&
                (primary.state == Call.STATE_ACTIVE || primary.state == Call.STATE_HOLDING) &&
                (secondary?.state == Call.STATE_ACTIVE || secondary?.state == Call.STATE_HOLDING)

        // Can Merge: 2 active/held calls and not already in conference
        val canMerge = hasSecondary && !isConference && !hasCallWaiting &&
                (primary.state == Call.STATE_ACTIVE || primary.state == Call.STATE_HOLDING) &&
                (secondary?.state == Call.STATE_ACTIVE || secondary?.state == Call.STATE_HOLDING)

        val canHold = primary.state == Call.STATE_ACTIVE || primary.state == Call.STATE_HOLDING
        val canAddCall = !isConference && activeCalls.size < 2 &&
                (primary.state == Call.STATE_ACTIVE || primary.state == Call.STATE_HOLDING)

        if ((primary.state == Call.STATE_ACTIVE || secondary?.state == Call.STATE_ACTIVE) && durationTimer == null) {
            startDurationTimer()
        } else if (activeCalls.all { it.state == Call.STATE_DISCONNECTED }) {
            stopDurationTimer()
        }

        _callState.value = _callState.value.copy(
            hasCall = true,
            callState = primary.state,
            number = primaryNumber,
            callerName = if (!primaryCallerName.isNullOrEmpty()) primaryCallerName else primaryNumber,
            photoUri = primaryPhotoUri,
            isIncoming = isIncoming,
            isHeld = isHeld,

            hasSecondaryCall = hasSecondary,
            secondaryNumber = secondaryNumber,
            secondaryCallerName = if (!secondaryName.isNullOrEmpty()) secondaryName else secondaryNumber,
            secondaryPhotoUri = secondaryPhoto,
            secondaryCallState = secondary?.state ?: Call.STATE_DISCONNECTED,
            secondaryIsHeld = secondary?.state == Call.STATE_HOLDING,

            isConference = isConference,
            conferenceParticipantsCount = if (isConference) activeCalls.size.coerceAtLeast(2) else 0,

            canHold = canHold,
            canSwap = canSwap,
            canMerge = canMerge,
            canAddCall = canAddCall,

            hasCallWaiting = hasCallWaiting,
            callWaitingNumber = if (hasCallWaiting) secondaryNumber else "",
            callWaitingName = if (hasCallWaiting) (secondaryName ?: secondaryNumber) else null,
            callWaitingPhotoUri = if (hasCallWaiting) secondaryPhoto else null
        )
    }

    private fun startDurationTimer() {
        primaryDuration = 0L
        secondaryDuration = 0L
        durationTimer = object : Runnable {
            override fun run() {
                if (primaryCall?.state == Call.STATE_ACTIVE) {
                    primaryDuration++
                }
                if (secondaryCall?.state == Call.STATE_ACTIVE) {
                    secondaryDuration++
                }
                _callState.value = _callState.value.copy(
                    callDurationSeconds = primaryDuration,
                    secondaryDurationSeconds = secondaryDuration
                )
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
