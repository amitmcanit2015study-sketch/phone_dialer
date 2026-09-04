package com.amitbharat.phonedialer.model

enum class CallType {
    INCOMING,
    OUTGOING,
    MISSED,
    REJECTED,
    BLOCKED
}

data class CallLogItem(
    val id: Long = 0,
    val number: String,
    val name: String? = null,
    val callType: CallType = CallType.OUTGOING,
    val timestamp: Long = System.currentTimeMillis(),
    val duration: Long = 0, // In seconds
    val simSlot: Int = 0, // 0 for SIM 1, 1 for SIM 2
    val recordingPath: String? = null,
    val notes: String? = null,
    val isSavedContact: Boolean = true
)
