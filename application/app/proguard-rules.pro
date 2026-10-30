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

# Nağmedar (1.0): the playback service finds Android Auto's catalog by name
-keep class app.nagmedar.ui.auto.CarCatalogObserver { <init>(); }

# ---------------------------------------------------------------- Nağmedar 1.0: R8 on release builds
# Names stay as written. A crash on the devices reads without a mapping file (none leaves CI: the
# personal build's only output is its ciphertext), and what the debug build it replaces wrote by
# name (WorkManager's worker classes, Moshi's JSON fields, Parcelables in pending alarms) reads the
# same. R8 still removes, inlines and optimizes; the rules below keep what it cannot see used.
-dontobfuscate

# libnagmedar_audio.so (native/audio/audio_jni.c): NativeAudio's natives are bound by their JNI
# names; the engine's events and the converter's progress are called back by method name
-keep class app.nagmedar.audio.NativeAudio { native <methods>; }
-keep interface app.nagmedar.audio.NativeAudio$Listener { *; }
-keep interface app.nagmedar.audio.NativeAudio$ConvertListener { *; }
-keepclassmembers class * implements app.nagmedar.audio.NativeAudio$Listener { *; }
-keepclassmembers class * implements app.nagmedar.audio.NativeAudio$ConvertListener { *; }

# libnagmedar_lab.so (native/lab): NativeLab's natives (the media probe, the USB audio tools) and
# its progress callback, by name
-keep class app.nagmedar.lab.NativeLab { native <methods>; }
-keep interface app.nagmedar.lab.NativeLab$Progress { *; }
-keepclassmembers class * implements app.nagmedar.lab.NativeLab$Progress { *; }

# WorkManager makes its workers by class name, from the rows its database keeps across updates
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Moshi 1.8 without code generation reads and writes fields by name: the settings backup and the
# equalizers' export (files the debug build may have written), the remote access server's JSON
# (the web page's, and its sessions in the preferences), the licences, the locales, Moviepedia's
-keepclassmembers class org.videolan.vlc.gui.preferences.search.SettingsBackup { <fields>; }
-keepclassmembers class org.videolan.vlc.gui.preferences.search.SettingEntry { <fields>; }
-keepclassmembers class org.videolan.vlc.util.EqualizerExport { <fields>; }
-keepclassmembers class org.videolan.vlc.util.EqualizerState { <fields>; }
-keepclassmembers class org.videolan.vlc.mediadb.models.** { <fields>; }
-keepclassmembers class org.videolan.vlc.util.LocaleUtil$VLCLocale { <fields>; }
-keepclassmembers class org.videolan.vlc.gui.Licenses { <fields>; }
-keepclassmembers class org.videolan.vlc.gui.Library { <fields>; }
-keepclassmembers class org.videolan.vlc.gui.LibraryWithLicense { <fields>; }
-keepclassmembers class org.videolan.vlc.remoteaccessserver.** { <fields>; }
-keepclassmembers class org.videolan.moviepedia.models.** { <fields>; }
# its enum adapter looks each constant's field up by the constant's name
-keepclassmembers enum org.videolan.** { <fields>; }

# Retrofit (Moviepedia) under R8's full mode: the service interfaces' generic return types
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>

# Ktor's Netty engine (the remote access server, S-1304) makes its channels from their classes and
# reaches fields through Unsafe and field updaters by name
-keep class io.netty.** { *; }
-keepclassmembers class io.ktor.** { volatile <fields>; }

# Serializable classes keep what Java serialization reads by name
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
