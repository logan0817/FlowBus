# Espresso brings error_prone_annotations into the instrumentation APK. The
# annotation references this JDK compiler type, which is not needed at runtime.
-dontwarn javax.lang.model.element.Modifier

# AndroidJUnitRunner touches androidx.tracing during startup. Keep it in the
# minified instrumentation APK so release androidTest can start before tests run.
-keep class androidx.tracing.** { *; }
