# ---- Bokeya ProGuard / R8 rules ----

# Strip all logging from release builds (privacy: no financial data in logcat).
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.shohan.bokeya.**$$serializer { *; }
-keepclassmembers class com.shohan.bokeya.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class com.shohan.bokeya.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# WorkManager workers instantiated reflectively
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.CoroutineWorker
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# Enum values used by Room type converters
-keepclassmembers enum com.shohan.bokeya.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Compose
-dontwarn androidx.compose.**
