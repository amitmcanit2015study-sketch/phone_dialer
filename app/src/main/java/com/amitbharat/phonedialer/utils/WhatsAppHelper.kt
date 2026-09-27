package com.amitbharat.phonedialer.utils

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

object WhatsAppHelper {

    // Set of contact IDs registered in WhatsApp
    private val whatsAppContactIds = ConcurrentHashMap.newKeySet<Long>()

    // Set of normalized phone numbers registered in WhatsApp
    private val whatsAppNumbers = ConcurrentHashMap.newKeySet<String>()

    // Compose-observable state to trigger recomposition when contacts are loaded
    var updateVersion by mutableIntStateOf(0)
        private set

    @Volatile
    private var isInitialized = false

    private fun addNumberVariants(raw: String?, set: MutableSet<String>) {
        if (raw.isNullOrBlank()) return
        val digits = raw.replace(Regex("[^0-9]"), "")
        if (digits.isBlank()) return
        set.add(digits)
        if (digits.length >= 10) {
            set.add(digits.takeLast(10))
        }
        if (digits.startsWith("91") && digits.length == 12) {
            set.add(digits.substring(2))
        }
    }

    private fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val digits = raw.replace(Regex("[^0-9]"), "")
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    suspend fun refreshWhatsAppContacts(context: Context) = withContext(Dispatchers.IO) {
        try {
            val resolver = context.contentResolver
            val tempIds = HashSet<Long>()
            val tempNumbers = HashSet<String>()

            // 1. Query RawContacts for com.whatsapp and com.whatsapp.w4b
            try {
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

                    while (it.moveToNext()) {
                        if (contactIdIdx >= 0) {
                            val cid = it.getLong(contactIdIdx)
                            if (cid > 0) tempIds.add(cid)
                        }
                        if (sync1Idx >= 0) {
                            val sync1 = it.getString(sync1Idx) ?: ""
                            val numPart = sync1.substringBefore("@")
                            addNumberVariants(numPart, tempNumbers)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Query ContactsContract.Data for WhatsApp profile mimetype
            try {
                val dataCursor: Cursor? = resolver.query(
                    ContactsContract.Data.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Data.CONTACT_ID,
                        ContactsContract.Data.DATA1,
                        ContactsContract.Data.DATA3
                    ),
                    "${ContactsContract.Data.MIMETYPE} = ?",
                    arrayOf("vnd.android.cursor.item/vnd.com.whatsapp.profile"),
                    null
                )
                dataCursor?.use {
                    val contactIdIdx = it.getColumnIndex(ContactsContract.Data.CONTACT_ID)
                    val data1Idx = it.getColumnIndex(ContactsContract.Data.DATA1)
                    val data3Idx = it.getColumnIndex(ContactsContract.Data.DATA3)

                    while (it.moveToNext()) {
                        if (contactIdIdx >= 0) {
                            val cid = it.getLong(contactIdIdx)
                            if (cid > 0) tempIds.add(cid)
                        }
                        if (data1Idx >= 0) {
                            val data1 = it.getString(data1Idx) ?: ""
                            val numPart = data1.substringBefore("@")
                            addNumberVariants(numPart, tempNumbers)
                        }
                        if (data3Idx >= 0) {
                            val data3 = it.getString(data3Idx) ?: ""
                            addNumberVariants(data3, tempNumbers)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            whatsAppContactIds.clear()
            whatsAppContactIds.addAll(tempIds)

            whatsAppNumbers.clear()
            whatsAppNumbers.addAll(tempNumbers)
            isInitialized = true

            withContext(Dispatchers.Main) {
                updateVersion++
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun isWhatsAppLinked(contactId: Long? = null, number: String? = null): Boolean {
        // Read observable property so caller Composable recomposes when loaded
        @Suppress("UNUSED_VARIABLE")
        val v = updateVersion

        if (contactId != null && contactId > 0 && whatsAppContactIds.contains(contactId)) {
            return true
        }
        if (!number.isNullOrBlank()) {
            val digits = number.replace(Regex("[^0-9]"), "")
            if (digits.isNotBlank()) {
                if (whatsAppNumbers.contains(digits)) return true
                if (digits.length >= 10 && whatsAppNumbers.contains(digits.takeLast(10))) return true
                if (digits.startsWith("91") && digits.length == 12 && whatsAppNumbers.contains(digits.substring(2))) return true
            }
        }
        return false
    }

    fun isWhatsAppLinked(contactId: Long?, numbers: List<String>): Boolean {
        // Read observable property so caller Composable recomposes when loaded
        @Suppress("UNUSED_VARIABLE")
        val v = updateVersion

        if (contactId != null && contactId > 0 && whatsAppContactIds.contains(contactId)) {
            return true
        }
        for (num in numbers) {
            val digits = num.replace(Regex("[^0-9]"), "")
            if (digits.isNotBlank()) {
                if (whatsAppNumbers.contains(digits)) return true
                if (digits.length >= 10 && whatsAppNumbers.contains(digits.takeLast(10))) return true
                if (digits.startsWith("91") && digits.length == 12 && whatsAppNumbers.contains(digits.substring(2))) return true
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
