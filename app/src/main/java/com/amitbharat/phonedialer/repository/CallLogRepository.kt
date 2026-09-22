package com.amitbharat.phonedialer.repository

import android.content.ContentResolver
import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import com.amitbharat.phonedialer.database.AppDatabase
import com.amitbharat.phonedialer.database.entity.CallLogEntity
import com.amitbharat.phonedialer.database.entity.SpeedDialEntity
import com.amitbharat.phonedialer.model.CallLogItem
import com.amitbharat.phonedialer.model.CallType
import com.amitbharat.phonedialer.model.SpeedDialItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class CallLogRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val callLogDao = db.callLogDao()
    private val speedDialDao = db.speedDialDao()

    companion object {
        @Volatile
        private var cachedCallLogs: List<CallLogItem>? = null
        @Volatile
        private var lastLogFetchTime: Long = 0L

        fun getCachedCallLogs(): List<CallLogItem> = cachedCallLogs ?: emptyList()

        fun updateCachedCallLogs(logs: List<CallLogItem>) {
            cachedCallLogs = logs
            lastLogFetchTime = System.currentTimeMillis()
        }
    }

    fun getCachedCallLogs(): List<CallLogItem> = cachedCallLogs ?: emptyList()

    private fun deduplicateLogs(logs: List<CallLogItem>): List<CallLogItem> {
        return logs.distinctBy { item ->
            val cleanNum = item.number.replace(Regex("[^0-9+]"), "")
            "${cleanNum}_${item.timestamp}_${item.callType.name}_${item.duration}"
        }.sortedByDescending { it.timestamp }
    }

    fun getAllCallLogs(forceRefresh: Boolean = false): Flow<List<CallLogItem>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedCallLogs
        if (!forceRefresh && cached != null && (now - lastLogFetchTime < 30_000)) {
            emit(cached)
        } else {
            val deviceLogs = deduplicateLogs(fetchDeviceCallLogsDirectly())
            cachedCallLogs = deviceLogs
            lastLogFetchTime = now
            emit(deviceLogs)
        }

        callLogDao.getAllCallLogs().map { list ->
            val dbModels = list.map { it.toModel() }
            val current = cachedCallLogs ?: emptyList()
            deduplicateLogs(current + dbModels)
        }.collect {
            cachedCallLogs = it
            emit(it)
        }
    }.flowOn(Dispatchers.IO)

    suspend fun addCallLog(item: CallLogItem): Long = withContext(Dispatchers.IO) {
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
        callLogDao.insertCallLog(entity)
    }

    suspend fun deleteCallLog(id: Long) = withContext(Dispatchers.IO) {
        callLogDao.deleteCallLog(id)
    }

    suspend fun clearCallLogs() = withContext(Dispatchers.IO) {
        callLogDao.clearCallLogs()
    }

    fun fetchDeviceCallLogsDirectly(): List<CallLogItem> {
        val result = mutableListOf<CallLogItem>()
        try {
            // Build saved contact number sets for O(1) instant lookup
            val (savedExactNumbers, savedLast10Numbers) = getSavedContactNumberIndices()

            // Map available recordings from recordings folder
            val recordingsMap = getRecordingsMap()

            val resolver: ContentResolver = context.contentResolver
            val cursor = resolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls._ID,
                    CallLog.Calls.NUMBER,
                    CallLog.Calls.CACHED_NAME,
                    CallLog.Calls.TYPE,
                    CallLog.Calls.DATE,
                    CallLog.Calls.DURATION
                ),
                null,
                null,
                CallLog.Calls.DATE + " DESC"
            )

            cursor?.use {
                val idIdx = it.getColumnIndex(CallLog.Calls._ID)
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val durIdx = it.getColumnIndex(CallLog.Calls.DURATION)

                while (it.moveToNext()) {
                    val id = if (idIdx >= 0) it.getLong(idIdx) else 0L
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
                        val cleanNum = number.replace(Regex("[^0-9+]"), "")
                        val digitsOnly = number.replace(Regex("[^0-9]"), "")
                        val last10 = if (digitsOnly.length >= 10) digitsOnly.takeLast(10) else digitsOnly
                        val isSaved = savedExactNumbers.contains(cleanNum) || 
                                      (last10.isNotBlank() && savedLast10Numbers.contains(last10))
                        val recordingPath = recordingsMap[cleanNum]

                        result.add(
                            CallLogItem(
                                id = id,
                                number = number,
                                name = if (!name.isNullOrBlank()) name else null,
                                callType = callType,
                                timestamp = date,
                                duration = duration,
                                simSlot = 0,
                                recordingPath = recordingPath,
                                isSavedContact = isSaved
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return result
    }

    private fun getSavedContactNumberIndices(): Pair<Set<String>, Set<String>> {
        val exactSet = HashSet<String>()
        val last10Set = HashSet<String>()

        // First check if cached contacts are already in memory to avoid querying ContactsProvider
        val cached = ContactsRepository.getCachedContacts()
        if (cached.isNotEmpty()) {
            for (contact in cached) {
                for (num in contact.numbers) {
                    val clean = num.replace(Regex("[^0-9+]"), "")
                    val digits = num.replace(Regex("[^0-9]"), "")
                    if (clean.isNotBlank()) exactSet.add(clean)
                    if (digits.length >= 10) last10Set.add(digits.takeLast(10))
                    else if (digits.isNotBlank()) last10Set.add(digits)
                }
            }
            return Pair(exactSet, last10Set)
        }

        try {
            val resolver = context.contentResolver
            val cursor = resolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                null,
                null,
                null
            )
            cursor?.use {
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val raw = if (numIdx >= 0) it.getString(numIdx) ?: "" else ""
                    val clean = raw.replace(Regex("[^0-9+]"), "")
                    val digits = raw.replace(Regex("[^0-9]"), "")
                    if (clean.isNotBlank()) exactSet.add(clean)
                    if (digits.length >= 10) last10Set.add(digits.takeLast(10))
                    else if (digits.isNotBlank()) last10Set.add(digits)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return Pair(exactSet, last10Set)
    }

    private fun getRecordingsMap(): Map<String, String> {
        val map = HashMap<String, String>()
        try {
            val candidateDirs = listOf(
                File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_RECORDINGS), "CallRecordings"),
                File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), "CallRecordings"),
                File(context.getExternalFilesDir(null), "CallRecordings"),
                File(context.getExternalFilesDir(null), "Recordings")
            )

            candidateDirs.forEach { dir ->
                if (dir.exists() && dir.isDirectory) {
                    dir.listFiles()?.forEach { file ->
                        val name = file.name
                        if ((name.startsWith("Call_") || name.startsWith("REC_")) &&
                            (name.endsWith(".m4a") || name.endsWith(".aac") || name.endsWith(".mp3") || name.endsWith(".mp4"))
                        ) {
                            val parts = name.split("_")
                            for (part in parts) {
                                val clean = part.replace(Regex("[^0-9+]"), "")
                                if (clean.length >= 6) {
                                    map[clean] = file.absolutePath
                                    if (clean.length >= 10) {
                                        map[clean.takeLast(10)] = file.absolutePath
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    suspend fun syncDeviceCallLogs() = withContext(Dispatchers.IO) {
        callLogDao.clearUnrecordedCallLogs()
    }

    // Speed Dial
    fun getSpeedDials(): Flow<List<SpeedDialItem>> {
        return speedDialDao.getSpeedDials().map { list ->
            list.map { SpeedDialItem(digit = it.digit, name = it.name, number = it.number, photoUri = it.photoUri) }
        }
    }

    suspend fun setSpeedDial(item: SpeedDialItem) = withContext(Dispatchers.IO) {
        speedDialDao.setSpeedDial(
            SpeedDialEntity(digit = item.digit, name = item.name, number = item.number, photoUri = item.photoUri)
        )
    }

    suspend fun deleteSpeedDial(digit: Int) = withContext(Dispatchers.IO) {
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
