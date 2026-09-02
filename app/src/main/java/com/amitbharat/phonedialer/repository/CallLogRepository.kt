package com.amitbharat.phonedialer.repository

import android.content.Context
import com.amitbharat.phonedialer.database.AppDatabase
import com.amitbharat.phonedialer.database.entity.CallLogEntity
import com.amitbharat.phonedialer.database.entity.SpeedDialEntity
import com.amitbharat.phonedialer.model.CallLogItem
import com.amitbharat.phonedialer.model.CallType
import com.amitbharat.phonedialer.model.SpeedDialItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class CallLogRepository(context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val callLogDao = db.callLogDao()
    private val speedDialDao = db.speedDialDao()

    fun getAllCallLogs(): Flow<List<CallLogItem>> {
        return callLogDao.getAllCallLogs().map { list ->
            list.map { it.toModel() }
        }
    }

    fun getMissedCalls(): Flow<List<CallLogItem>> {
        return callLogDao.getMissedCalls().map { list ->
            list.map { it.toModel() }
        }
    }

    fun getRecordedCalls(): Flow<List<CallLogItem>> {
        return callLogDao.getRecordedCalls().map { list ->
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
