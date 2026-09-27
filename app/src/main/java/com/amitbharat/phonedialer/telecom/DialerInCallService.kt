package com.amitbharat.phonedialer.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.telecom.Call
import android.telecom.InCallService
import android.telecom.TelecomManager
import androidx.core.app.NotificationCompat
import com.amitbharat.phonedialer.R
import com.amitbharat.phonedialer.ui.incall.InCallActivity
import com.amitbharat.phonedialer.utils.PreferencesManager

class DialerInCallService : InCallService(), SensorEventListener {

    companion object {
        var instance: DialerInCallService? = null
        const val INCOMING_CHANNEL_ID = "incoming_call_channel"
        const val ONGOING_CHANNEL_ID = "ongoing_call_channel"
        private const val NOTIFICATION_ID = 1001

        const val ACTION_HANGUP = "com.amitbharat.phonedialer.ACTION_HANGUP"
        const val ACTION_REJECT = "com.amitbharat.phonedialer.ACTION_REJECT"
        const val ACTION_ANSWER = "com.amitbharat.phonedialer.ACTION_ANSWER"

        fun createNotificationChannels(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

                // Channel for Incoming Calls: HIGH importance so Heads-Up alert is shown
                val incomingChannel = NotificationChannel(
                    INCOMING_CHANNEL_ID,
                    "Incoming Calls",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alerts and full screen displays for incoming calls"
                    setSound(null, null) // System Telecom framework handles the ringtone
                    enableVibration(true)
                }

                // Channel for Ongoing / Active / Dialing Calls: DEFAULT importance so notification is active and visible
                val ongoingChannel = NotificationChannel(
                    ONGOING_CHANNEL_ID,
                    "Ongoing Calls",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Status for active, dialing, and in-progress phone calls"
                    setSound(null, null)
                    enableVibration(false)
                }

                nm.createNotificationChannel(incomingChannel)
                nm.createNotificationChannel(ongoingChannel)
            }
        }
    }

    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private var isSensorRegistered = false

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            showCallNotification(call)
            if (state != Call.STATE_RINGING) {
                unregisterFlipSensor()
            }
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            super.onDetailsChanged(call, details)
            showCallNotification(call)
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels(this)
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_ANSWER -> {
                CallManager.answerCall()
                val activityIntent = Intent(this, InCallActivity::class.java).apply {
                    this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                }
                startActivity(activityIntent)
            }
            ACTION_REJECT -> {
                CallManager.rejectCall()
            }
            ACTION_HANGUP -> {
                CallManager.disconnectCall()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        instance = this
        CallManager.setCall(call, applicationContext)

        call.registerCallback(callCallback)
        showCallNotification(call)

        // If incoming call, launch InCallActivity and register flip-to-silence if enabled
        if (call.state == Call.STATE_RINGING) {
            registerFlipSensorIfEnabled()
        }

        try {
            val intent = Intent(this, InCallActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        unregisterFlipSensor()
        CallManager.clearCall()
        stopForeground(STOP_FOREGROUND_REMOVE)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIFICATION_ID)
        if (instance == this) {
            instance = null
        }
    }

    override fun onSilenceRinger() {
        super.onSilenceRinger()
        unregisterFlipSensor()
    }

    private fun showCallNotification(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: "Phone Call"
        val callerName = call.details?.callerDisplayName ?: CallManager.callState.value.callerName ?: number
        val isIncoming = call.state == Call.STATE_RINGING
        val isDialing = call.state == Call.STATE_DIALING || call.state == Call.STATE_CONNECTING
        val isHolding = call.state == Call.STATE_HOLDING

        val largeIconBitmap = try {
            BitmapFactory.decodeResource(resources, R.drawable.ic_retro_phone)
        } catch (e: Exception) {
            null
        }

        val activityIntent = Intent(this, InCallActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingActivityIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (isIncoming) {
            // Incoming Call Notification (Getting call)
            val rejectIntent = Intent(this, DialerInCallService::class.java).apply {
                action = ACTION_REJECT
            }
            val pendingRejectIntent = PendingIntent.getService(
                this, 1, rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val answerIntent = Intent(this, DialerInCallService::class.java).apply {
                action = ACTION_ANSWER
            }
            val pendingAnswerIntent = PendingIntent.getService(
                this, 2, answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, INCOMING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_phone_retro)
                .setLargeIcon(largeIconBitmap)
                .setContentTitle(callerName)
                .setContentText("Incoming call...")
                .setSubText("Developed by Amit Bharat")
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingActivityIntent, true) // Required to wake screen and show UI on lockscreen
                .setContentIntent(pendingActivityIntent)
                .addAction(R.drawable.ic_call_end, "Decline", pendingRejectIntent)
                .addAction(R.drawable.ic_call, "Answer", pendingAnswerIntent)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        } else {
            // Outgoing / Active / Ongoing Call Notification (Making call, In-call, On hold)
            val hangupIntent = Intent(this, DialerInCallService::class.java).apply {
                action = ACTION_HANGUP
            }
            val pendingHangupIntent = PendingIntent.getService(
                this, 3, hangupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val statusText = when {
                isDialing -> "Calling..."
                isHolding -> "Call on hold"
                else -> "Ongoing Call • Tap to return"
            }

            val notification = NotificationCompat.Builder(this, ONGOING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_phone_retro)
                .setLargeIcon(largeIconBitmap)
                .setContentTitle(callerName)
                .setContentText(statusText)
                .setSubText("Developed by Amit Bharat")
                .setOngoing(true)
                .setPriority(if (isDialing) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(pendingActivityIntent)
                .addAction(R.drawable.ic_call, "Return to Call", pendingActivityIntent)
                .addAction(R.drawable.ic_call_end, "End Call", pendingHangupIntent)
                .build()

            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun registerFlipSensorIfEnabled() {
        val prefs = PreferencesManager.getInstance(this)
        if (prefs.isFlipToSilenceEnabled() && accelerometer != null && !isSensorRegistered) {
            sensorManager?.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL)
            isSensorRegistered = true
        }
    }

    private fun unregisterFlipSensor() {
        if (isSensorRegistered) {
            sensorManager?.unregisterListener(this)
            isSensorRegistered = false
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val z = event.values[2]
            // If phone is flipped face down (negative gravity along z axis)
            if (z < -7.0f) {
                try {
                    val tm = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
                    tm?.silenceRinger()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                unregisterFlipSensor()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        super.onDestroy()
        unregisterFlipSensor()
        if (instance == this) {
            instance = null
        }
    }
}
