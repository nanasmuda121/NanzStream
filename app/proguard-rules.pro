# Proguard rules for NanzStream
-keepattributes *Annotation*
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keep class com.nanzstream.nanas.data.model.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**
