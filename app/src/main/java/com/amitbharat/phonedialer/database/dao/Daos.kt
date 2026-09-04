package com.amitbharat.phonedialer.database.dao

import androidx.room.*
import com.amitbharat.phonedialer.database.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM contacts ORDER BY name ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE isFavorite = 1 ORDER BY name ASC")
    fun getFavoriteContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM contacts WHERE name LIKE '%' || :query || '%' OR numbersJson LIKE '%' || :query || '%'")
    suspend fun searchContacts(query: String): List<ContactEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity): Long

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)

    @Query("DELETE FROM contacts")
    suspend fun deleteAllContacts()
}

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_logs ORDER BY timestamp DESC")
    fun getAllCallLogs(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE callType = 'MISSED' ORDER BY timestamp DESC")
    fun getMissedCalls(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE callType = 'BLOCKED' ORDER BY timestamp DESC")
    fun getBlockedCalls(): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE recordingPath IS NOT NULL ORDER BY timestamp DESC")
    fun getRecordedCalls(): Flow<List<CallLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCallLog(callLog: CallLogEntity): Long

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteCallLog(id: Long)

    @Query("DELETE FROM call_logs WHERE recordingPath IS NULL AND notes IS NULL")
    suspend fun clearUnrecordedCallLogs()

    @Query("DELETE FROM call_logs")
    suspend fun clearCallLogs()
}

@Dao
interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers ORDER BY timestamp DESC")
    fun getBlockedNumbers(): Flow<List<BlockedNumberEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM blocked_numbers WHERE number = :number)")
    suspend fun isNumberBlocked(number: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedNumber(blocked: BlockedNumberEntity)

    @Delete
    suspend fun deleteBlockedNumber(blocked: BlockedNumberEntity)
}

@Dao
interface SpeedDialDao {
    @Query("SELECT * FROM speed_dial ORDER BY digit ASC")
    fun getSpeedDials(): Flow<List<SpeedDialEntity>>

    @Query("SELECT * FROM speed_dial WHERE digit = :digit LIMIT 1")
    suspend fun getSpeedDial(digit: Int): SpeedDialEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setSpeedDial(speedDial: SpeedDialEntity)

    @Query("DELETE FROM speed_dial WHERE digit = :digit")
    suspend fun deleteSpeedDial(digit: Int)
}
