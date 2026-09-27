package com.amitbharat.phonedialer.receiver

/**
 * Fallback BroadcastReceiver registered for android.provider.Telephony.SMS_RECEIVED.
 * This runs when the app is not the default SMS app or as a system fallback.
 */
class SmsReceiverFallback : SmsReceiver()
