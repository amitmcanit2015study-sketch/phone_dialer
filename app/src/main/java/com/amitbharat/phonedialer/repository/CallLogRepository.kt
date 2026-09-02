package com.amitbharat.phonedialer.repository

import android.content.ContentResolver
import android.content.Context
import android.provider.CallLog
import com.amitbharat.phonedialer.database.AppDatabase
import com.amitbharat.phonedialer.database.entity.CallLogEntity
import com.amitbharat.phonedialer.database.entity.SpeedDialEntity
import com.amitbharat.phonedialer.model.CallLogItem
import com.amitbharat.phonedialer.model.CallType
import com.amitbharat.phonedialer.model.SpeedDialItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class CallLogRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val callLogDao = db.callLogDao()
    private val speedDialDao = db.speedDialDao()

    fun getAllCallLogs(): Flow<List<CallLogItem>> {
        return callLogDao.getAllCallLogs().map { list ->
            list.map { it.toModel() }
        }
    }

    suspend fun addCallLog(item: CallLogItem): Long {
        val entity = CallLogEntity(
            number = item.number,
            name = item.name,
            callType = item.callType.name,
            timestamp = item.timestamp,
            duration = item.duration,
            simSlot = item.simSlot,
            recordingPath = item.recordingPath,
            notes = item.notes
        )
        return callLogDao.insertCallLog(entity)
    }

    suspend fun deleteCallLog(id: Long) {
        callLogDao.deleteCallLog(id)
    }

    suspend fun clearCallLogs() {
        callLogDao.clearCallLogs()
    }

    suspend fun syncDeviceCallLogs() = withContext(Dispatchers.IO) {
        try {
            val resolver: ContentResolver = context.contentResolver
            val cursor = resolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null,
                null,
                CallLog.Calls.DATE + " DESC LIMIT 200"
            )

            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)

                while (it.moveToNext()) {
                    val number = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) it.getString(nameIdx) else null
                    val rawType = if (typeIdx >= 0) it.getInt(typeIdx) else CallLog.Calls.OUTGOING_TYPE
                    val date = if (dateIdx >= 0) it.getLong(dateIdx) else System.currentTimeMillis()
                    val duration = if (durIdx >= 0) it.getLong(durIdx) else 0L

                    val callType = when (rawType) {
                        CallLog.Calls.INCOMING_TYPE -> CallType.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallType.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallType.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallType.REJECTED
                        CallLog.Calls.BLOCKED_TYPE -> CallType.BLOCKED
                        else -> CallType.INCOMING
                    }

                    if (number.isNotBlank()) {
                        val entity = CallLogEntity(
                            number = number,
                            name = name,
                            callType = callType.name,
                            timestamp = date,
                            duration = duration,
                            simSlot = 0
                        )
                        callLogDao.insertCallLog(entity)
                    }
                }
            }
        } catch (ignored: Exception) {}
    }

    // Speed Dial
    fun getSpeedDials(): Flow<List<SpeedDialItem>> {
        return speedDialDao.getSpeedDials().map { list ->
            list.map { SpeedDialItem(digit = it.digit, name = it.name, number = it.number, photoUri = it.photoUri) }
        }
    }

    suspend fun setSpeedDial(item: SpeedDialItem) {
        speedDialDao.setSpeedDial(
            SpeedDialEntity(digit = item.digit, name = item.name, number = item.number, photoUri = item.photoUri)
        )
    }

    suspend fun deleteSpeedDial(digit: Int) {
        speedDialDao.deleteSpeedDial(digit)
    }

    private fun CallLogEntity.toModel(): CallLogItem {
        val type = try {
            CallType.valueOf(callType)
        } catch (e: Exception) {
            CallType.OUTGOING
        }
        return CallLogItem(
            id = id,
            number = number,
            name = name,
            callType = type,
            timestamp = timestamp,
            duration = duration,
            simSlot = simSlot,
            recordingPath = recordingPath,
            notes = notes
        )
    }
}
