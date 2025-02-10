package com.bulifier.core.utils

import java.io.Reader

/**
 * Reads characters from this Reader until the given terminator string is encountered or EOF.
 * The terminator is NOT included in the returned string.
 */
fun Reader.readUntil(terminator: String): String {
    val sb = StringBuilder()
    val termLength = terminator.length
    while (true) {
        val ch = this.read()
        if (ch == -1) break
        sb.append(ch.toChar())
        if (sb.length >= termLength && sb.substring(sb.length - termLength) == terminator) {
            return sb.substring(0, sb.length - termLength)
        }
    }
    return sb.toString()
}

/**
 * Reads characters from this Reader until the given terminator character is encountered or EOF.
 * The terminator is NOT included in the returned string.
 */
fun Reader.readUntil(terminator: Char): String {
    val sb = StringBuilder()
    while (true) {
        val ch = this.read()
        if (ch == -1) break
        val c = ch.toChar()
        if (c == terminator) break
        sb.append(c)
    }
    return sb.toString()
}

fun Reader.hasMore(): Boolean {
    mark(1)
    val c = read()
    reset()
    return c != -1
}