package com.amitbharat.phonedialer.utils

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object WhatsAppHelper {

    // Set of contact IDs registered in WhatsApp
    private val whatsAppContactIds = ConcurrentHashMap.newKeySet<Long>()

    // Set of normalized 10-digit phone numbers registered in WhatsApp
    private val whatsAppNumbers = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var isInitialized = false

    private fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val digits = raw.replace(Regex("[^0-9]"), "")
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    suspend fun refreshWhatsAppContacts(context: Context) = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val cursor: Cursor? = resolver.query(
                ContactsContract.RawContacts.CONTENT_URI,
                arrayOf(
                    ContactsContract.RawContacts.CONTACT_ID,
                    ContactsContract.RawContacts.SYNC1,
                    ContactsContract.RawContacts.ACCOUNT_TYPE
                ),
                "${ContactsContract.RawContacts.ACCOUNT_TYPE} IN (?, ?)",
                arrayOf("com.whatsapp", "com.whatsapp.w4b"),
                null
            )

            cursor?.use {
                val contactIdIdx = it.getColumnIndex(ContactsContract.RawContacts.CONTACT_ID)
                val sync1Idx = it.getColumnIndex(ContactsContract.RawContacts.SYNC1)

                val tempIds = HashSet<Long>()
                val tempNumbers = HashSet<String>()

                while (it.moveToNext()) {
                    if (contactIdIdx >= 0) {
                        val cid = it.getLong(contactIdIdx)
                        if (cid > 0) tempIds.add(cid)
                    }
                    if (sync1Idx >= 0) {
                        val sync1 = it.getString(sync1Idx) ?: ""
                        val numPart = sync1.substringBefore("@")
                        val norm = normalize(numPart)
                        if (norm.isNotBlank()) {
                            tempNumbers.add(norm)
                        }
                    }
                }

                whatsAppContactIds.clear()
                whatsAppContactIds.addAll(tempIds)

                whatsAppNumbers.clear()
                whatsAppNumbers.addAll(tempNumbers)
                isInitialized = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isWhatsAppLinked(contactId: Long? = null, number: String? = null): Boolean {
        if (contactId != null && contactId > 0 && whatsAppContactIds.contains(contactId)) {
            return true
        }
        if (!number.isNullOrBlank()) {
            val norm = normalize(number)
            if (norm.isNotBlank() && whatsAppNumbers.contains(norm)) {
                return true
            }
        }
        return false
    }

    fun isWhatsAppLinked(contactId: Long?, numbers: List<String>): Boolean {
        if (contactId != null && contactId > 0 && whatsAppContactIds.contains(contactId)) {
            return true
        }
        for (num in numbers) {
            val norm = normalize(num)
            if (norm.isNotBlank() && whatsAppNumbers.contains(norm)) {
                return true
            }
        }
        return false
    }

    fun openWhatsAppChat(context: Context, number: String) {
        val digits = number.replace(Regex("[^0-9]"), "")
        if (digits.isBlank()) {
            Toast.makeText(context, "Invalid phone number", Toast.LENGTH_SHORT).show()
            return
        }
        // If 10 digits without country code, default to India (+91)
        val fullNumber = if (digits.length == 10) "91$digits" else digits

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("https://api.whatsapp.com/send?phone=$fullNumber")
                `package` = "com.whatsapp"
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                // Fallback to general intent (allows WhatsApp Business or browser if regular WhatsApp not installed)
                val fallbackIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$fullNumber")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(fallbackIntent)
            } catch (ex: Exception) {
                Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
