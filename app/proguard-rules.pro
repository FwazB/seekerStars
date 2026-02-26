# ── Epoch Defenders ProGuard/R8 Rules ─────────────────────────────────
# Compose and CameraX AARs bundle their own R8 rules — no broad keeps needed.
# Engine classes are all direct-call (no reflection/JNI) — R8-safe.

# ── Crash reports ──────────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Kotlin Coroutines ──────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# ── Android components (explicit for safety, AAPT also generates) ─────
-keep class com.epochdefenders.MainActivity { *; }

# ── MediaPipe (AAR bundles no consumer rules — JNI loads classes by name) ──
-keep class com.google.mediapipe.** { *; }

# ── Strip Log calls in release (R8 optimization) ─────────────────────
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
