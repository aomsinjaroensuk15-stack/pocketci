// ============================================================================
// PocketCI — settings.gradle.kts
// This is the FIRST file Gradle reads. Before any module builds, Gradle
// needs to know: (1) where to find plugins, (2) where to find libraries,
// (3) what the project is even called. That's all this file does.
// Think of it as the front door of the house — no furniture yet, but
// nothing else exists without it.
// ============================================================================

// ---------------------------------------------------------------------------
// PLUGIN MANAGEMENT
// ---------------------------------------------------------------------------
// When you write `plugins { id("com.android.application") version "8.7.3" }`
// in a module, Gradle asks: "8.7.3 of WHAT, and from WHERE?" This block
// answers that. It runs before everything, including the root build.gradle.kts.
pluginManagement {
    repositories {
        // Google's Maven repo: Android Gradle Plugin (AGP), androidx libraries,
        // the emulator images metadata, etc. Always list this first — AGP is
        // ONLY published here, and putting google() first avoids Maven Central
        // returning a stale "not found" cache entry for Google artifacts.
        google()

        // Maven Central: Kotlin plugin, KSP, OkHttp, Moshi, Room, JGit,
        // NanoHTTPD, Shizuku (RikkaApps publishes to Central), snakeyaml...
        // Basically 95% of our third-party dependencies live here.
        mavenCentral()

        // Gradle Plugin Portal: fallback for plugins that aren't on either
        // of the above (e.g. some community Gradle plugins). We don't strictly
        // need it today, but removing it costs nothing and saves pain the day
        // we add a random Gradle plugin from the internet.
        gradlePluginPortal()

        // NOT listed: jitpack.io. If Shizuku resolution ever fails, add
        //     maven("https://jitpack.io")
        // as a last resort here. JitPack builds artifacts on demand and is
        // notoriously flaky — treat it as a lifeboat, not a dock.
    }

    // NOTE: no plugin version resolutions needed here yet. If two modules ever
    // disagree on a plugin version, you can pin it like:
    //     resolutionStrategy {
    //         eachPlugin {
    //             if (requested.id.id == "com.google.devtools.ksp") {
    //                 useVersion("2.0.21-1.0.28")
    //             }
    //         }
    //     }
}

// ---------------------------------------------------------------------------
// DEPENDENCY RESOLUTION MANAGEMENT
// ---------------------------------------------------------------------------
// This block controls where MODULES (like :app) look for their `dependencies { }`.
// It's separate from pluginManagement because plugins and libraries have
// different trust requirements.
dependencyResolutionManagement {
    // This is the strictest mode and we want it:
    //   - FAIL_ON_PROJECT_REPOS = "if any module declares its own repositories {},
    //     STOP THE BUILD with an error."
    //
    // Why so strict? Because a library you add in Phase 5+ (some random
    // GitHub library) might sneak in `repositories { maven("https://shady.io") }`
    // in its own .gradle file. Without this flag, Gradle silently allows it
    // and you're now building with code from an untrusted server.
    // With it, you get a loud error at sync time and you consciously decide
    // what to trust. For a CI app that RUNS OTHER PEOPLE'S BUILD SCRIPTS
    // (that's literally PocketCI's job), this paranoia is not optional.
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        mavenCentral()
        // Same jitpack note as above applies here.
    }
}

// ---------------------------------------------------------------------------
// PROJECT IDENTITY
// ---------------------------------------------------------------------------
// The name of the root project. This shows up in:
//   - Android Studio's window title
//   - the output APK naming schemes if we ever customize them
//   - Gradle's own logging ("Configure project :PocketCI")
// Purely cosmetic, but a nice detail for screenshots of your portfolio. 🚀
rootProject.name = "PocketCI"

// The one and only module. When we split off a `:core` or `:runner` module
// someday (Phase M5+), it gets its own `include(":core")` line here.
include(":app")

// ---------------------------------------------------------------------------
// TERMUX BUILD NOTE (save yourself an hour later)
// ---------------------------------------------------------------------------
// When you run `./gradlew` inside Termux, the wrapper downloads a full JDK-
// compatible distribution if ANDROID_HOME isn't set. On device that path is
// usually ~/android-sdk (from the command line tools zip). You'll set:
//   export ANDROID_HOME=$HOME/android-sdk
// in your Termux session. Gradle doesn't know or care — this file just needs
// the repos to be right, which they are.
