# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# SLF4J warnings
-dontwarn org.slf4j.impl.StaticMDCBinder
-dontwarn org.slf4j.impl.StaticLoggerBinder

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# Keep your app classes
-keep class org.mountaincircles.app.** { *; }

# Keep data classes and their properties
-keep class kotlinx.serialization.** { *; }
-keep class kotlin.reflect.** { *; }

# Keep Compose runtime classes
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**

# Keep MapLibre classes
-keep class org.maplibre.** { *; }
-dontwarn org.maplibre.**

# Keep activity classes
-keep class androidx.activity.** { *; }

# Keep datastore classes
-keep class androidx.datastore.** { *; }

# Keep coroutine classes
-keep class kotlinx.coroutines.** { *; }

# Keep Ktor classes
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# Keep Kotlin datetime classes
-keep class kotlinx.datetime.** { *; }

# Keep lifecycle classes
-keep class androidx.lifecycle.** { *; }

# Keep view model classes
-keep class androidx.lifecycle.ViewModel
-keep class * extends androidx.lifecycle.ViewModel { *; }

# Remove ALL logging calls in release builds
-assumenosideeffects class ** {
    *** log(...);
    *** debug(...);
    *** info(...);
    *** warn(...);
    *** error(...);
    *** println(...);
    void printStackTrace();
}

# Keep the Logger class but remove its methods
-keep class org.mountaincircles.app.logger.Logger {
    <init>();
    <fields>;
}
-keep class org.mountaincircles.app.logger.LogConfig {
    <init>();
    <fields>;
}

# Keep all classes that are referenced in the manifest
-keep class * extends android.app.Application
-keep class * extends android.app.Activity
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.content.ContentProvider

# Keep custom views and their constructors
-keep class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# Keep parcelable classes
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# Keep enum classes
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Keep annotations
-keepattributes *Annotation*,Signature,Exceptions,InnerClasses

# Keep source file and line number information for debugging
-keepattributes SourceFile,LineNumberTable

# Avoid obfuscating package names
-adaptresourcefilenames **.properties,**.xml,**.json
-adaptresourcefilecontents **.properties,**.xml

# Keep native method names
-keepclasseswithmembernames class * {
    native <methods>;
}
