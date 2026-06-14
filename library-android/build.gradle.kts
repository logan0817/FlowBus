import com.vanniktech.maven.publish.AndroidSingleVariantLibrary
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.android.library")
    kotlin("android")

    id("signing")
    alias(libs.plugins.vanniktech.maven.publish)
}

android {
    compileSdk = libs.versions.compile.sdk.version.get().toInt()

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        minSdk = libs.versions.min.sdk.version.get().toInt()
        namespace = "com.logan.flowbus"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    testOptions {
        animationsDisabled = true
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        disable.add("GradleDependency")
        disable.add("AndroidGradlePluginVersion")
    }
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    api(projects.flowbusCore)
    api(libs.lifecycle.runtime)
    api(libs.lifecycle.viewmodel)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}

signing {
    // 强制使用 GPG 命令行工具，这会使插件去 gradle.properties 中查找
    // signing.keyId 和 signing.password
    useGpgCmd()
}

mavenPublishing {
    configure(
        AndroidSingleVariantLibrary(
            variant = "release",
            sourcesJar = true,
            publishJavadocJar = true
        )
    )

    coordinates(
        providers.gradleProperty("GROUP").get(),
        "flowbus",
        providers.gradleProperty("VERSION_NAME").get()
    )

    publishToMavenCentral(false)
    signAllPublications() // <-- 关键！这个方法会自动找到并签名 Publication

    pom {
        name.set("FlowBus")
        description.set("Android entry package for FlowBus, built on top of flowbus-core.")
        inceptionYear.set("2026")
        url.set(providers.gradleProperty("POM_URL"))
        licenses {
            license {
                name.set(providers.gradleProperty("POM_LICENSE_NAME"))
                url.set(providers.gradleProperty("POM_LICENSE_URL"))
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set(providers.gradleProperty("POM_DEVELOPER_ID"))
                name.set(providers.gradleProperty("POM_DEVELOPER_NAME"))
                email.set(providers.gradleProperty("POM_DEVELOPER_EMAIL"))
            }
        }
        scm {
            url.set(providers.gradleProperty("POM_SCM_URL"))
            connection.set(providers.gradleProperty("POM_SCM_CONNECTION"))
            developerConnection.set(providers.gradleProperty("POM_SCM_DEV_CONNECTION"))
        }
        issueManagement {
            url.set(providers.gradleProperty("POM_ISSUE_URL"))
            system.set(providers.gradleProperty("POM_ISSUE_SYSTEM"))
        }
    }
}
