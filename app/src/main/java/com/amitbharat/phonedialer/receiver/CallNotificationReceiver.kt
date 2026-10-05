package com.amitbharat.phonedialer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.amitbharat.phonedialer.telecom.CallManager
import com.amitbharat.phonedialer.telecom.DialerInCallService

class CallNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DialerInCallService.ACTION_HANGUP -> {
                CallManager.disconnectCall()
            }
            DialerInCallService.ACTION_REJECT -> {
                CallManager.rejectCall()
            }
            DialerInCallService.ACTION_ANSWER -> {
                CallManager.answerCall()
            }
            DialerInCallService.ACTION_SPEAKER -> {
                val current = CallManager.callState.value.isSpeakerOn
                CallManager.setSpeakerphoneOn(!current)
            }
            DialerInCallService.ACTION_MUTE -> {
                val current = CallManager.callState.value.isMuted
                CallManager.setMuted(!current)
            }
        }
    }
}
