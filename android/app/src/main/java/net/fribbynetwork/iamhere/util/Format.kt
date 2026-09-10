package net.fribbynetwork.iamhere.util

import java.util.Locale
import java.util.concurrent.TimeUnit

fun fmtCoord(v: Double, decimals: Int = 6): String =
    String.format(Locale.US, "%.${decimals}f", v)

fun fmtMeters(m: Double?): String = when {
    m == null -> "--"
    m < 1000 -> String.format(Locale.US, "%.0f m", m)
    else -> String.format(Locale.US, "%.2f km", m / 1000.0)
}

fun fmtSpeed(mps: Float?): String =
    if (mps == null) "--" else String.format(Locale.US, "%.0f km/h", mps * 3.6f)

fun fmtElapsed(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}

fun fmtClock(unixMillis: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    return sdf.format(java.util.Date(unixMillis))
}

fun fmtDateTime(unixMillis: Long): String {
    val sdf = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return sdf.format(java.util.Date(unixMillis))
}
