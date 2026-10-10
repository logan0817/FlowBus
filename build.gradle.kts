import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

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
    tasks.withType<Test>().configureEach {
        // 仓库门禁直接读取文档与配置，必须将它们纳入输入，避免复用旧通过结果。
        inputs.files(rootProject.fileTree(rootProject.projectDir) {
            include("*.md", ".gitignore", ".gitmodules", "*.gradle.kts", "gradle.properties")
            include(".github/workflows/*.yml", "docs/**/*.md", "gradle/**")
            include("flowbus-core/build.gradle.kts", "flowbus-core/README*.md")
            include("library-android/build.gradle.kts", "library-android/README*.md")
            include("app/build.gradle.kts", "app/proguard*.pro", "app/src/**")
            include("compatibility/android-consumer/app/src/**")
        }).withPropertyName("repositoryGuardrailFiles")
    }
}

apply(from = "gradle/release-publishing.gradle.kts")
apply(from = "gradle/release-verification.gradle.kts")
