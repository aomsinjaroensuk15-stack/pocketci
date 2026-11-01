// ============================================================================
// PocketCI — PocketCiApp.kt
// ----------------------------------------------------------------------------
// The Application class: created ONCE per process, before ANY activity,
// service, or worker. This is where the app's "plumbing" lives.
//
// Why it exists as a class at all: without android:name=".PocketCiApp" in
// the manifest (Phase 7), Android instantiates the default Application —
// no custom onCreate, no db, no settings. Everything else in the app then
// needs those two objects, so this class IS the dependency root.
//
// THE DESIGN DECISION you're looking at: manual DI (dependency injection)
// via a singleton, NOT Hilt/Koin. Reasons, honestly:
//   - Hilt adds KSP processors + a component tree + @HiltAndroidApp ceremony.
//     For 2 singletons, that's a framework tax of ~500 lines of concepts.
//   - A CI tool should have a boring, obvious startup path. When a build
//     fails at 2 AM, "open PocketCiApp.kt, see exactly what's created" is
//     worth more than fancy injection graphs.
//   - If PocketCI grows to 5+ singletons (it might in M5), we reconsider.
//     The refactor cost is one constructor pass — cheap because everything
//     already goes through this one class.
// ============================================================================

package com.pocketci.app

import android.app.Application
import com.pocketci.app.core.AppLogger
import com.pocketci.app.data.db.CiDatabase
import com.pocketci.app.data.prefs.SettingsStore

class PocketCiApp : Application() {

    // ------------------------------------------------------------------
    // lateinit + private set: the "create once in onCreate, read-only after"
    // pattern. lateinit lets us declare non-null WITHOUT initializing at
    // construction time — because we genuinely can't build the database
    // until we have a Context, and the constructor has none.
    // `private set` = only THIS class can reassign; everyone else reads.
    // Anyone writing `app.db = ...` elsewhere gets a compile error.
    // ------------------------------------------------------------------
    lateinit var db: CiDatabase
        private set

    lateinit var settings: SettingsStore
        private set

    override fun onCreate() {
        super.onCreate()   // ALWAYS first. Framework bookkeeping
                           // (like multi-dex install) happens there.

        // Order matters and is deliberate:
        // 1. instance    — other phases read it via companion object; if
        //                  we crash before setting it, the crash itself is
        //                  the useful signal (fail fast).
        // 2. AppLogger   — filesDir-based log sink (Phase 18). Nothing
        //                  else logs until this exists.
        // 3. db          — Room (Phase 26). Survives process death; run
        //                  history is the first thing a CI must not lose.
        // 4. settings    — SharedPreferences wrapper (Phase 27). Used by
        //                  GitHubApi and ReportStep; must exist before the
        //                  first webhook can arrive.
        instance = this
        AppLogger.init(this)
        db = CiDatabase.get(this)
        settings = SettingsStore(this)

        // NOT created here, on purpose:
        // - WebhookServer  — started on demand by MainActivity (M1), not at
        //   boot. Starting a server in Application.onCreate = battery drain
        //   + an open port before the user even opened the app.
        // - Shizuku hooks — Shizuku state changes at runtime; we query
        //   it fresh via ShizukuGuard (Phase 21) instead of caching.
    }

    companion object {
        // ------------------------------------------------------------------
        // THE MEMORY-LEAK TRAP, explained so you never write it:
        //
        //   companion object { lateinit var instance: PocketCiApp }
        //
        // ...is a static reference to the Application. Statics live for the
        // PROCESS LIFETIME. That's FINE here ONLY because Application IS
        // the process-lifetime object — it can never leak.
        //
        // The trap version students write:
        //   companion object { lateinit var instance: MainActivity }
        // which keeps the whole screen (and its View tree, bitmaps, ...)
        // alive forever after rotation. Rotating once = two Activities in
        // RAM. Ten rotations = memory pressure.
        //
        // RULE: static `instance` of Application = OK. Static `instance` of
        // anything with a View/Context that isn't the Application = leak.
        //
        // `private set` on the companion property keeps the discipline:
        // writes only from PocketCiApp.onCreate, reads everywhere via
        // PocketCiApp.instance.db.
        // ------------------------------------------------------------------
        lateinit var instance: PocketCiApp
            private set
    }
}
