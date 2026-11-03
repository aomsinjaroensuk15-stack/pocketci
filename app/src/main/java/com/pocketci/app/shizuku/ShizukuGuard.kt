package com.pocketci.app.shizuku

import android.content.pm.PackageManager
import moe.shizuku.api.Shizuku

/**
 * Single choke-point for everything Shizuku.
 * Rule: no file outside this package may import moe.shizuku.* directly.
 * If Shizuku's API ever changes, exactly ONE file adapts.
 */
object ShizukuGuard {

    enum class State { OK, NO_BINDER, NO_PERMISSION, VERSION_TOO_OLD }

    // ------------------------------------------------------------------
    // State queries — cheap, synchronous, call anywhere
    // ------------------------------------------------------------------

    fun isAvailable(): Boolean = Shizuku.pingBinder()

    fun hasPermission(): Boolean =
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED

    fun state(): State = when {
        !isAvailable() -> State.NO_BINDER
        !hasPermission() -> State.NO_PERMISSION
        else -> State.OK
    }

    fun shizukuVersion(): Int = if (isAvailable()) Shizuku.getVersion() else -1

    // ------------------------------------------------------------------
    // Permission flow — Shizuku's permission dialog, not Android's runtime
    // dialog. requestCode is caller-chosen; 0 is fine, we only ever have
    // one in-flight request.
    // ------------------------------------------------------------------

    fun requestPermission(onResult: (granted: Boolean) -> Unit) {
        val listener = object : Shizuku.OnRequestPermissionResultListener {
            override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                Shizuku.removeRequestPermissionResultListener(this)
                onResult(grantResult == PackageManager.PERMISSION_GRANTED)
            }
        }
        Shizuku.addRequestPermissionResultListener(listener)
        Shizuku.requestPermission(0)
    }

    // ------------------------------------------------------------------
    // Binder lifecycle — Shizuku's server process can DIE (user force-stops
    // it, it crashes, adb connection drops). Every long-lived consumer must
    // listen so the UI doesn't keep claiming "Shizuku ready ✅" over a dead
    // binder. Callers hold the returned token and pass it to
    // removeBinderDeadListener when done (Activity onDestroy, etc).
    // ------------------------------------------------------------------

    fun addBinderDeadListener(onDead: () -> Unit): Shizuku.OnBinderDeadListener {
        val listener = Shizuku.OnBinderDeadListener { onDead() }
        Shizuku.addBinderDeadListener(listener)
        return listener
    }

    fun removeBinderDeadListener(listener: Shizuku.OnBinderDeadListener) {
        Shizuku.removeBinderDeadListener(listener)
    }

    fun addBinderReceivedListener(onUp: () -> Unit): Shizuku.OnBinderReceivedListener {
        val listener = Shizuku.OnBinderReceivedListener { onUp() }
        Shizuku.addBinderReceivedListener(listener)
        return listener
    }

    fun removeBinderReceivedListener(listener: Shizuku.OnBinderReceivedListener) {
        Shizuku.removeBinderReceivedListener(listener)
    }

    // ------------------------------------------------------------------
    // THE SHELL BRIDGE. Shizuku.newProcess spawns a process as the SHELL
    // uid (2000) — the same identity `adb shell` has. That's why
    // `pm install` works silently with no user dialog: shell is a trusted
    // installer. No root needed, no confirmation prompt, no REQUEST_INSTALL
    //_PACKAGES dance.
    //
    // env/cwd nullable = inherit. Redirect stderr into stdout so callers
    // reading inputStream see everything ("Failure [INSTALL_FAILED...]"
    // comes back on stderr — without this we'd miss WHY installs fail).
    // ------------------------------------------------------------------
    fun newShell(cmd: String): Process =
        Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)

    // ------------------------------------------------------------------
    // useShell {} — the required wrapper for privileged ops.
    // check-then-run, so a lost permission mid-run fails LOUDLY at the
    // choke-point instead of half-working somewhere deep. Throws
    // IllegalStateException with a specific message; all callers in
    // ShizukuInstaller/PackageOps catch Throwable and wrap into CiResult.
    // ------------------------------------------------------------------
    inline fun <T> useShell(cmd: String, block: (Process) -> T): T {
        check(isAvailable()) { "Shizuku binder not running" }
        check(hasPermission()) { "Shizuku permission not granted" }
        return block(newShell(cmd))
    }

    /** Run a shell command, collect combined output + exit code. */
    fun execShell(cmd: String, timeoutMs: Long = 60_000): ShellResult {
        if (state() != State.OK) return ShellResult(-1, "", "shizuku not ready: ${state()}")
        return try {
            val p = newShell(cmd)
            val out = p.inputStream.bufferedReader().readText()
            val finished = p.waitFor(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (finished) ShellResult(p.exitValue(), out, "")
            else {
                p.destroy()
                ShellResult(-2, out, "timeout after ${timeoutMs}ms")
            }
        } catch (t: Throwable) {
            ShellResult(-3, "", t.message ?: t.toString())
        }
    }

    data class ShellResult(val exitCode: Int, val stdout: String, val stderrOrError: String) {
        val ok: Boolean get() = exitCode == 0
    }
}
