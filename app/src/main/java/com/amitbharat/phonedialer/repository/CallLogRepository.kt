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
        @Volatile
        private var cachedRecordingsMap: Map<String, String>? = null
        @Volatile
        private var lastRecordingsScanTime: Long = 0L
        @Volatile
        var currentFetchLimit: Int = 60

        private val _callLogsFlow = kotlinx.coroutines.flow.MutableStateFlow<List<CallLogItem>>(emptyList())

        fun getCachedCallLogs(): List<CallLogItem> = cachedCallLogs ?: emptyList()

        fun updateCachedCallLogs(logs: List<CallLogItem>) {
            cachedCallLogs = logs
            lastLogFetchTime = System.currentTimeMillis()
            _callLogsFlow.value = logs
        }
    }

    fun getCachedCallLogs(): List<CallLogItem> = cachedCallLogs ?: emptyList()

    private fun extractDigits(raw: String): String {
        val len = raw.length
        val sb = StringBuilder(len)
        for (i in 0 until len) {
            val c = raw[i]
            if (c in '0'..'9') sb.append(c)
        }
        return sb.toString()
    }

    private fun extractCleanNumber(raw: String): String {
        val len = raw.length
        val sb = StringBuilder(len)
        for (i in 0 until len) {
            val c = raw[i]
            if (c in '0'..'9' || c == '+') sb.append(c)
        }
        return sb.toString()
    }

    private fun deduplicateLogs(logs: List<CallLogItem>): List<CallLogItem> {
        val seen = HashSet<String>(logs.size)
        val result = ArrayList<CallLogItem>(logs.size)
        for (i in logs.indices) {
            val item = logs[i]
            val digits = extractDigits(item.number)
            if (digits.length < 3) continue
            val cleanNum = extractCleanNumber(item.number)
            val key = "${cleanNum}_${item.timestamp}_${item.callType.name}_${item.duration}"
            if (seen.add(key)) {
                result.add(item)
            }
        }
        result.sortByDescending { it.timestamp }
        return result
    }

    fun getAllCallLogs(forceRefresh: Boolean = false): Flow<List<CallLogItem>> = flow {
        val now = System.currentTimeMillis()
        val cached = cachedCallLogs
        if (!forceRefresh && cached != null && (now - lastLogFetchTime < 30_000)) {
            emit(cached)
        } else {
            // Lazy Loading: Only load the most recent calls (default 60) for instant startup.
            // Do NOT load complete historical call logs on app startup!
            val recentLogs = deduplicateLogs(fetchDeviceCallLogsDirectly(daysLimit = null, maxCount = currentFetchLimit))
            cachedCallLogs = recentLogs
            lastLogFetchTime = now
            _callLogsFlow.value = recentLogs
            emit(recentLogs)
        }

        _callLogsFlow.collect { updatedList ->
            if (updatedList.isNotEmpty()) {
                emit(updatedList)
            }
        }
    }.flowOn(Dispatchers.IO)

    suspend fun loadMoreCallLogs(batchSize: Int = 60): List<CallLogItem> = withContext(Dispatchers.IO) {
        currentFetchLimit += batchSize
        val updated = deduplicateLogs(fetchDeviceCallLogsDirectly(daysLimit = null, maxCount = currentFetchLimit))
        cachedCallLogs = updated
        lastLogFetchTime = System.currentTimeMillis()
        _callLogsFlow.value = updated
        updated
    }

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

    fun fetchDeviceCallLogsDirectly(daysLimit: Int? = null, maxCount: Int? = null): List<CallLogItem> {
        val result = mutableListOf<CallLogItem>()
        try {
            // Build saved contact number sets for O(1) instant lookup
            val (savedExactNumbers, savedLast10Numbers) = getSavedContactNumberIndices()

            // Skip file scanning during fast initial load
            val recordingsMap = if (maxCount != null && maxCount <= 50) emptyMap() else getRecordingsMap()

            val resolver: ContentResolver = context.contentResolver
            val (selection, selectionArgs) = if (daysLimit != null) {
                val minDate = System.currentTimeMillis() - (daysLimit.toLong() * 24L * 60L * 60L * 1000L)
                Pair("${CallLog.Calls.DATE} >= ?", arrayOf(minDate.toString()))
            } else {
                Pair(null, null)
            }

            val sortOrder = if (maxCount != null) "${CallLog.Calls.DATE} DESC LIMIT $maxCount" else "${CallLog.Calls.DATE} DESC"

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
                selection,
                selectionArgs,
                sortOrder
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
                        else -> null // Reject OEM SMS/MMS types or unknown types
                    }

                    // Only process recognized call types
                    if (callType == null) continue

                    if (number.isNotBlank()) {
                        val digitsOnly = number.filter { it.isDigit() }
                        // Filter out SMS sender IDs or non-dialable short codes without digits
                        if (digitsOnly.length < 3) continue

                        val cleanNum = number.replace(Regex("[^0-9+]"), "")
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
                    val clean = extractCleanNumber(num)
                    val digits = extractDigits(num)
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
                    val clean = extractCleanNumber(raw)
                    val digits = extractDigits(raw)
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
        val now = System.currentTimeMillis()
        val cached = cachedRecordingsMap
        if (cached != null && (now - lastRecordingsScanTime < 60_000)) {
            return cached
        }
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
        cachedRecordingsMap = map
        lastRecordingsScanTime = now
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
