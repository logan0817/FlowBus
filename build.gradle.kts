import org.gradle.api.tasks.compile.JavaCompile

plugins {
    id("com.android.application") apply false
    id("com.android.library") apply false
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.vanniktech.maven.publish) apply false
    base
}

apiValidation {
    ignoredProjects.add("app")
    ignoredClasses.add("com.logan.flowbus.BuildConfig")
}

allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-Xlint:-options")
    }
}

apply(from = "gradle/release-publishing.gradle.kts")
apply(from = "gradle/release-verification.gradle.kts")
