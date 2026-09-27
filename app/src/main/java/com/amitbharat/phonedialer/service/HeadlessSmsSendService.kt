package com.amitbharat.phonedialer.service

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * Service required by Android Telephony for Default SMS App compliance
 * (handling respond via message for quick responses).
 */
class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
