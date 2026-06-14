import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.KotlinJvm
import org.gradle.kotlin.dsl.withType
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    `java-library`
    kotlin("jvm")

    id("signing")
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktech.maven.publish)
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_1_8)
    }
}

dependencies {
    api(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}


fun metadataProperty(name: String) = providers.gradleProperty(name)

val artifactGroup = metadataProperty("GROUP").get()
val artifactVersion = metadataProperty("VERSION_NAME").get()

group = artifactGroup
version = artifactVersion


signing {
    // 强制使用 GPG 命令行工具，这会使插件去 gradle.properties 中查找
    // signing.keyId 和 signing.password
    useGpgCmd()
}

mavenPublishing {
    coordinates(
        artifactGroup,
        "flowbus-core",
        artifactVersion
    )

    configure(
        KotlinJvm(
            javadocJar = JavadocJar.Dokka("dokkaHtml"),
            sourcesJar = true
        )
    )

    publishToMavenCentral(false)
    signAllPublications() // <-- 关键！这个方法会自动找到并签名 Publication
    pom {
        name.set("FlowBus Core")
        description.set("Platform-neutral FlowBus core built on Kotlin Coroutines and Flow.")
        inceptionYear.set("2026")
        url.set(metadataProperty("POM_URL"))
        licenses {
            license {
                name.set(metadataProperty("POM_LICENSE_NAME"))
                url.set(metadataProperty("POM_LICENSE_URL"))
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set(metadataProperty("POM_DEVELOPER_ID"))
                name.set(metadataProperty("POM_DEVELOPER_NAME"))
                email.set(metadataProperty("POM_DEVELOPER_EMAIL"))
            }
        }
        scm {
            url.set(metadataProperty("POM_SCM_URL"))
            connection.set(metadataProperty("POM_SCM_CONNECTION"))
            developerConnection.set(metadataProperty("POM_SCM_DEV_CONNECTION"))
        }
        issueManagement {
            url.set(metadataProperty("POM_ISSUE_URL"))
            system.set(metadataProperty("POM_ISSUE_SYSTEM"))
        }
    }
}
