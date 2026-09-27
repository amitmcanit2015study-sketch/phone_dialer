package com.amitbharat.phonedialer.receiver

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.app.RemoteInput
import com.amitbharat.phonedialer.repository.SmsRepository
import com.amitbharat.phonedialer.ui.messages.SmsMessageItem
import com.amitbharat.phonedialer.utils.SmsNotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val address = intent.getStringExtra(SmsNotificationHelper.EXTRA_ADDRESS) ?: return
        val notifId = intent.getIntExtra(SmsNotificationHelper.EXTRA_NOTIF_ID, address.hashCode() and 0x7FFFFFFF)
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        when (action) {
            SmsNotificationHelper.ACTION_MARK_READ -> {
                nm.cancel(notifId)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val repo = SmsRepository.getInstance(context)
                        repo.markThreadAsRead(address)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        try {
                            pendingResult.finish()
                        } catch (e: Exception) {}
                    }
                }
            }

            SmsNotificationHelper.ACTION_QUICK_REPLY -> {
                val remoteInput = RemoteInput.getResultsFromIntent(intent)
                val replyText = remoteInput?.getCharSequence(SmsNotificationHelper.KEY_TEXT_REPLY)?.toString()
                if (!replyText.isNullOrBlank()) {
                    nm.cancel(notifId)
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                context.getSystemService(SmsManager::class.java)
                            } else {
                                @Suppress("DEPRECATION") SmsManager.getDefault()
                            }
                            smsManager?.sendTextMessage(address, null, replyText, null, null)

                            val now = System.currentTimeMillis()
                            val values = ContentValues().apply {
                                put(Telephony.Sms.ADDRESS, address)
                                put(Telephony.Sms.BODY, replyText)
                                put(Telephony.Sms.DATE, now)
                                put(Telephony.Sms.READ, 1)
                                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                            }
                            try {
                                context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }

                            val repo = SmsRepository.getInstance(context)
                            repo.addSentMessage(
                                SmsMessageItem(
                                    id = now,
                                    address = address,
                                    body = replyText,
                                    timestamp = now,
                                    isOutgoing = true,
                                    isRead = true
                                )
                            )
                            repo.updateThreadOptimistic(address, replyText, null)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            try {
                                pendingResult.finish()
                            } catch (e: Exception) {}
                        }
                    }
                }
            }
        }
    }
}
