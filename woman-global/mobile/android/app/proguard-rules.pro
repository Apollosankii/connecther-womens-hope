# ConnectHer release ProGuard / R8 rules

# Keep line numbers for crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Required for Gson TypeToken<List<...>>() {} anonymous subclasses under R8
-keepattributes Signature, InnerClasses, EnclosingMethod

# ---- Firebase / Google ----
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ---- Supabase / Ktor / kotlinx.serialization ----
-keep class io.github.jan.supabase.** { *; }
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
-keepclassmembers class * {
    @kotlinx.serialization.Serializable *;
}
-keep @kotlinx.serialization.Serializable class * { *; }
-keep class kotlinx.serialization.** { *; }
-dontwarn kotlinx.serialization.**

# ---- Paystack ----
-keep class com.paystack.** { *; }
-dontwarn com.paystack.**

# ---- Gson (offline cache, HomeFragment, EmergencyHelper) ----
# TypeToken anonymous subclasses must keep generic signatures (else: IllegalStateException at runtime).
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ---- App data classes used with Gson / Serialization ----
-keep class com.womanglobal.connecther.data.** { *; }
-keep class com.womanglobal.connecther.services.** { *; }
-keep class com.womanglobal.connecther.supabase.SupabaseData$* { *; }

# ---- OkHttp ----
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }

# ---- Glide ----
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule { <init>(...); }
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# ---- Material CalendarView ----
-keep class com.prolificinteractive.materialcalendarview.** { *; }

# ---- pg_net / multiplatform-settings (transitive from Supabase) ----
-dontwarn com.russhwolf.settings.**
