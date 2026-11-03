package com.pocketci.app.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Time formatting helpers for run lists, run detail, and logs.
 * All formatting funnels through here so the whole app shows time the same way.
 */
object TimeUtils {

    // ThreadLocal because SimpleDateFormat is NOT thread-safe. One instance
    // per thread, reused — cheaper than new'ing one per call.
    private val LOG_FMT = ThreadLocal.withInitial {
        SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
    }

    private val FULL_FMT = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    }

    /** Log line prefix: "11-03 07:00:14" — short, sorts well, locale digits. */
    fun fmt(ts: Long): String = LOG_FMT.get().format(Date(ts))

    /** Full timestamp for run detail headers. */
    fun fmtFull(ts: Long): String = FULL_FMT.get().format(Date(ts))

    /**
     * Human duration between two epoch millis: "3.2s", "1m 05s", "2h 14m".
     * Used under run titles ("built in 4m 12s") and in notifications.
     */
    fun duration(start: Long, end: Long): String {
        val totalSec = ((end - start) / 1000).coerceAtLeast(0)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return when {
            h > 0 -> "${h}h ${pad2(m)}m"
            m > 0 -> "${m}m ${pad2(s)}s"
            else -> "${s}.${((end - start) % 1000) / 100}s"
        }
    }

    /**
     * Relative time for the runs list: "just now", "5m ago", "3h ago",
     * "yesterday", "Nov 2". Compact beats precise in a scrolling list.
     */
    fun ago(ts: Long, now: Long = System.currentTimeMillis()): String {
        val diff = now - ts
        val min = 60_000L
        val hour = 60 * min
        val day = 24 * hour
        return when {
            diff < min -> "just now"
            diff < hour -> "${diff / min}m ago"
            diff < day -> "${diff / hour}h ago"
            isSameDay(ts, now) -> "yesterday"
            diff < 7 * day -> "${diff / day}d ago"
            else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(ts))
        }
    }

    /** "07:14" elapsed-style clock for a live running build. */
    fun clock(elapsedMs: Long): String {
        val totalSec = elapsedMs / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) "${h}:${pad2(m)}:${pad2(s)}" else "${pad2(m)}:${pad2(s)}"
    }

    /** True if [a] and [b] fall on the same calendar day in local time. */
    fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    /** True if [ts] is today local time. Used to group run list by Today/Yesterday. */
    fun isToday(ts: Long): Boolean = isSameDay(ts, System.currentTimeMillis())

    fun isYesterday(ts: Long): Boolean {
        val cal = Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            add(Calendar.DAY_OF_YEAR, -1)
        }
        val target = Calendar.getInstance().apply { timeInMillis = ts }
        return cal.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
            cal.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    }

    private fun pad2(v: Long): String = v.toString().padStart(2, '0')
}
