package com.amitbharat.phonedialer.repository

import android.content.Context
import android.database.Cursor
import android.provider.Telephony
import com.amitbharat.phonedialer.model.Contact
import com.amitbharat.phonedialer.ui.messages.MessageThread
import com.amitbharat.phonedialer.ui.messages.SmsMessageItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

class SmsRepository(private val context: Context) {

    companion object {
        @Volatile
        private var instance: SmsRepository? = null

        fun getInstance(context: Context): SmsRepository {
            return instance ?: synchronized(this) {
                instance ?: SmsRepository(context.applicationContext).also { instance = it }
            }
        }
    }

    private val _threads = MutableStateFlow<List<MessageThread>>(emptyList())
    val threads: StateFlow<List<MessageThread>> = _threads.asStateFlow()

    @Volatile
    private var isLoaded = false

    fun getCachedThreads(): List<MessageThread> = _threads.value

    fun normalizeNumber(raw: String): String {
        val digits = raw.replace(Regex("[^0-9]"), "")
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    suspend fun loadThreads(contacts: List<Contact>, forceRefresh: Boolean = false) = withContext(Dispatchers.IO) {
        if (!forceRefresh && isLoaded && _threads.value.isNotEmpty()) {
            return@withContext
        }

        val threadMap = LinkedHashMap<String, MutableList<SmsMessageItem>>()
        try {
            val cursor: Cursor? = context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("_id", "address", "body", "date", "type", "read", "status"),
                null,
                null,
                "date DESC"
            )
            cursor?.use {
                val idIdx = it.getColumnIndex("_id")
                val addrIdx = it.getColumnIndex("address")
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")
                val typeIdx = it.getColumnIndex("type")
                val readIdx = it.getColumnIndex("read")
                val statusIdx = it.getColumnIndex("status")

                while (it.moveToNext()) {
                    val addr = if (addrIdx >= 0) it.getString(addrIdx) ?: "" else ""
                    val norm = normalizeNumber(addr)
                    if (norm.isNotBlank()) {
                        val id = if (idIdx >= 0) it.getLong(idIdx) else 0L
                        val body = if (bodyIdx >= 0) it.getString(bodyIdx) ?: "" else ""
                        val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
                        val type = if (typeIdx >= 0) it.getInt(typeIdx) else Telephony.Sms.MESSAGE_TYPE_INBOX
                        val read = if (readIdx >= 0) it.getInt(readIdx) == 1 else true
                        val status = if (statusIdx >= 0) it.getInt(statusIdx) else -1

                        val isOut = type == Telephony.Sms.MESSAGE_TYPE_SENT || type == Telephony.Sms.MESSAGE_TYPE_OUTBOX
                        val isDelivered = status == 0 // STATUS_COMPLETE

                        threadMap.getOrPut(norm) { mutableListOf() }.add(
                            SmsMessageItem(
                                id = id,
                                address = addr,
                                body = body,
                                timestamp = date,
                                isOutgoing = isOut,
                                status = status,
                                isDelivered = isDelivered
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val list = threadMap.map { (norm, items) ->
            val latest = items.first()
            val matchingContact = contacts.find { c ->
                c.numbers.any { num -> normalizeNumber(num) == norm }
            }

            MessageThread(
                normalizedNumber = norm,
                displayAddress = latest.address,
                contactName = matchingContact?.name,
                latestBody = latest.body,
                latestTimestamp = latest.timestamp,
                unreadCount = items.count { !it.isOutgoing },
                threadIds = emptyList(),
                isOutgoing = latest.isOutgoing,
                isDelivered = latest.isDelivered
            )
        }.sortedByDescending { it.latestTimestamp }

        _threads.value = list
        isLoaded = true
    }

    fun updateThreadOptimistic(address: String, body: String, contactName: String?) {
        val norm = normalizeNumber(address)
        val current = _threads.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.normalizedNumber == norm }
        val updatedThread = MessageThread(
            normalizedNumber = norm,
            displayAddress = address,
            contactName = contactName,
            latestBody = body,
            latestTimestamp = System.currentTimeMillis(),
            unreadCount = 0,
            threadIds = emptyList(),
            isOutgoing = true,
            isDelivered = false
        )
        if (existingIndex >= 0) {
            current.removeAt(existingIndex)
        }
        current.add(0, updatedThread)
        _threads.value = current
    }
}
