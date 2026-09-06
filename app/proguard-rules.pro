# Proguard rules for SongPad

# Keep Oboe JNI exports
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep Room generated classes
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Media3 classes
-keep class androidx.media3.** { *; }
