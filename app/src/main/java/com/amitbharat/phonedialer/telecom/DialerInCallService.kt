package com.amitbharat.phonedialer.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import com.amitbharat.phonedialer.ui.incall.InCallActivity

class DialerInCallService : InCallService() {

    companion object {
        var instance: DialerInCallService? = null
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        instance = this
        CallManager.setCall(call, applicationContext)

        // Launch in-call full screen
        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.clearCall()
        if (instance == this) {
            instance = null
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }
}
