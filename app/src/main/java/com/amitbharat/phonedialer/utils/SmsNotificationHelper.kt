package com.amitbharat.phonedialer.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.amitbharat.phonedialer.receiver.SmsActionReceiver
import com.amitbharat.phonedialer.repository.ContactsRepository
import com.amitbharat.phonedialer.repository.SmsRepository
import com.amitbharat.phonedialer.ui.main.MainActivity

object SmsNotificationHelper {

    const val CHANNEL_ID = "sms_notifications_channel"
    const val KEY_TEXT_REPLY = "key_text_reply"
    const val ACTION_MARK_READ = "com.amitbharat.phonedialer.ACTION_MARK_SMS_READ"
    const val ACTION_QUICK_REPLY = "com.amitbharat.phonedialer.ACTION_QUICK_SMS_REPLY"
    const val EXTRA_ADDRESS = "extra_address"
    const val EXTRA_NOTIF_ID = "extra_notif_id"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Incoming message alerts, sound, and vibration"
                enableLights(true)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 150, 250)
                setSound(soundUri, audioAttributes)
            }

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    fun showSmsNotification(
        context: Context,
        sender: String,
        body: String,
        timestamp: Long
    ) {
        createNotificationChannel(context)
        val prefs = PreferencesManager.getInstance(context)
        if (!prefs.isSmsNotificationsEnabled()) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Resolve contact name if available
        val contactName = SmsRepository.getInstance(context).resolveContactName(
            sender,
            ContactsRepository(context).getCachedContacts()
        ) ?: sender

        val notifId = (sender.hashCode() and 0x7FFFFFFF)

        // Content intent: open chat directly in MainActivity
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_OPEN_TAB, "MESSAGES")
            putExtra(MainActivity.EXTRA_CHAT_ADDRESS, sender)
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Mark as read action
        val markReadIntent = Intent(context, SmsActionReceiver::class.java).apply {
            action = ACTION_MARK_READ
            putExtra(EXTRA_ADDRESS, sender)
            putExtra(EXTRA_NOTIF_ID, notifId)
        }
        val pendingMarkRead = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            markReadIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Quick Reply action
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply")
            .build()

        val replyIntent = Intent(context, SmsActionReceiver::class.java).apply {
            action = ACTION_QUICK_REPLY
            putExtra(EXTRA_ADDRESS, sender)
            putExtra(EXTRA_NOTIF_ID, notifId)
        }
        val pendingReply = PendingIntent.getBroadcast(
            context,
            notifId + 2,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
        )

        val replyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_send,
            "Reply",
            pendingReply
        ).addRemoteInput(remoteInput).build()

        val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val largeIconBitmap = try {
            android.graphics.BitmapFactory.decodeResource(context.resources, com.amitbharat.phonedialer.R.drawable.ic_retro_phone)
        } catch (e: Exception) {
            null
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.amitbharat.phonedialer.R.drawable.ic_notification_phone_retro)
            .setLargeIcon(largeIconBitmap)
            .setContentTitle(contactName)
            .setContentText(body)
            .setSubText("Developed by Amit Bharat")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingOpenIntent)
            .addAction(android.R.drawable.checkbox_on_background, "Mark as Read", pendingMarkRead)
            .addAction(replyAction)

        if (prefs.isSmsSoundEnabled()) {
            builder.setSound(soundUri)
        } else {
            builder.setSound(null)
        }

        if (prefs.isSmsVibrationEnabled()) {
            builder.setVibrate(longArrayOf(0, 250, 150, 250))
        } else {
            builder.setVibrate(longArrayOf(0))
        }

        nm.notify(notifId, builder.build())
    }
}
