package com.amitbharat.phonedialer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Receiver required by Android Telecom / RoleManager for Default SMS App compliance.
 */
class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // MMS push deliver receiver for default SMS app compliance
    }
}
