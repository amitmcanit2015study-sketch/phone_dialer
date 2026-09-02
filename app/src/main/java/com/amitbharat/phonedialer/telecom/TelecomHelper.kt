package com.amitbharat.phonedialer.telecom

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.TelecomManager
import com.amitbharat.phonedialer.ui.incall.InCallActivity

object TelecomHelper {

    fun isDefaultDialer(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as? RoleManager
            roleManager?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        } else {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            telecomManager?.defaultDialerPackage == context.packageName
        }
    }

    fun makeCall(context: Context, number: String, simSlot: Int = 0) {
        val cleanNumber = number.replace("[^0-9+]".toRegex(), "")
        if (cleanNumber.isBlank()) return
        val uri = Uri.fromParts("tel", cleanNumber, null)
        val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager

        try {
            // Direct TelecomManager placement without system app chooser popup
            val extras = Bundle().apply {
                putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
                putBoolean(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, false)
            }
            telecomManager?.placeCall(uri, extras)
        } catch (e: SecurityException) {
            // Fallback to direct ACTION_CALL intent
            try {
                val callIntent = Intent(Intent.ACTION_CALL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(callIntent)
            } catch (err: Exception) {
                val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(dialIntent)
            }
        }
    }
}
