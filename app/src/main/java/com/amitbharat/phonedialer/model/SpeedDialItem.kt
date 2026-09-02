package com.amitbharat.phonedialer.model

data class SpeedDialItem(
    val digit: Int, // 1 to 9
    val name: String,
    val number: String,
    val photoUri: String? = null
)

data class BlockedNumberItem(
    val id: Long = 0,
    val number: String,
    val reason: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
