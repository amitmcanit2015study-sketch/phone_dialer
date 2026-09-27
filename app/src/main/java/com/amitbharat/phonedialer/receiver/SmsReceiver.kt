package com.amitbharat.phonedialer.receiver

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import com.amitbharat.phonedialer.repository.SmsRepository
import com.amitbharat.phonedialer.utils.PreferencesManager
import com.amitbharat.phonedialer.utils.SmsNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

open class SmsReceiver : BroadcastReceiver() {

    companion object {
        private val recentlyProcessed = ConcurrentHashMap<String, Long>()

        fun isDuplicate(sender: String, timestamp: Long, body: String): Boolean {
            val key = "$sender:$timestamp:${body.take(20)}"
            val now = System.currentTimeMillis()
            val iterator = recentlyProcessed.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (now - entry.value > 30000) {
                    iterator.remove()
                }
            }
            if (recentlyProcessed.containsKey(key)) {
                return true
            }
            recentlyProcessed[key] = now
            return false
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Telephony.Sms.Intents.SMS_DELIVER_ACTION &&
            action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        val messages: Array<SmsMessage> = try {
            Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        } catch (e: Exception) {
            return
        }

        if (messages.isEmpty()) return

        val grouped = messages.groupBy { it.displayOriginatingAddress ?: it.originatingAddress ?: "Unknown" }

        for ((sender, partList) in grouped) {
            val fullBody = partList.joinToString("") { it.displayMessageBody ?: it.messageBody ?: "" }
            val timestamp = partList.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()

            if (isDuplicate(sender, timestamp, fullBody)) {
                continue
            }

            val isDefaultSms = Telephony.Sms.getDefaultSmsPackage(context) == context.packageName

            // When default SMS app or SMS_DELIVER action: write message to inbox provider
            if (isDefaultSms || action == Telephony.Sms.Intents.SMS_DELIVER_ACTION) {
                try {
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, sender)
                        put(Telephony.Sms.BODY, fullBody)
                        put(Telephony.Sms.DATE, timestamp)
                        put(Telephony.Sms.READ, 0)
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    }
                    context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Notify SmsRepository cache
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    SmsRepository.getInstance(context).notifyNewIncomingMessage(sender, fullBody, timestamp)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    try {
                        pendingResult.finish()
                    } catch (e: Exception) {}
                }
            }

            // Show notification with ringtone sound and vibration!
            val prefs = PreferencesManager.getInstance(context)
            if (prefs.isSmsNotificationsEnabled()) {
                SmsNotificationHelper.showSmsNotification(
                    context = context,
                    sender = sender,
                    body = fullBody,
                    timestamp = timestamp
                )
            }
        }
    }
}
