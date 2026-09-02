package com.amitbharat.phonedialer.telecom

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

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
        val uri = Uri.fromParts("tel", number, null)
        val intent = Intent(Intent.ACTION_CALL, uri).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // Fallback to DIAL intent
            val dialIntent = Intent(Intent.ACTION_DIAL, uri).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(dialIntent)
        }
    }
}
