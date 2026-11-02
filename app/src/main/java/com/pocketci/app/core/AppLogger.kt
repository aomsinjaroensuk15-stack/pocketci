// ============================================================================
// PocketCI — AppLogger.kt
// ----------------------------------------------------------------------------
// The run-log system. Every line a step writes via RunContext.log()
// (Phase 32) lands in TWO places with different lifetimes:
//
//   1. A FILE per run  — run_<id>.log under filesDir/logs/
//      Survives: process death, app kill, reboot, WorkManager retry.
//      This is the SOURCE OF TRUTH. A CI that loses its logs when the
//      phone dies mid-build is a CI you can't debug.
//
//   2. logcat          — for `adb logcat` / Termux `logcat` debugging while
//      developing. Ephemeral by design, never read by the app itself.
//
// What does NOT live here: LogEntity rows in Room (Phase 24). That's the
// M2 live-streaming path for the RunDetail UI. File = history, Room =
// live view. Two consumers, one writer, no coupling.
// ============================================================================

package com.pocketci.app.core

import android.content.Context
import android.util.Log
import java.io.File

object AppLogger {

    private const val TAG = "PocketCI"

    // Lateinit: init() runs in PocketCiApp.onCreate (Phase 14), which is
    // guaranteed to fire before any worker/activity. If someone calls a
    // logging method before init, we WANT the UninitializedPropertyAccess
    // exception — that's a wiring bug, and silently swallowing logs would
    // hide it.
    private lateinit var logDir: File

    /** Called exactly once, from PocketCiApp.onCreate. */
    fun init(ctx: Context) {
        // filesDir, not cacheDir: cacheDir can be purged by the system
        // under storage pressure — logs are diagnostic history, not cache.
        logDir = File(ctx.filesDir, "logs").apply { mkdirs() }
    }

    /** The log file for a given run. Public: RunDetailActivity's M2
     *  "share log" feature will need this path. */
    fun fileFor(runId: Long): File = File(logDir, "run_$runId.log")

    /** Plain logcat-only line. For framework-level events that aren't tied
     *  to any run (e.g. "Shizuku binder died"). Deliberately NOT also
     *  written to a file — no run owns it, and a global file that mixes
     *  runs together is how you get unparsable logs. */
    fun log(tag: String, msg: String) = Log.d(tag, msg)

    /**
     * Append one line to a run's file.
     *
     * FORMAT: "<epochMillis> <line>" — timestamp first, space-separated,
     * never pretty-printed. Why ugly? Because this file gets:
     *   - tailed live (split on "\n", take prefix as Long)
     *   - shared via ACTION_SEND to a text editor
     *   - parsed by nothing fancy (we may grep it in Termux: `grep 1730
     *     run_42.log`)
     * ISO-8601 would be prettier and slower to eyeball in a terminal.
     * Unix millis is a number you can sort, diff, and threshold.
     *
     * THREAD SAFETY: appendText on File is NOT atomic across processes,
     * but all our writers are in one process (RunWorker + steps on the IO
     * dispatcher). Coroutine + Dispatchers.IO gives us sequential execution
     * per run in practice. If we ever add a second process (unlikely), this
     * becomes synchronized(FileLock) — noted here so the constraint is
     * visible, not hidden.
     *
     * CRASH-DURING-APPEND edge case: appendText opens, writes, closes per
     * call. Worst case on kill mid-write = a truncated final line. We can
     * live with that; the NEXT line starts fresh. A torn line in a log is
     * cosmetic. A torn line in a DATABASE would be corruption — which is
     * why Room handles state (Phase 24) and this only handles narration.
     */
    fun append(runId: Long, line: String) {
        fileFor(runId).appendText("${System.currentTimeMillis()} $line\n")
    }
}

// ----------------------------------------------------------------------------
// USAGE in steps — every step logs via its RunContext:
//
//     ctx.log("gradlew assembleDebug (this may take a while…)")
//
// which is RunContext.log (Phase 32) delegating here. The step never
// touches File APIs directly. If the log format ever changes (JSON lines?
// rotating files?), exactly ONE file changes.
//
// RETENTION POLICY (not enforced yet — M2): files older than 30 days get
// purged at app start. 30 days × ~10 runs/day × ~50 KB/run ≈ 15 MB. Fine
// for app-private storage. Adding retention before we have any logs is
// premature; adding it never is how phones fill up.
// ----------------------------------------------------------------------------
