# ========== qMusic ProGuard/R8 规则 ==========

# ---------- kotlinx.serialization ----------
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keep,includedescriptorclasses class com.qmusic.data.model.**$$serializer { *; }
-keepclassmembers class com.qmusic.data.model.** {
    *** Companion;
}
-keepclasseswithmembers class com.qmusic.data.model.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ---------- Retrofit / OkHttp / Okio ----------
-keepattributes Signature, InnerClasses, EnclosingMethod, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-dontwarn okhttp3.**
-dontwarn okio.**

# ---------- Coil ----------
-dontwarn coil.**
-keep class coil.decode.DecodeUtils { *; }

# ---------- Room ----------
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# ---------- Media3 / ExoPlayer ----------
-dontwarn androidx.media3.**

# ---------- 应用入口与前台服务 ----------
-keep class com.qmusic.MainActivity { *; }
-keep class com.qmusic.QMusicApp { *; }
-keep class com.qmusic.player.PlayerService { *; }
-keep class com.qmusic.player.WidgetPlayerProvider { *; }