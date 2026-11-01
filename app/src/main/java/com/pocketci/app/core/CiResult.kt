// ============================================================================
// PocketCI — CiResult.kt
// ----------------------------------------------------------------------------
// The error-handling type for EVERYTHING in the pipeline. Every step
// (checkout, build, sign, install, report) returns CiResult<Unit> instead
// of throwing. This file is short; the reasoning is long, because picking
// THIS over the alternatives is a real architectural decision.
// ============================================================================

package com.pocketci.app.core

// ----------------------------------------------------------------------------
// WHY NOT just throw exceptions? Three reasons, in increasing importance:
//
//   1. A CI pipeline is a SEQUENCE where each step's failure is EXPECTED,
//      not exceptional. A failing test isn't a bug — it's Tuesday. Exception
//      semantics ("something went catastrophically wrong") are wrong for
//      ordinary step failure.
//
//   2. Exceptions can't be LOGGED BY THE FAILING STEP and returned cleanly.
//      With CiResult, StepExecutor (Phase 34) receives the Err, writes it
//      into the run log, marks the run failed in Room, and the error path
//      is all in one place, all testable.
//
//   3. runAttemptCount-based retry (Phase 45) needs to distinguish "transient
//      network blip" (retry) from "gradle build failed" (don't retry — just
//      fail). An Err with a message is data; a thrown exception is control
//      flow. Data > control flow for retries.
// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------
// WHY NOT kotlin.Result? The stdlib Result<T> is usable, but:
//   - It's designed for CALLBACKS/coroutines (originally restricted so
//     people wouldn't use it for domain modeling).
//   - `Result` as a return type forces callers to handle it — good — but
//     its API is minimal (getOrNull, fold, exceptionOrNull) and its error
//     side is Throwable-only, which loses our structured "message + cause"
//     without wrapping.
//   - Most importantly: OUR error carries a human-readable message meant
//     for log files AND a structured cause. CiResult.Err models that
//     directly. Result<Throwable> would model it indirectly.
// Rolling a 15-line sealed type beats importing semantics that almost fit.
// ----------------------------------------------------------------------------

// ----------------------------------------------------------------------------
// WHY sealed interface (not sealed class)?
//   - interface: no state, no constructors, both impls are data classes.
//     Cleanest possible contract.
//   - sealed: the compiler KNOWS every subtype is in this file. So a
//     `when` over CiResult doesn't need an `else` branch — exhaustiveness
//     checked at compile time. Add a third variant someday (Cancelled?) and
//     every `when` in the app gets a compile error until handled. That's the
//     feature you want in a growing codebase.
//   - `out T` variance: Ok<T> can produce CiResult<Any> from CiResult<String>,
//     which makes mapping/combining painless.
// ----------------------------------------------------------------------------
sealed interface CiResult<out T> {

    /** Success path. Carries a value — often Unit for pipeline steps. */
    data class Ok<T>(val value: T) : CiResult<T>

    /**
     * Failure path. [message] is for HUMANS (log lines, the failed-run
     * summary). [cause] is optional and for DEVS (stack traces when we
     * log with Log.e(tag, msg, throwable)).
     *
     * Both fields deliberately nullable-free for message: a failure with
     * no explanation is a bug in the step that produced it.
     */
    data class Err(val message: String, val cause: Throwable? = null) : CiResult<Nothing>

    // ------------------------------------------------------------------
    // THE ONLY COMBINATOR WE NEED TODAY. map() lets a happy-path chain
    // transform Ok values without unwrapping. We intentionally do NOT add
    // flatMap/zip/andThen/etc. yet — combinators get invented when TWO
    // use sites need them, not when one site could theoretically use them.
    // Premature combinators = an algebra nobody asked for.
    //
    // `inline` + `crossinline`-free: this is a single expression, the
    // lambda inlines cleanly, zero allocation on the hot path.
    // ------------------------------------------------------------------
    inline fun <R> map(f: (T) -> R): CiResult<R> = when (this) {
        is Ok -> Ok(f(value))
        is Err -> this          // Err is CiResult<Nothing>, and Nothing is
                                // a subtype of every R — passes through.
    }
}

// ----------------------------------------------------------------------------
// USAGE CONTRACT (how every step looks; e.g. Phase 39 InstallStep):
//
//     override suspend fun run(ctx: RunContext, args: Map<String, String>)
//             : CiResult<Unit> {
//         val apk = File(ctx.artifactsDir, "app-signed.apk")
//         if (!apk.exists()) return CiResult.Err("APK missing at ${apk.path}")
//         return when (val r = ShizukuInstaller().install(apk)) {
//             is CiResult.Ok -> CiResult.Ok(Unit)
//             is CiResult.Err -> CiResult.Err("install: ${r.message}", r.cause)
//         }
//     }
//
// Read that pattern: fail early with a SPECIFIC message, never return
// Err("something went wrong"). The message IS the user-facing log line.
// ----------------------------------------------------------------------------
