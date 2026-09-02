package com.amitbharat.phonedialer.model

data class Contact(
    val id: Long = 0,
    val name: String,
    val numbers: List<String> = emptyList(),
    val photoUri: String? = null,
    val email: String? = null,
    val isFavorite: Boolean = false,
    val notes: String? = null,
    val company: String? = null
)
