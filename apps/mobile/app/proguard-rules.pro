# Keep the youtubedl / media3 entry points referenced from the manifest.
-keep class com.yausername.** { *; }
-keep class androidx.media3.** { *; }
-dontwarn com.yausername.**

# NewPipe builds its services through reflection and ships its own downloaders.
-keep class org.schabi.newpipe.** { *; }
-keep class org.jsoup.** { *; }
-keep class org.mozilla.javascript.** { *; }
-dontwarn org.schabi.**
-dontwarn org.jsoup.**
-dontwarn org.mozilla.javascript.**
-dontwarn javax.annotation.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**

# Annotations are read at runtime by Room and Media3.
-keepattributes *Annotation*, InnerClasses, Signature
