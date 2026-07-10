# Retrofit + kotlinx.serialization models
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Keep serializer lookups for our API models
-keep,includedescriptorclasses class com.sparkora.app.data.api.**$$serializer { *; }
-keepclassmembers class com.sparkora.app.data.api.** {
    *** Companion;
}
-keepclasseswithmembers class com.sparkora.app.data.api.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Retrofit
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
