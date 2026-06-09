# Jellyfin SDK – keep all model classes used by kotlinx.serialization
-keep class org.jellyfin.sdk.model.** { *; }
-keepclassmembers class org.jellyfin.sdk.model.** { *; }

# Media3 / ExoPlayer
-keep class androidx.media3.** { *; }

# Keep Kotlin coroutine internals
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
