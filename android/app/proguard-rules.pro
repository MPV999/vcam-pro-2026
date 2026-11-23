-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,Exceptions

# --- VCAM SDK ---
-keep class com.vcampro.sdk.** { *; }
-keepclassmembers class com.vcampro.sdk.** { *; }

# --- Room ---
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

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

# --- DataStore / Proto ---
-dontwarn androidx.datastore.**
-keep class androidx.datastore.** { *; }
