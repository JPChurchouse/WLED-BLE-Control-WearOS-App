# Keep kotlinx.serialization models (BLE preset JSON parsing)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.** { kotlinx.serialization.KSerializer serializer(...); }

# Keep our data models
-keep class com.jpchurchouse.wledble.model.** { *; }