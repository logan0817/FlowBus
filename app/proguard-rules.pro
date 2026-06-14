# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# AndroidJUnitRunner starts inside the target release process and touches this
# exact tracing class before tests run. Keep the precise runtime entry point
# without preserving the whole AndroidX namespace.
-keep class androidx.tracing.Trace { *; }

# AndroidX Test storage uses Kotlin lazy helpers before test discovery. Keep the
# exact facade and lazy implementations needed by the runner startup path.
-keep class kotlin.LazyKt { *; }
-keep class kotlin.LazyKt__LazyJVMKt { *; }
-keep class kotlin.LazyKt__LazyKt { *; }
-keep class kotlin.Lazy { *; }
-keep class kotlin.LazyThreadSafetyMode { *; }
-keep class kotlin.SynchronizedLazyImpl { *; }
-keep class kotlin.SafePublicationLazyImpl { *; }
-keep class kotlin.UnsafeLazyImpl { *; }
-keep class kotlin.collections.** { *; }
-keep class kotlin.text.** { *; }

# Espresso Kotlin helpers call this reflection facade from the target release
# process. Preserve the KClass descriptor so app and androidTest APKs agree.
-keep,includedescriptorclasses class kotlin.jvm.internal.Reflection {
    public static kotlin.reflect.KClass getOrCreateKotlinClass(java.lang.Class);
}
-keep,includedescriptorclasses class kotlin.jvm.internal.ReflectionFactory {
    public kotlin.reflect.KClass getOrCreateKotlinClass(java.lang.Class);
}
-keep interface kotlin.reflect.KClass { *; }

# Espresso startup reflects on this listenablefuture type from the target
# release process. Keep the interface only instead of the whole Guava package.
-keep interface com.google.common.util.concurrent.ListenableFuture { *; }

# Release instrumentation test code references the target app R classes by
# original name. Keep them so minified release APKs remain testable.
-keep class **.R { *; }
-keep class **.R$* { *; }

# Release instrumentation references sample activities by original class name.
-keep class com.logan.flowbusapp.**Activity { *; }
