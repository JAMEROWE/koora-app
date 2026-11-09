package com.koora.app.ui

import com.koora.app.data.Match
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Small formatting helpers shared by all screens. */

private val utcParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

fun isLive(m: Match): Boolean = m.status == "LIVE" || m.status == "IN_PLAY"

/** Kickoff time in the user's local timezone, e.g. "19:00". */
fun kickoffLabel(m: Match): String = try {
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(utcParser.parse(m.utcDate)!!)
} catch (_: Exception) {
    m.utcDate.take(16).replace('T', ' ')
}

/** Full date for the detail screen, e.g. "Sat 9 Nov, 19:00". */
fun dateLabel(m: Match): String = try {
    SimpleDateFormat("EEE d MMM, HH:mm", Locale.getDefault()).format(utcParser.parse(m.utcDate)!!)
} catch (_: Exception) {
    m.utcDate.take(16).replace('T', ' ')
}

/** "2 - 1" when a score exists, otherwise the kickoff time. */
fun scoreLabel(m: Match): String {
    val ft = m.score.fullTime
    return if (ft?.home != null && ft.away != null) "${ft.home} - ${ft.away}" else kickoffLabel(m)
}

/** Small badge under the score: LIVE / FT / kickoff time. */
fun statusLabel(m: Match): String = when {
    isLive(m) -> "LIVE"
    m.status == "FINISHED" -> "FT"
    else -> kickoffLabel(m)
}
