# ============================================================================
# PocketCI — R8/ProGuard keep rules
# ----------------------------------------------------------------------------
# WHEN these rules matter: only for release builds (isMinifyEnabled = true in
# Phase 5). Debug builds skip R8 entirely, which is why obfuscation bugs
# love to hide until the exact moment you publish. 😤
#
# HOW to read this file: each `-keep` line is a bandage for a library that
# uses reflection. R8's whole job is renaming classes to shrink the APK;
# reflection looks classes up BY NAME at runtime, so renamed = "class not
# found" crashes in production. We tell R8: "these names are load-bearing,
# hands off."
# ============================================================================

# ---------------------------------------------------------------------------
# Shizuku — DO NOT REMOVE, DO NOT SIMPLIFY
# ---------------------------------------------------------------------------
# PocketCI is useless without Shizuku. Shizuku talks to its server app over
# an Android Binder using AIDL — and AIDL is reflection-adjacent: the
# client looks up the interface by name. If R8 renames `moe.shizuku.*`
# classes, one of two very cursed things happens:
#   - Cold crash: NoSuchMethodError / ClassNotFoundException on launch
#   - WORSE: the binder call silently fails and ShizukuGuard reports
#     "permission not granted" even when it IS granted — because the
#     exception got swallowed in binder marshalling. You'll debug
#     permissions for three days when it was ProGuard all along.
# `-keep class moe.shizuku.** { *; }` keeps the class AND all members.
-keep class moe.shizuku.** { *; }

# ---------------------------------------------------------------------------
# JGit — too big to audit, too reflection-heavy to shrink safely
# ---------------------------------------------------------------------------
# JGit (Phase 35 checkout step) parses your .git internals using class names
# from config, pack files, and refs. R8 would strip or rename swaths of it,
# and JGit's own ProGuard metadata isn't shipped with the artifact. The
# pragmatic student answer: `-dontwarn` its transitive friends (slf4j is its
# logging API — we don't ship a binding, and JGit only warns about it),
# and let R8 shrink OUR code while leaving JGit whole. APK grows ~1 MB.
# Acceptable: this is a CI tool, not a 4 MB budget game.
-dontwarn org.eclipse.jgit.**
-dontwarn org.slf4j.**

# ---------------------------------------------------------------------------
# Why no -keep rules for Room / Moshi / NanoHTTPD?
# ---------------------------------------------------------------------------
# Because they ship their own consumer ProGuard rules INSIDE the AAR.
# When you write implementation("androidx.room:room-runtime..."), the
# artifact carries room-runtime/META-INF/proguard/... which AGP merges into
# the final R8 config automatically. Same for Moshi and NanoHTTPD. This is
# why we use the KSP codegen (Phase 5) instead of reflection-based adapters:
# codegen generates REAL classes that R8 understands natively, so the
# keep-rules are tiny and auto-shipped. Hand-written -keep for them would
# be cargo cult.
#
# The ONE exception you'll add later: when Phase 42's ApkSigner uses
# com.android.apksig, it may need:
#   -keep class com.android.apksig.** { *; }
# Add it when we wire signing, not before — dead rules rot.

# ---------------------------------------------------------------------------
# Debug tip that will save you an evening
# ---------------------------------------------------------------------------
# If a release build crashes where debug works, build a minified-but-
# readable APK once to diff:
#   ./gradlew assembleRelease -Pandroid.enableR8.fullMode=false
# and check mapping.txt in app/build/outputs/mapping/release/ — it shows
# what every class got renamed TO, so stack traces become readable again.
