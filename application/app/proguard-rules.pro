# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Uncomment this to preserve the line number information for
# debugging stack traces.
-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-dontpreverify
-dontwarn **
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*,!code/allocation/variable

# Do not optimize/shrink LibVLC, because of native code
-keep class org.videolan.libvlc.** { *; }
# Same for MediaLibrary
-keep class org.videolan.medialibrary.** { *; }

-keep public class * extends android.app.Activity {
    public <methods>;
}
-keep public class * extends android.app.Fragment {
    public <methods>;
}
-keep public class * extends androidx.fragment.app.Fragment {
    public <methods>;
}
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class com.android.vending.licensing.ILicensingService

-keepclasseswithmembernames class * {
    native <methods>;
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keepclassmembers class * extends android.app.Activity {
   public void *(android.view.View);
}

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

-keep class * implements android.os.Parcelable {
  public static final android.os.Parcelable$Creator *;
}

# support design
-dontwarn android.support.design.**
-keep class android.support.design.** { *; }
-keep interface android.support.design.** { *; }
-keep public class android.support.design.R$* { *; }

-dontwarn android.support.**

# Kotlinx
-keep class kotlinx.coroutines.internal.MainDispatcherFactory { *; }
-keep class kotlinx.coroutines.android.AndroidExceptionPreHandler { *; }
-keep class kotlinx.coroutines.CoroutineExceptionHandler { *; }
-keep class kotlinx.coroutines.android.AndroidDispatcherFactory { *; }
-keep class kotlin.Result { *; }
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
# Retrofit
# Retrofit does reflection on generic parameters. InnerClasses is required to use Signature and
# EnclosingMethod is required to use InnerClasses.
-keepattributes Signature, InnerClasses, EnclosingMethod

# Retrofit does reflection on method and parameter annotations.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# Retain service method parameters when optimizing.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Ignore annotation used for build tooling.
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement

# Ignore JSR 305 annotations for embedding nullability information.
-dontwarn javax.annotation.**

# Guarded by a NoClassDefFoundError try/catch and only used when on the classpath.
-dontwarn kotlin.Unit

# Top-level functions that can only be used by Kotlin.
-dontwarn retrofit2.-KotlinExtensions

-keep class com.google.android.material.** { *; }

# Nağmedar (S-808): the playback service finds the play statistics' recorder by name
-keep class app.nagmedar.ui.stats.PlayStatsRecorder { <init>(); }

# Nağmedar (S-1009): the playback service finds the album covers' observer by name
-keep class app.nagmedar.ui.covers.CoverObserver { <init>(); }

# Nağmedar (S-613): the playback service finds the latency profiles' observer by name
-keep class app.nagmedar.ui.audio.latency.LatencyObserver { <init>(); }

# Nağmedar (S-1301): the playback service finds the network sign-ins' observer by name
-keep class app.nagmedar.ui.network.NetworkSignInObserver { <init>(); }

# Nağmedar (S-1104): the playback service finds the podcast episodes' observer by name
-keep class app.nagmedar.ui.podcast.PodcastPlaybackObserver { <init>(); }

# Nağmedar: the playback service finds the home screen widget's observer by name
-keep class app.nagmedar.ui.widget.PlayerWidgetObserver { <init>(); }

# Nağmedar (S-709): libnagmedar_lab.so calls the transcription's listener by its methods' names
-keep interface app.nagmedar.lab.NativeLab$Transcription { *; }
-keepclassmembers class * implements app.nagmedar.lab.NativeLab$Transcription { *; }

# Nağmedar (S-1206): libnagmedar_lab.so asks the remux's listener how to go on by its method's name
-keep interface app.nagmedar.lab.NativeLab$RemuxProgress { *; }
-keepclassmembers class * implements app.nagmedar.lab.NativeLab$RemuxProgress { *; }
# Nağmedar: libnagmedar_lab.so calls the embedded subtitles' extraction listener by its method's name
-keep interface app.nagmedar.lab.NativeLab$SubtitleExtraction { *; }
-keepclassmembers class * implements app.nagmedar.lab.NativeLab$SubtitleExtraction { *; }
