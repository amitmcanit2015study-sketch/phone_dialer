package com.amitbharat.phonedialer.search

import com.amitbharat.phonedialer.model.Contact

/**
 * High-performance, zero-allocation T9 search engine.
 * Eliminates all Regex patterns, intermediate String allocations, and Map lookups.
 */
object T9SearchEngine {

    @Suppress("NOTHING_TO_INLINE")
    private inline fun charToT9(ch: Char): Char = when (ch) {
        'a', 'b', 'c', 'A', 'B', 'C' -> '2'
        'd', 'e', 'f', 'D', 'E', 'F' -> '3'
        'g', 'h', 'i', 'G', 'H', 'I' -> '4'
        'j', 'k', 'l', 'J', 'K', 'L' -> '5'
        'm', 'n', 'o', 'M', 'N', 'O' -> '6'
        'p', 'q', 'r', 's', 'P', 'Q', 'R', 'S' -> '7'
        't', 'u', 'v', 'T', 'U', 'V' -> '8'
        'w', 'x', 'y', 'z', 'W', 'X', 'Y', 'Z' -> '9'
        in '0'..'9' -> ch
        else -> '\u0000'
    }

    private fun matchesNumber(number: String, query: String): Boolean {
        val qLen = query.length
        val nLen = number.length
        if (qLen == 0 || nLen < qLen) return false

        var startIdx = 0
        while (startIdx < nLen) {
            var qIdx = 0
            var nIdx = startIdx
            while (qIdx < qLen && nIdx < nLen) {
                val c = number[nIdx]
                if (c !in '0'..'9' && c != '+') {
                    nIdx++
                    continue
                }
                if (c != query[qIdx]) break
                nIdx++
                qIdx++
            }
            if (qIdx == qLen) return true
            startIdx++
        }
        return false
    }

    private fun matchesT9(name: String, query: String): Boolean {
        val qLen = query.length
        val nLen = name.length
        if (qLen == 0 || nLen < qLen) return false

        // 1. Check substring T9 match anywhere in the name
        var startIdx = 0
        while (startIdx < nLen) {
            var qIdx = 0
            var nIdx = startIdx
            while (qIdx < qLen && nIdx < nLen) {
                val t9Char = charToT9(name[nIdx])
                if (t9Char == '\u0000') {
                    nIdx++
                    continue
                }
                if (t9Char != query[qIdx]) break
                nIdx++
                qIdx++
            }
            if (qIdx == qLen) return true
            startIdx++
        }

        // 2. Check initials / word starts (e.g., "Rahul Sharma" -> "RS" -> "77")
        var wordStart = true
        var matchedInitials = 0
        for (i in 0 until nLen) {
            val c = name[i]
            if (c.isWhitespace() || c == '.' || c == '_' || c == '-') {
                wordStart = true
            } else if (wordStart) {
                wordStart = false
                val t9 = charToT9(c)
                if (t9 != '\u0000' && matchedInitials < qLen && t9 == query[matchedInitials]) {
                    matchedInitials++
                    if (matchedInitials == qLen) return true
                }
            }
        }

        return false
    }

    fun search(query: String, contacts: List<Contact>): List<Contact> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return emptyList()

        val results = ArrayList<Contact>(8)
        val count = contacts.size
        for (i in 0 until count) {
            val contact = contacts[i]
            var matched = false

            // Check numbers
            val numbers = contact.numbers
            val numCount = numbers.size
            for (j in 0 until numCount) {
                if (matchesNumber(numbers[j], cleanQuery)) {
                    matched = true
                    break
                }
            }

            // Check T9 name if not already matched
            if (!matched && matchesT9(contact.name, cleanQuery)) {
                matched = true
            }

            if (matched) {
                results.add(contact)
                if (results.size >= 8) break
            }
        }
        return results
    }
}
