# VidPlay release rules.

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Google Mobile Ads (Next-Gen) + UMP. The AARs ship consumer rules, but the
# native view registration relies on reflection-like lookups, so keep the SDK
# surface untouched in release builds.
-keep class com.google.android.libraries.ads.mobile.sdk.** { *; }
-keep class com.google.android.ump.** { *; }
-dontwarn com.google.android.libraries.ads.mobile.sdk.**
-dontwarn com.google.android.ump.**

# NextLib FFmpeg renderers and JNI entry points.
-keep class io.github.anilbeesetti.nextlib.** { *; }
-dontwarn io.github.anilbeesetti.nextlib.**

# Room generated code.
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**

# WorkManager workers are instantiated by reflection.
-keep class com.vidplay.app.data.vault.VaultImportWorker { <init>(...); }
-keep class com.vidplay.app.data.vault.VaultExportWorker { <init>(...); }

# Kotlin metadata used by reflection in a few libraries.
-keepattributes *Annotation*, InnerClasses, Signature, Exceptions
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep media session / notification models.
-keep class androidx.media3.session.** { *; }
