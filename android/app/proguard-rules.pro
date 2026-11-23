# Minification is currently disabled for release builds (see app/build.gradle.kts).
# These rules are kept so R8 can be re-enabled once verified on a real device.

-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,Exceptions

# --- VCAM SDK (public surface used by integrators) ---
-keep class com.vcampro.sdk.** { *; }
-keepclassmembers class com.vcampro.sdk.** { *; }

# --- Room: the generated <Database>_Impl is loaded by name ---
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# --- DataStore / protobuf-lite ---
-keep class androidx.datastore.** { *; }
-keep class androidx.datastore.preferences.protobuf.** { *; }
-dontwarn androidx.datastore.**

# --- WorkManager ---
-keep class * extends androidx.work.ListenableWorker { <init>(...); }
-keep class androidx.work.impl.WorkDatabase_Impl { *; }

# --- ML Kit (bundled face detection) ---
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_face.** { *; }
-keep class com.google.android.gms.internal.mlkit_vision_common.** { *; }
-dontwarn com.google.mlkit.**

# --- Media3 ---
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# --- CameraX ---
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# --- Kotlin coroutines ---
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
