package com.pocketci.app.shizuku

import com.pocketci.app.core.CiResult
import com.pocketci.app.util.ShellQuote.q
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Silent install/uninstall/query via shell-identity (uid 2000) through
 * Shizuku. `pm` as shell installs with NO user dialog — this is the whole
 * reason PocketCI exists.
 */
class ShizukuInstaller(private val timeoutMs: Long = 120_000) {

    // ------------------------------------------------------------------
    // install()
    // ------------------------------------------------------------------
    // Strategy: plain `pm install` with flags, NOT the PackageInstaller
    // session API. Sessions give progress callbacks; we don't need them
    // (build step already streamed the interesting output, install is
    // seconds). Plain pm = one process, simple output parsing, works on
    // every Android ≥ 5.0. Session API via shell would need a helper —
    // revisit only if M5 needs install progress bars.
    // ------------------------------------------------------------------
    fun install(
        apk: File,
        replace: Boolean = true,
        allowDowngrade: Boolean = false,
    ): CiResult<Unit> {
        if (!apk.exists()) return CiResult.Err("APK missing: ${apk.path}")
        if (apk.length() == 0L) return CiResult.Err("APK is 0 bytes: ${apk.path}")

        val flags = buildString {
            if (replace) append(" -r")
            if (allowDowngrade) append(" -d")
        }
        val cmd = "pm install$flags ${q(apk.absolutePath)}"

        return try {
            val p = ShizukuGuard.useShell(cmd) { it }
            val out = p.inputStream.bufferedReader().readText().trim()
            val finished = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                p.destroy()
                return CiResult.Err("pm install timed out after ${timeoutMs / 1000}s")
            }
            val rc = p.exitValue()
            // pm prints "Success" or "Failure [REASON]" on stdout.
            if (rc == 0 && out.endsWith("Success")) {
                CiResult.Ok(Unit)
            } else {
                val reason = out.lineSequence()
                    .firstOrNull { it.contains("Failure") }
                    ?.substringAfter("Failure")
                    ?.trim('[', ']')
                    ?: "rc=$rc, output=$out"
                CiResult.Err("pm install failed: ${mapInstallError(reason)} ($reason)")
            }
        } catch (t: Throwable) {
            CiResult.Err("install exception: ${t.message}", t)
        }
    }

    // ------------------------------------------------------------------
    // uninstall() — keep the data (-k) by default: a CI reinstall shouldn't
    // wipe the app's user data unless the pipeline explicitly asks.
    // ------------------------------------------------------------------
    fun uninstall(packageName: String, keepData: Boolean = true): CiResult<Unit> {
        val cmd = "pm uninstall ${if (keepData) "-k " else ""}${q(packageName)}"
        return try {
            val p = ShizukuGuard.useShell(cmd) { it }
            val out = p.inputStream.bufferedReader().readText().trim()
            if (p.waitFor(timeoutMs, TimeUnit.MILLISECONDS) && p.exitValue() == 0
                && out.endsWith("Success")
            ) CiResult.Ok(Unit)
            else CiResult.Err("pm uninstall failed: $out")
        } catch (t: Throwable) {
            CiResult.Err("uninstall exception: ${t.message}", t)
        }
    }

    // ------------------------------------------------------------------
    // isInstalled() + installedVersion() — used by InstallStep to log
    // "updating com.foo v1.2 → v1.3" lines, and by the UI's run summary.
    // ------------------------------------------------------------------
    fun isInstalled(packageName: String): Boolean =
        ShizukuGuard.execShell("pm path ${q(packageName)}").ok

    fun installedVersion(packageName: String): String? {
        val r = ShizukuGuard.execShell("dumpsys package ${q(packageName)} | grep versionName")
        if (!r.ok) return null
        return r.stdout.lineSequence()
            .map { it.substringAfter("versionName=").trim() }
            .firstOrNull { it.isNotBlank() }
    }

    // ------------------------------------------------------------------
    // Error translation table. `pm` returns machine codes; log files get
    // the RAW code (grep-able), the Err message gets the WHY (human).
    // ------------------------------------------------------------------
    private fun mapInstallError(code: String): String = when (code) {
        "INSTALL_FAILED_ALREADY_EXISTS" -> "already installed, use replace"
        "INSTALL_FAILED_INVALID_APK" -> "APK corrupt or unsigned"
        "INSTALL_FAILED_OLDER_SDK" -> "APK minSdk above device API"
        "INSTALL_FAILED_UPDATE_INCOMPATIBLE" ->
            "signature mismatch with installed version (new signing key?)"
        "INSTALL_FAILED_INSUFFICIENT_STORAGE" -> "device storage full"
        "INSTALL_PARSE_FAILED_NOT_APK" -> "file is not an APK"
        "INSTALL_PARSE_FAILED_NO_CERTIFICATES" -> "APK unsigned or bad signature"
        "INSTALL_FAILED_USER_RESTRICTED" -> "install blocked (work profile/parental control)"
        else -> code
    }
}
