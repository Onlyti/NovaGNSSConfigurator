# Keep kotlinx-serialization generated serializers.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class com.onlyti.novagnss.** {
    *** Companion;
}
-keepclasseswithmembers class com.onlyti.novagnss.** {
    kotlinx.serialization.KSerializer serializer(...);
}
