// ============================================================================
// PocketCI — Constants.kt
// ----------------------------------------------------------------------------
// The one place "magic values" are allowed to live. The discipline:
// if a value is used in TWO+ places and changing it wrongly would break
// behavior (not just text), it goes here. If it's user-visible text, it
// goes in strings.xml (Phase 8). If it's build-time info (versionCode),
// it goes in build.gradle.kts (Phase 5). Three buckets, no ambiguity.
// ============================================================================

package com.pocketci.app.core

object Constants {

    // ------------------------------------------------------------------
    // THE WEBHOOK PORT — and its two failure modes you should know:
    //
    // Port 8080 is IN THE EPHEMERAL RANGE on some Linux kernels, and some
    // phones run local proxies/other dev tools that squat on it. So:
    //
    //   FAILURE MODE 1 — "bind failed: Address already in use":
    //     WebhookServer (Phase 31) throws at start(). Our planned handling:
    //     catch it, show a banner "Port busy — switch to polling mode".
    //     Polluting the UI with a crash would be amateur hour; a CI tool
    //     degrades gracefully BY DESIGN.
    //
    //   FAILURE MODE 2 — carrier NAT:
    //     Even with port 8080 open, GitHub webhooks CAN'T reach a phone on
    //     mobile data (CGNAT). This is why Phase 30's GitHubApi has the
    //     polling fallback, and why docs/SETUP.md says "LAN only for dev".
    //
    // No "user-configurable port" setting in v1 — that's option-paralysis
    // for a setting 1% of users need. Hardcoded + documented beats
    // configurable + undocumented.
    // ------------------------------------------------------------------
    const val WEBHOOK_PORT = 8080

    // Name of the per-run working directory created under filesDir by
    // RunWorker (Phase 45). Files here get wiped after each run succeeds —
    // named like a directory, referenced like a contract.
    const val WORKSPACE = "pocketci-work"

    // Notification channel ID for run progress. The CHANNEL is created
    // once (in CiService, Phase 46) and the ID is referenced here so the
    // service and any direct-notification code agree. Channel IDs are
    // stringly-typed API surface — exactly the kind of thing Constants
    // exists to pin.
    const val NOTIF_CHANNEL = "ci_runs"

    // Intent extra key: RunDetailActivity reads the run ID from its launch
    // intent using this exact key. Keeping it here means the sender
    // (RunsAdapter click, Phase 49) and the receiver (Phase 50) can't drift
    // apart silently. A typo in one of them = run detail always shows -1
    // = empty screen = "why is my list broken" debugging session.
    const val EXTRA_RUN_ID = "run_id"

    // Action string for an internal broadcast carrying a log line to the UI.
    // DEFINED NOW, USED IN M2 (log streaming). Declaring it early costs
    // nothing and documents the plan: RunContext.log() → AppLogger → file,
    // and for live updates also → broadcast with this action → RunDetailActivity
    // receiver. "Broadcast action" here is a plain string, not a manifest
    // permission-guarded intent — it's local-only (same-app components).
    const val BROADCAST_LOG = "com.pocketci.app.LOG"
}
