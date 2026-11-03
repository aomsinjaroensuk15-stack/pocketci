// ============================================================================
// PocketCI — ShellQuote.kt
// ----------------------------------------------------------------------------
// Six lines of code. Forty lines of why. This is the difference between
// a CI tool and a remote-code-execution-as-a-service.
//
// THE THREAT MODEL, concretely:
// ShizukuInstaller (Phase 22) and PackageOps (Phase 23) build shell
// commands like:
//
//     "pm install -r ${q(apk.absolutePath)}"
//
// The path comes from the filesystem — usually safe. But Phase 45's
// RunWorker builds Termux commands containing REPO NAMES from GitHub
// webhooks. A repo can be named anything. GitHub even allows... creative
// names. Without quoting, a repo named `foo; rm -rf /` turns our pipeline
// into a fork bomb with extra steps. Shell injection, meet CI runner.
//
// THE FIX IS ONE FUNCTION. This function. Never interpolate a string into
// a shell command without passing it through q() first. No exceptions.
// "It's just a file path I made" — q() it anyway. Consistency is the only
// thing that keeps you safe at 3 AM when you're tired and hacking.
// ============================================================================

package com.pocketci.app.util

object ShellQuote {

    // Pulled into named constants for ONE reason: the replacement string
    // is impossible to read inline, and "impossible to read" + "security-
    // critical" is a bad combination. SQ = a single quote character.
    // SQ_ESC = the shell-quoted escape for an embedded single quote.
    private const val SQ = "'"

    // THE TRICK, character by character:
    //
    //   ' \ ' '        (what SQ_ESC actually is: quote, backslash, quote, quote)
    //
    // Shell parsing of single quotes: everything between ' and ' is LITERAL.
    // There's no escape sequence inside single quotes — '\' is just a
    // backslash... until you CLOSE the quote. So to embed a literal '
    // inside a single-quoted string, you must:
    //   1. close the current quote        → '
    //   2. emit an escaped quote outside  → \'   (now backslash works)
    //   3. reopen a new quote             → '
    //
    // Example:  q("it's")  →  'it'\''s'
    // Shell sees: literal "it", escaped quote, literal "s". Total content:
    // the four characters i t ' s. Correct.
    private const val SQ_ESC = "'" + "\\" + "''"

    /**
     * Wrap [s] in single quotes so it survives shell interpolation as
     * exactly the literal string it is now.
     *
     * Guarantees:
     *   - spaces, $, `, \, ;, |, >, <, newlines — all become inert
     *   - the only transformation is: every ' in [s] becomes '\''
     *   - the output is safe to paste into `sh -c "..."` contexts
     *
     * Non-goals (deliberately):
     *   - Not for bash-specific arrays (we run sh, not bash)
     *   - Not a general "make this shell-safe" sanitizer for COMMAND
     *     structure — it quotes ONE argument. Building "cmd $A; cmd $B"
     *     with q() on A and B still requires YOU to decide the ';' is
     *     wanted. q() makes data inert; it doesn't bless control flow.
     */
    fun q(s: String): String = SQ + s.replace(SQ, SQ_ESC) + SQ
}

// ----------------------------------------------------------------------------
// WHY NOT alternatives:
//
//   - Double quotes ("$s"): still expands $VARS and backticks inside.
//     q("$(rm -rf /)") in double quotes = still executes. Fatal flaw.
//
//   - Escaping each special char individually (\$, \;, \| ...): it's a
//     whitelist you have to maintain forever, and forgetting ONE metachar
//     is a bug. The single-quote-wrapping approach handles EVERYTHING by
//     construction. One rule > thirty rules.
//
//   - Kotlin's ProcessBuilder-style argument arrays: Shizuku.newProcess
//     DOES accept an argv array (Phase 21 uses it). Arrays are injection-
//     proof BY CONSTRUCTION — no shell parsing at all. We still keep q()
//     because the commands we build are sometimes SINGLE shell strings
//     ("cd /dir && gradle build" needs the shell's &&). Where we can use
//     argv arrays, we should; where we need shell features, q() is the lock.
//     (TermuxBridge in Phase 41 currently sends "sh -c <string>" — that
//     string MUST be built with q() anywhere user/repo data appears.)
//
// TEST IT YOURSELF in Termux, one minute, do it:
//     s="it's a $test; tricky | string"
//     echo 'pm install -r '"$(printf '%s' "$s" | sed "s/'/'\\\\''/g" | sed "s/^/'/;s/$/'/")"
// ...or trust the unit test we'll write in M2. Either way, SEE it work once
// and you'll never hand-roll quoting again.
// ----------------------------------------------------------------------------
