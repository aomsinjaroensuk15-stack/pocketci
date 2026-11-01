// ============================================================================
// PocketCI — ROOT build.gradle.kts
// ----------------------------------------------------------------------------
// What this file is:
//   The "menu card" for the whole project. It declares which PLUGIN VERSIONS
//   exist here. It does NOT compile anything, does NOT apply anything to the
//   root project, does NOT contain dependencies.
//
// What this file is NOT:
//   The app build script. That's app/build.gradle.kts (Phase 5).
//
// The mental model:
//   settings.gradle.kts  = "this house exists, here's the front door"
//   build.gradle.kts     = "here are the tools available in this house"
//   app/build.gradle.kts = "here is how we build THE APP with those tools"
// ============================================================================

plugins {
    // ------------------------------------------------------------------
    // 1. Android Gradle Plugin (AGP)
    // ------------------------------------------------------------------
    // WHAT: Turns Kotlin + XML + resources into a real APK. Also provides
    //       the `android { }` block you'll see in Phase 5.
    //
    // VERSION CHOICE: 8.7.3
    //   - 8.7.x targets compileSdk 35 (Android 15) — the current stable.
    //   - Pairs cleanly with Android Studio Ladybug / Hedgehog patches.
    //   - The ".3" is a bugfix bump; for a new project, always take the
    //     latest patch of the minor you target.
    //
    // RULE: AGP version ↔ Android Studio version are best friends. If you
    //       ever open this in an old Studio and see "plugin too new",
    //       downgrade here, not your IDE.
    //
    // `apply false` — CRITICAL:
    //   The root project is NOT an Android app. Applying the Android plugin
    //   here makes Gradle look for an android { } block that doesn't exist
    //   and fail with a cryptic error. `apply false` means "register this
    //   plugin + version in the catalog, but let the :app module actually
    //   apply it." Same story for the two below.
    id("com.android.application") version "8.7.3" apply false

    // ------------------------------------------------------------------
    // 2. Kotlin Android plugin
    // ------------------------------------------------------------------
    // WHAT: Compiles .kt files to bytecode, wires Kotlin into the Android
    //       build (Kotlin-specific lint, kapt/KSP integration, stdlib).
    //
    // VERSION CHOICE: 2.0.21
    //   - 2.0.x is the K2 compiler generation: significantly faster builds,
    //     better type inference. As a student, you want the modern one —
    //     tutorials are starting to assume K2.
    //   - 2.0.21 is a stable patch release; avoid .0 releases for new work.
    //
    // NOTE: If you ever bump Kotlin, KSP must bump in LOCKSTEP (see #3).
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false

    // ------------------------------------------------------------------
    // 3. KSP — Kotlin Symbol Processing
    // ------------------------------------------------------------------
    // WHAT: An annotation processor engine. Two of our libraries NEED it:
    //   - Room (Phase 24-26): generates database implementation code from
    //     our @Entity / @Dao interfaces at compile time.
    //   - Moshi (Phase 28-30): generates JSON adapters from our data classes.
    // Without KSP, both libraries fall back to reflection → slower runtime,
    // bigger APK, and ProGuard/R8 headaches in release builds.
    //
    // VERSION CHOICE: 2.0.21-1.0.28
    //   The version FORMAT is: "<kotlin version>-<ksp build number>".
    //   So "2.0.21-1.0.28" means "KSP 1.0.28, built for Kotlin 2.0.21".
    //   Mismatched KSP vs Kotlin = build errors that look like alien glyphs.
    //   When you bump Kotlin to 2.0.22, you look up the matching KSP build
    //   on the KSP releases page and update both here and in Phase 5.
    //
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
}

// ---------------------------------------------------------------------------
// Why no `repositories { }` block here?
// ---------------------------------------------------------------------------
// Phase 1 (settings.gradle.kts) owns all repositories, and
// `repositoriesMode = FAIL_ON_PROJECT_REPOS` forbids declaring them anywhere
// else — including this file. If you ever see a tutorial that puts
// repositories in the root build.gradle.kts, that's pre-Gradle-7 style.
// Don't copy it; the build will fail loudly (which is good, the flag is
// doing its job).

// ---------------------------------------------------------------------------
// Why no `allprojects { }` / `subprojects { }` blocks?
// ---------------------------------------------------------------------------
// Because we have exactly ONE module (:app) for now. Centralizing config
// across modules is tempting, but it hides where settings come from. When
// we split modules later (Phase M5+), we'll evaluate convention plugins.
// Until then: everything lives in the module that uses it. Boring = good.

// ---------------------------------------------------------------------------
// TERMUX NOTE
// ---------------------------------------------------------------------------
// The first time you run `./gradlew` in Termux, the Gradle wrapper
// (gradlew + gradle/wrapper/*) will download ~130 MB of Gradle 8.9+.
// Make sure you're on Wi-Fi, and run it with:
//     ./gradlew help --no-daemon
// just to verify the plumbing before trusting a full build. If the wrapper
// complains about JAVA_HOME, do:
//     pkg install openjdk-17
//     export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
