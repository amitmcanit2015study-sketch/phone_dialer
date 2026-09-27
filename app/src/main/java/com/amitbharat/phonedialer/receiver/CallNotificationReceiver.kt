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
        }
    }
}
