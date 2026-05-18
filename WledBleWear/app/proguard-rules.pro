# Kotlin serialization — keep @Serializable data classes
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.jpchurchouse.wledblewear.model.**$$serializer { *; }
-keepclassmembers class com.jpchurchouse.wledblewear.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.jpchurchouse.wledblewear.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Keep model package (used via reflection by serialization)
-keep class com.jpchurchouse.wledblewear.model.** { *; }

# Wear Tiles & ProtoLayout (library consumer rules usually cover this, belt-and-braces)
-keep class androidx.wear.tiles.** { *; }
-keep class androidx.wear.protolayout.** { *; }
