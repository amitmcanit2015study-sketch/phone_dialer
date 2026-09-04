package com.amitbharat.phonedialer.telecom

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import com.amitbharat.phonedialer.R
import com.amitbharat.phonedialer.ui.incall.InCallActivity

class DialerInCallService : InCallService() {

    companion object {
        var instance: DialerInCallService? = null
        private const val CHANNEL_ID = "ongoing_call_channel"
        private const val NOTIFICATION_ID = 1001
        const val ACTION_HANGUP = "com.amitbharat.phonedialer.ACTION_HANGUP"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HANGUP) {
            CallManager.disconnectCall()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        instance = this
        CallManager.setCall(call, applicationContext)

        showCallNotification(call)

        // Launch in-call full screen Activity
        val intent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.clearCall()
        stopForeground(STOP_FOREGROUND_REMOVE)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
        if (instance == this) {
            instance = null
        }
    }

    private fun showCallNotification(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: "Phone Call"
        val callerName = call.details?.callerDisplayName ?: CallManager.callState.value.callerName ?: number

        val activityIntent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingActivityIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val hangupIntent = Intent(this, DialerInCallService::class.java).apply {
            action = ACTION_HANGUP
        }
        val pendingHangupIntent = PendingIntent.getService(
            this, 1, hangupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle(callerName)
            .setContentText("Ongoing Call • Tap to return")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(pendingActivityIntent)
            .addAction(android.R.drawable.ic_menu_call, "Return to Call", pendingActivityIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "End Call", pendingHangupIntent)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Ongoing Calls",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Shows active and background ongoing call status"
                setSound(null, null)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }
}
