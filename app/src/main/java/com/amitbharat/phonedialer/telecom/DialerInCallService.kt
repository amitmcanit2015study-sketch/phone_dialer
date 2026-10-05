package com.amitbharat.phonedialer.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Build
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat
import com.amitbharat.phonedialer.R
import com.amitbharat.phonedialer.receiver.CallNotificationReceiver
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
        const val ACTION_SPEAKER = "com.amitbharat.phonedialer.ACTION_SPEAKER"
        const val ACTION_MUTE = "com.amitbharat.phonedialer.ACTION_MUTE"

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
            ACTION_SPEAKER -> {
                val current = CallManager.callState.value.isSpeakerOn
                CallManager.setSpeakerphoneOn(!current)
                updateOngoingNotification()
            }
            ACTION_MUTE -> {
                val current = CallManager.callState.value.isMuted
                CallManager.setMuted(!current)
                updateOngoingNotification()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        super.onCallAudioStateChanged(audioState)
        audioState?.let {
            val isMuted = it.isMuted
            val isSpeaker = it.route == CallAudioState.ROUTE_SPEAKER
            CallManager.updateAudioState(isMuted, isSpeaker)
            updateOngoingNotification()
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        instance = this
        CallManager.addCall(call, applicationContext)

        call.registerCallback(callCallback)
        showCallNotification(call)

        // If incoming call, launch InCallActivity and register flip-to-silence if enabled
        if (call.state == Call.STATE_RINGING) {
            registerFlipSensorIfEnabled()
        }

        try {
            val intent = Intent(this, InCallActivity::class.java).apply {
                this.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        CallManager.removeCall(call)

        if (!CallManager.hasCalls()) {
            unregisterFlipSensor()
            stopForeground(STOP_FOREGROUND_REMOVE)
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(NOTIFICATION_ID)
            if (instance == this) {
                instance = null
            }
        } else {
            CallManager.primaryCall?.let { showCallNotification(it) }
        }
    }

    override fun onSilenceRinger() {
        super.onSilenceRinger()
        unregisterFlipSensor()
    }

    private fun getCircularBitmap(bitmap: Bitmap): Bitmap {
        val size = minOf(bitmap.width, bitmap.height)
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply {
            isAntiAlias = true
            isFilterBitmap = true
        }
        val srcRect = Rect((bitmap.width - size) / 2, (bitmap.height - size) / 2, (bitmap.width + size) / 2, (bitmap.height + size) / 2)
        val dstRect = Rect(0, 0, size, size)
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
        canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
        return output
    }

    private fun createLetterAvatar(letter: String): Bitmap {
        val size = 160
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.parseColor("#4A6572")
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, bgPaint)
        val textPaint = Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.WHITE
            textSize = 68f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val yPos = (size / 2 - (textPaint.descent() + textPaint.ascent()) / 2)
        canvas.drawText(letter.uppercase(), size / 2f, yPos, textPaint)
        return bitmap
    }

    private fun loadAvatarBitmap(callerName: String, photoUriStr: String?): Bitmap {
        if (!photoUriStr.isNullOrBlank()) {
            try {
                val uri = Uri.parse(photoUriStr)
                contentResolver.openInputStream(uri)?.use { stream ->
                    val original = BitmapFactory.decodeStream(stream)
                    if (original != null) {
                        return getCircularBitmap(original)
                    }
                }
            } catch (e: Exception) {
                // fall through to initial avatar
            }
        }
        val initial = callerName.trim().take(1).ifBlank { "P" }
        return try {
            createLetterAvatar(initial)
        } catch (e: Exception) {
            BitmapFactory.decodeResource(resources, R.drawable.ic_retro_phone)
        }
    }

    private fun getCarrierOrSimName(call: Call): String {
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            val handle = call.details?.accountHandle
            if (handle != null && telecomManager != null) {
                val phoneAccount = telecomManager.getPhoneAccount(handle)
                val label = phoneAccount?.label?.toString()
                if (!label.isNullOrBlank()) return label
                val desc = phoneAccount?.shortDescription?.toString()
                if (!desc.isNullOrBlank()) return desc
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                val sm = getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
                val activeList = sm?.activeSubscriptionInfoList
                if (!activeList.isNullOrEmpty()) {
                    val displayName = activeList[0].displayName?.toString()
                    if (!displayName.isNullOrBlank()) return displayName
                    val carrier = activeList[0].carrierName?.toString()
                    if (!carrier.isNullOrBlank()) return carrier
                }
            }
            val tm = getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
            val simName = tm?.simOperatorName
            if (!simName.isNullOrBlank()) return simName
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "SIM"
    }

    fun updateOngoingNotification() {
        val call = CallManager.primaryCall ?: return
        showCallNotification(call)
    }

    fun showCallNotification(call: Call) {
        val number = call.details?.handle?.schemeSpecificPart ?: "Phone Call"
        val callerName = call.details?.callerDisplayName ?: CallManager.callState.value.callerName ?: number
        val isIncoming = call.state == Call.STATE_RINGING
        val isDialing = call.state == Call.STATE_DIALING || call.state == Call.STATE_CONNECTING
        val isHolding = call.state == Call.STATE_HOLDING
        val isActive = call.state == Call.STATE_ACTIVE
        val carrierName = getCarrierOrSimName(call)

        val avatarBitmap = loadAvatarBitmap(callerName, CallManager.callState.value.photoUri)

        // Target InCallActivity with flags to bring any existing instance immediately to front
        val activityIntent = Intent(this, InCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }

        val pendingActivityIntent = PendingIntent.getActivity(
            this, 0, activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        if (isIncoming) {
            // Incoming Call Notification (Getting call)
            val rejectIntent = Intent(this, CallNotificationReceiver::class.java).apply {
                action = ACTION_REJECT
            }
            val pendingRejectIntent = PendingIntent.getBroadcast(
                this, 1, rejectIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val answerIntent = Intent(this, InCallActivity::class.java).apply {
                action = ACTION_ANSWER
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
            }
            val pendingAnswerIntent = PendingIntent.getActivity(
                this, 2, answerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val pendingFullScreenIntent = PendingIntent.getActivity(
                this, 10, activityIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notification = NotificationCompat.Builder(this, INCOMING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_phone_retro)
                .setLargeIcon(avatarBitmap)
                .setContentTitle("$callerName • Phone")
                .setContentText("Incoming call via $carrierName")
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setFullScreenIntent(pendingFullScreenIntent, true)
                .setContentIntent(pendingActivityIntent)
                .addAction(R.drawable.ic_call_end, "Decline", pendingRejectIntent)
                .addAction(R.drawable.ic_call, "Answer", pendingAnswerIntent)
                .build()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.notify(NOTIFICATION_ID, notification)
            }
        } else {
            // Outgoing / Active / Ongoing Call Notification (Matches user's screenshot exactly!)
            val hangupIntent = Intent(this, CallNotificationReceiver::class.java).apply {
                action = ACTION_HANGUP
            }
            val pendingHangupIntent = PendingIntent.getBroadcast(
                this, 3, hangupIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val speakerIntent = Intent(this, CallNotificationReceiver::class.java).apply {
                action = ACTION_SPEAKER
            }
            val pendingSpeakerIntent = PendingIntent.getBroadcast(
                this, 5, speakerIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val muteIntent = Intent(this, CallNotificationReceiver::class.java).apply {
                action = ACTION_MUTE
            }
            val pendingMuteIntent = PendingIntent.getBroadcast(
                this, 6, muteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val statusText = when {
                isHolding -> "Call on hold via $carrierName"
                isDialing -> "Calling via $carrierName"
                else -> "Ongoing call via $carrierName"
            }

            val connectTime = call.details?.connectTimeMillis ?: 0L
            val startTime = if (connectTime > 0L) {
                connectTime
            } else {
                System.currentTimeMillis() - (CallManager.callState.value.callDurationSeconds * 1000L)
            }

            val isSpeakerOn = CallManager.callState.value.isSpeakerOn
            val isMuted = CallManager.callState.value.isMuted

            val notification = NotificationCompat.Builder(this, ONGOING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_phone_retro)
                .setLargeIcon(avatarBitmap)
                .setContentTitle("$callerName • Phone")
                .setContentText(statusText)
                .setUsesChronometer(isActive)
                .setWhen(startTime)
                .setShowWhen(isActive)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setContentIntent(pendingActivityIntent)
                .addAction(R.drawable.ic_call_end, "Hang up", pendingHangupIntent)
                .addAction(R.drawable.ic_speaker, if (isSpeakerOn) "Speaker on" else "Speaker", pendingSpeakerIntent)
                .addAction(R.drawable.ic_mic, if (isMuted) "Muted" else "Mute", pendingMuteIntent)
                .build()

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                val nm = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                nm?.notify(NOTIFICATION_ID, notification)
            }
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
