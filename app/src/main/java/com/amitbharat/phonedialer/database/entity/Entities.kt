package com.amitbharat.phonedialer.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val numbersJson: String, // JSON array of numbers
    val photoUri: String? = null,
    val email: String? = null,
    val isFavorite: Boolean = false,
    val notes: String? = null,
    val company: String? = null
)

@Entity(tableName = "call_logs")
data class CallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val name: String? = null,
    val callType: String, // INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED
    val timestamp: Long,
    val duration: Long,
    val simSlot: Int,
    val recordingPath: String? = null,
    val notes: String? = null
)

@Entity(tableName = "blocked_numbers")
data class BlockedNumberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val reason: String? = null,
    val timestamp: Long
)

@Entity(tableName = "speed_dial")
data class SpeedDialEntity(
    @PrimaryKey val digit: Int, // 1 - 9
    val name: String,
    val number: String,
    val photoUri: String? = null
)
