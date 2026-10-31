# Room entities are accessed by generated code only.
-keep class com.example.offlinequiz.data.entity.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { <init>(); }

# Gson models (question import DTOs)
-keep class com.example.offlinequiz.importer.dto.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Gson generic reflection
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken

-dontwarn okio.**
-dontwarn javax.annotation.**
