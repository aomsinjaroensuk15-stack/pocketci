// ============================================================================
// PocketCI — :app module build script
// ----------------------------------------------------------------------------
// This is THE file that builds the APK. Unlike the root build.gradle.kts
// (Phase 2, menu only), this one actually applies plugins and declares
// dependencies. Structure:
//   1. plugins      — what tools this module uses
//   2. android { }  — what kind of app this is (SDK levels, features)
//   3. dependencies — which libraries ship inside the APK
// ============================================================================

plugins {
    // These IDs match the versions pinned in the ROOT build.gradle.kts.
    // No `version` here — that's the whole point of the root menu file.
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")   // annotation processing: Room + Moshi codegen
}

// ---------------------------------------------------------------------------
// android { } — the identity of the app
// ---------------------------------------------------------------------------
android {
    // WHERE the R class and BuildConfig live. Every Kotlin file starts with
    // `package com.pocketci.app...` and this must match or the R class
    // "cannot be resolved" errors will haunt you. Changing this AFTER the
    // first release = new app identity on the Play Store (different
    // applicationId below is what actually matters for that, but keep
    // them consistent anyway — confusion here costs hours).
    namespace = "com.pocketci.app"

    // The SDK version we COMPILE against. 35 = Android 15. Rule: always use
    // the latest stable compileSdk, even if your minSdk is ancient — newer
    // compileSdk = newer lint checks + you can still guard runtime behavior
    // with Build.VERSION checks.
    compileSdk = 35

    defaultConfig {
        // THE app identity. Android treats this string as the primary key
        // of your app on a device and in stores. com.pocketci.app chosen
        // with aomsin's usual flavor — flip it to your GitHub handle before
        // publishing.
        applicationId = "com.pocketci.app"

        // minSdk = the OLDEST Android that can install this APK.
        // 26 = Android 8.0 Oreo (2017). Rationale:
        //   - Covers ~95%+ of active devices in 2026.
        //   - Below 26 you lose: adaptive icons, notification channels
        //     (we NEED channels for CI status notifs), Java 8 desugaring
        //     niceties, foreground service types.
        //   - Every minSdk bump below 26 costs real compatibility code.
        // Could we go 24? Sure, but we'd rewrite notification code for
        // nothing. 26 is the sweet spot for a modern utility app.
        minSdk = 26

        // targetSdk = "which Android version's RULES do we agree to play by."
        // NOT a limit on what devices can install — minSdk does that.
        // targetSdk 35 means: Android 15's permission, background-execution,
        // and foreground-service restrictions apply to us. Google REQUIRES
        // this for Play updates anyway; and for us it's honest: PocketCI
        // lives in the foreground-service world.
        targetSdk = 35

        versionCode = 1          // integer, must bump for every update
        versionName = "0.1.0"    // human-readable string, shown to users
    }

    buildTypes {
        // debug: default, signed with the auto debug keystore, no minify.
        // (not listed = defaults apply; fine for skeleton)

        release {
            // Shrinks + obfuscates. For a CI app we mostly want the size
            // win; the obfuscation is incidental. ProGuard/R8 needs our
            // rules file (Phase 6) or Shizuku reflection calls get renamed
            // into oblivion and silent installs break in mysterious ways.
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // NOTE: real release signing config comes in Phase 42-land
            // (ApkSigner signs CI OUTPUT; this block signs THE APP ITSELF
            // when you publish PocketCI). For now, debug-signed is fine.
        }
    }

    // viewBinding = auto-generates a typed binding class per layout XML.
    // activity_main.xml → ActivityMainBinding with .toolbar, .container
    // fields. This replaces findViewById — which is the #1 source of
    // NullPointerExceptions and typo crashes in student apps. On.
    buildFeatures { viewBinding = true }

    compileOptions {
        // Java 17 language level: records, sealed classes (via Kotlin we
        // get most of this free, but the Android runtime still compiles
        // java interop at this level). AGP 8.x defaults here anyway; we
        // pin it so behavior is identical on Termux builds.
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

// ---------------------------------------------------------------------------
// dependencies — what ships inside the APK
// ---------------------------------------------------------------------------
// Grouped by layer, matching the architecture docs. Each line = one line
// of justification. Versions chosen for mutual compatibility as of
// late 2026 — bump one, check the others.
dependencies {
    // ===== AndroidX foundation =====
    // Kotlin extensions for Context, Lifecycle, coroutines glue on Android.
    implementation("androidx.core:core-ktx:1.15.0")
    // AppCompatActivity — our base activity class; gives us the action bar,
    // themes, and back-compat behavior we rely on in MainActivity.
    implementation("androidx.appcompat:appcompat:1.7.0")
    // Material Components: MaterialToolbar, the look of Settings screens.
    implementation("com.google.android.material:material:1.12.0")
    // ConstraintLayout — not used much yet, but standard to include for
    // the settings/detail screens we'll build in M6.
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    // Lifecycle runtime for ViewModels + StateFlow collection in activities.
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // ===== Background execution =====
    // WorkManager: guaranteed, battery-aware background work with retries —
    // our run queue (Phase 44-45) is built on it. Survives reboots, doze,
    // app kills. THE backbone of "phone as CI runner".
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // ===== Persistence =====
    // Room: SQLite ORM for run history + logs (Phase 24-26). runtime + ktx
    // (coroutine DAO support: `suspend fun` queries) + compiler via KSP.
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ===== Network =====
    // OkHttp: HTTP client for GitHub API + webhook-free polling. We use
    // synchronous calls inside coroutines/IO dispatcher on purpose — simple
    // to reason about, easy to swap for MockWebServer tests later.
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ===== JSON =====
    // Moshi: JSON ↔ Kotlin data classes. Codegen (not reflection) via KSP:
    // reflection = slower startup + ProGuard pain; codegen = plain classes
    // generated at build time. KotlinJsonAdapterFactory covers any class
    // we forgot to annotate without failing the build.
    implementation("com.squareup.moshi:moshi:1.15.2")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.2")

    // ===== YAML =====
    // snakeyaml: parses pocketci.yml pipeline definitions (Phase 33).
    // Android ships no YAML parser. Version 2.x closes a billion CVEs from
    // the 1.x era — never use 1.x, even if a tutorial says so.
    implementation("org.yaml:snakeyaml:2.3")

    // ===== Embedded HTTP server =====
    // NanoHTTPD: ~50 KB single-jar web server. Our webhook receiver
    // (Phase 31) is just NanoHTTPD listening on :8080/hook. Alternatives
    // (Ktor server, Netty) are 100× heavier — for "receive one POST and
    // enqueue it", NanoHTTPD is exactly enough.
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    // ===== Git =====
    // JGit: pure-Java Git implementation. Clones repos WITHOUT shelling out
    // to a `git` binary (Termux has git, but APK-size and PATH issues make
    // a bundled implementation more reliable). This is the second-biggest
    // dependency (~2 MB) — acceptable for a CI app; unacceptable for a
    // calculator. Trade-offs are context-dependent.
    implementation("org.eclipse.jgit:org.eclipse.jgit:6.10.0.202406032230-r")

    // ===== Shizuku — THE point of this app =====
    // api    : Shizuku binder client (Shizuku.newProcess, permission checks).
    // provider: optional ContentProvider helpers for some ops.
    implementation("com.github.RikkaApps.Shizuku:api:13.1.5")
    implementation("com.github.RikkaApps.Shizuku:provider:13.1.5")
}
