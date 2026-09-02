package com.amitbharat.phonedialer.search

import com.amitbharat.phonedialer.model.Contact
import java.util.Locale

object T9SearchEngine {

    private val t9Map = mapOf(
        'a' to '2', 'b' to '2', 'c' to '2',
        'd' to '3', 'e' to '3', 'f' to '3',
        'g' to '4', 'h' to '4', 'i' to '4',
        'j' to '5', 'k' to '5', 'l' to '5',
        'm' to '6', 'n' to '6', 'o' to '6',
        'p' to '7', 'q' to '7', 'r' to '7', 's' to '7',
        't' to '8', 'u' to '8', 'v' to '8',
        'w' to '9', 'x' to '9', 'y' to '9', 'z' to '9'
    )

    fun nameToT9(name: String): String {
        val sb = StringBuilder()
        for (ch in name.lowercase(Locale.ROOT)) {
            if (t9Map.containsKey(ch)) {
                sb.append(t9Map[ch])
            } else if (ch.isDigit()) {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    fun search(query: String, contacts: List<Contact>): List<Contact> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return emptyList()

        return contacts.filter { contact ->
            // Match in phone numbers
            val numberMatch = contact.numbers.any { num ->
                num.replace("[^0-9+]".toRegex(), "").contains(cleanQuery)
            }
            if (numberMatch) return@filter true

            // Match T9 letters in full name or words
            val t9Name = nameToT9(contact.name)
            if (t9Name.contains(cleanQuery)) return@filter true

            // Check word initials (e.g. Rahul Sharma -> RS -> 77)
            val words = contact.name.split("\\s+".toRegex())
            val wordMatch = words.any { word ->
                nameToT9(word).startsWith(cleanQuery)
            }
            wordMatch
        }.take(8)
    }
}
