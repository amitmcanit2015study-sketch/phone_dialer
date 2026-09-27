package com.amitbharat.phonedialer.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.amitbharat.phonedialer.R
import com.amitbharat.phonedialer.repository.CallLogRepository
import com.amitbharat.phonedialer.repository.ContactsRepository
import com.amitbharat.phonedialer.ui.main.MainActivity
import com.amitbharat.phonedialer.utils.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Persistent Background Service that keeps the Phone Dialer process alive in RAM.
 * Prevents OS process termination and enables instant 0-second launch speed.
 */
class DialerKeepAliveService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val CHANNEL_ID = "channel_dialer_keep_alive"
        const val NOTIFICATION_ID = 9001
        const val ACTION_STOP = "com.amitbharat.phonedialer.action.STOP_KEEP_ALIVE"

        fun startService(context: Context) {
            try {
                val intent = Intent(context, DialerKeepAliveService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun stopService(context: Context) {
            try {
                val intent = Intent(context, DialerKeepAliveService::class.java)
                context.stopService(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        fun createNotificationChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                val existing = nm.getNotificationChannel(CHANNEL_ID)
                if (existing == null) {
                    val channel = NotificationChannel(
                        CHANNEL_ID,
                        "Background Service (Instant Launch)",
                        NotificationManager.IMPORTANCE_MIN
                    ).apply {
                        description = "Keeps Phone Dialer resident in memory for instant opening and background readiness"
                        setShowBadge(false)
                        enableLights(false)
                        enableVibration(false)
                        setSound(null, null)
                    }
                    nm.createNotificationChannel(channel)
                }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel(this)
        prewarmCaches()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = buildForegroundNotification()
        startForeground(NOTIFICATION_ID, notification)

        return START_STICKY
    }

    private fun prewarmCaches() {
        serviceScope.launch {
            try {
                val contactsRepo = ContactsRepository(this@DialerKeepAliveService)
                val callLogRepo = CallLogRepository(this@DialerKeepAliveService)
                contactsRepo.syncDeviceContacts()
                callLogRepo.syncDeviceCallLogs()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun buildForegroundNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingOpenIntent = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_retro_phone)
            .setContentTitle("Phone Dialer")
            .setContentText("Active in background for instant opening")
            .setSubText("Developed by Amit Bharat")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOngoing(true)
            .setContentIntent(pendingOpenIntent)
            .build()
    }
}
