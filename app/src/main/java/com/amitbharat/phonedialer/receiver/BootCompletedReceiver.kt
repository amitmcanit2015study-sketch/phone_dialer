package com.amitbharat.phonedialer.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.amitbharat.phonedialer.service.DialerKeepAliveService
import com.amitbharat.phonedialer.utils.PreferencesManager

/**
 * Receiver invoked when the device boots up or app is updated,
 * pre-loading the Phone Dialer process into RAM.
 */
class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == Intent.ACTION_BOOT_COMPLETED ||
            action == "android.intent.action.QUICKBOOT_POWERON" ||
            action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = PreferencesManager.getInstance(context)
            if (prefs.isKeepAliveEnabled()) {
                DialerKeepAliveService.startService(context)
            }
        }
    }
}
