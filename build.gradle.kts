import groovy.json.JsonSlurper
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.tasks.GradleBuild
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.plugins.signing.Sign

plugins {
    id("com.android.application") apply false
    id("com.android.library") apply false
    alias(libs.plugins.binary.compatibility.validator)
    alias(libs.plugins.vanniktech.maven.publish) apply false
    base
}

val mavenLocalPublishTasks = listOf(
    ":flowbus-core:publishToMavenLocal",
    ":library-android:publishToMavenLocal"
)
val mavenCentralPublishTasks = listOf(
    ":flowbus-core:publishAllPublicationsToMavenCentralRepository",
    ":library-android:publishAllPublicationsToMavenCentralRepository"
)
val artifactGroup = providers.gradleProperty("GROUP").get()
val artifactVersion = providers.gradleProperty("VERSION_NAME").get()
val groupPath = artifactGroup.replace('.', '/')
val mavenLocalRoot = file("${System.getProperty("user.home")}/.m2/repository/$groupPath")
val coreArtifactDir = mavenLocalRoot.resolve("flowbus-core/$artifactVersion")
val androidArtifactDir = mavenLocalRoot.resolve("flowbus/$artifactVersion")

apiValidation {
    ignoredProjects.add("app")
    ignoredClasses.add("com.logan.flowbus.BuildConfig")
}

allprojects {
    group = providers.gradleProperty("GROUP").get()
    version = providers.gradleProperty("VERSION_NAME").get()
}

fun Project.remoteMavenPublicationRequested(): Boolean =
    gradle.taskGraph.allTasks.any { task ->
        task.name.startsWith("publish") &&
            task.name != "publishToMavenLocal" &&
            !task.name.endsWith("ToMavenLocal")
    }

subprojects {
    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("-Xlint:-options")
    }

    plugins.withId("signing") {
        tasks.withType<Sign>().configureEach {
            onlyIf("remote Maven publication requires signatures") {
                rootProject.remoteMavenPublicationRequested()
            }
        }
    }

    tasks.matching { it.name == "publishAllPublicationsToMavenCentralRepository" }.configureEach {
        mustRunAfter(rootProject.tasks.named("clean"))
    }
}

val cleanMavenLocalArtifacts = tasks.register("cleanMavenLocalArtifacts") {
    group = "publishing"
    description = "Remove this release version from Maven local before publishing fresh artifacts."
    doLast {
        delete(coreArtifactDir, androidArtifactDir)
    }
}

gradle.projectsEvaluated {
    mavenLocalPublishTasks.forEach { taskPath ->
        tasks.getByPath(taskPath).mustRunAfter(cleanMavenLocalArtifacts)
    }
}

tasks.register("publishToMavenLocal") {
    group = "publishing"
    description = "Local verification entry: publish flowbus-core and flowbus to Maven local from the main FlowBus project."
    dependsOn(cleanMavenLocalArtifacts)
    dependsOn(mavenLocalPublishTasks)
}

tasks.register("verifyMavenLocalArtifacts") {
    group = "verification"
    description = "Verify locally published flowbus-core and flowbus Maven artifacts and POM metadata."
    dependsOn("publishToMavenLocal")

    doLast {
        val coreBase = mavenLocalRoot.resolve("flowbus-core/$artifactVersion/flowbus-core-$artifactVersion")
        val androidBase = mavenLocalRoot.resolve("flowbus/$artifactVersion/flowbus-$artifactVersion")
        val coreJar = coreBase.resolveSibling("${coreBase.name}.jar")
        val corePom = coreBase.resolveSibling("${coreBase.name}.pom")
        val coreModule = coreBase.resolveSibling("${coreBase.name}.module")
        val coreSourcesJar = coreBase.resolveSibling("${coreBase.name}-sources.jar")
        val coreJavadocJar = coreBase.resolveSibling("${coreBase.name}-javadoc.jar")
        val androidAar = androidBase.resolveSibling("${androidBase.name}.aar")
        val androidPom = androidBase.resolveSibling("${androidBase.name}.pom")
        val androidModule = androidBase.resolveSibling("${androidBase.name}.module")
        val androidSourcesJar = androidBase.resolveSibling("${androidBase.name}-sources.jar")
        val androidJavadocJar = androidBase.resolveSibling("${androidBase.name}-javadoc.jar")

        fun requireFile(file: File, description: String) {
            check(file.isFile) { "Missing $description: ${file.absolutePath}" }
        }

        fun requireZipEntry(file: File, description: String, predicate: (String) -> Boolean) {
            ZipFile(file).use { zip ->
                val entries = zip.entries().asSequence().map { it.name }.toList()
                check(entries.any(predicate)) {
                    "$description has no expected entries: ${file.absolutePath}"
                }
            }
        }

        fun requirePomValue(pom: File, value: String, description: String) {
            check(pom.readText().contains(value)) {
                "${pom.name} is missing $description: $value"
            }
        }

        fun requirePomDependency(
            pom: File,
            groupId: String,
            artifactIds: Set<String>,
            scope: String,
            version: String? = null
        ) {
            val document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(pom)
            val dependencies = document.getElementsByTagName("dependency")
            val found = (0 until dependencies.length).any { index ->
                val dependency = dependencies.item(index)
                fun tagValue(tagName: String): String? {
                    val values = dependency.childNodes
                    return (0 until values.length)
                        .map { values.item(it) }
                        .firstOrNull { it.nodeName == tagName }
                        ?.textContent
                }

                tagValue("groupId") == groupId &&
                    artifactIds.contains(tagValue("artifactId")) &&
                    tagValue("scope") == scope &&
                    (version == null || tagValue("version") == version)
            }
            check(found) {
                "${pom.name} is missing $scope dependency $groupId:${artifactIds.joinToString("|")}" +
                    (version?.let { ":$it" } ?: "")
            }
        }

        fun requirePomDependency(
            pom: File,
            groupId: String,
            artifactId: String,
            scope: String,
            version: String? = null
        ) {
            requirePomDependency(
                pom = pom,
                groupId = groupId,
                artifactIds = setOf(artifactId),
                scope = scope,
                version = version
            )
        }

        fun requireModuleDependency(
            module: File,
            group: String,
            moduleNames: Set<String>,
            version: String? = null
        ) {
            @Suppress("UNCHECKED_CAST")
            val metadata = JsonSlurper().parse(module) as Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val variants = metadata["variants"] as? List<Map<String, Any?>> ?: emptyList()
            val apiVariants = variants.filter { variant ->
                (variant["name"] as? String)?.contains("api", ignoreCase = true) == true
            }
            val found = apiVariants.any { variant ->
                @Suppress("UNCHECKED_CAST")
                val dependencies = variant["dependencies"] as? List<Map<String, Any?>> ?: emptyList()
                dependencies.any { dependency ->
                    val versionInfo = dependency["version"] as? Map<*, *>
                    dependency["group"] == group &&
                        moduleNames.contains(dependency["module"]) &&
                        (version == null || versionInfo?.get("requires") == version)
                }
            }
            check(found) {
                "${module.name} is missing public module metadata dependency " +
                    "$group:${moduleNames.joinToString("|")}" +
                    (version?.let { ":$it" } ?: "")
            }
        }

        fun requireModuleDependency(
            module: File,
            group: String,
            moduleName: String,
            version: String? = null
        ) {
            requireModuleDependency(
                module = module,
                group = group,
                moduleNames = setOf(moduleName),
                version = version
            )
        }

        requireFile(coreJar, "flowbus-core jar")
        requireFile(corePom, "flowbus-core pom")
        requireFile(coreModule, "flowbus-core Gradle module metadata")
        requireFile(coreSourcesJar, "flowbus-core sources jar")
        requireFile(coreJavadocJar, "flowbus-core javadoc jar")
        requireFile(androidAar, "flowbus aar")
        requireFile(androidPom, "flowbus pom")
        requireFile(androidModule, "flowbus Gradle module metadata")
        requireFile(androidSourcesJar, "flowbus sources jar")
        requireFile(androidJavadocJar, "flowbus javadoc jar")
        requireZipEntry(coreSourcesJar, "flowbus-core sources jar") {
            it.startsWith("com/logan/flowbus/core/") && it.endsWith(".kt")
        }
        requireZipEntry(coreJavadocJar, "flowbus-core javadoc jar") {
            it.endsWith("index.html") || it.endsWith("package-list") || it.endsWith("element-list")
        }
        requireZipEntry(androidSourcesJar, "flowbus sources jar") {
            it.startsWith("com/logan/flowbus/") && it.endsWith(".kt")
        }
        requireZipEntry(androidJavadocJar, "flowbus javadoc jar") {
            it.endsWith("index.html") || it.endsWith("package-list") || it.endsWith("element-list")
        }

        listOf(corePom to "flowbus-core", androidPom to "flowbus").forEach { (pom, artifactId) ->
            requirePomValue(pom, "<groupId>$artifactGroup</groupId>", "groupId")
            requirePomValue(pom, "<artifactId>$artifactId</artifactId>", "artifactId")
            requirePomValue(pom, "<version>$artifactVersion</version>", "version")
            requirePomValue(pom, "<scm>", "SCM metadata")
            requirePomValue(pom, "<issueManagement>", "issue metadata")
            requirePomValue(pom, providers.gradleProperty("POM_LICENSE_NAME").get(), "license name")
            requirePomValue(pom, providers.gradleProperty("POM_LICENSE_URL").get(), "license URL")
            requirePomValue(pom, providers.gradleProperty("POM_DEVELOPER_ID").get(), "developer ID")
            requirePomValue(pom, providers.gradleProperty("POM_DEVELOPER_NAME").get(), "developer name")
            requirePomValue(pom, providers.gradleProperty("POM_DEVELOPER_EMAIL").get(), "developer email")
            requirePomValue(pom, providers.gradleProperty("POM_URL").get(), "project URL")
        }

        requirePomDependency(
            pom = corePom,
            groupId = "org.jetbrains.kotlinx",
            artifactIds = setOf("kotlinx-coroutines-core", "kotlinx-coroutines-core-jvm"),
            scope = "compile"
        )
        requireModuleDependency(
            module = coreModule,
            group = "org.jetbrains.kotlinx",
            moduleNames = setOf("kotlinx-coroutines-core", "kotlinx-coroutines-core-jvm")
        )
        requirePomDependency(
            pom = androidPom,
            groupId = artifactGroup,
            artifactId = "flowbus-core",
            scope = "compile",
            version = artifactVersion
        )
        requirePomDependency(
            pom = androidPom,
            groupId = "androidx.lifecycle",
            artifactId = "lifecycle-runtime-ktx",
            scope = "compile"
        )
        requirePomDependency(
            pom = androidPom,
            groupId = "androidx.lifecycle",
            artifactId = "lifecycle-viewmodel-ktx",
            scope = "compile"
        )
        requireModuleDependency(
            module = androidModule,
            group = artifactGroup,
            moduleName = "flowbus-core",
            version = artifactVersion
        )
        requireModuleDependency(
            module = androidModule,
            group = "androidx.lifecycle",
            moduleName = "lifecycle-runtime-ktx"
        )
        requireModuleDependency(
            module = androidModule,
            group = "androidx.lifecycle",
            moduleName = "lifecycle-viewmodel-ktx"
        )

        ZipFile(androidAar).use { aar ->
            check(aar.getEntry("classes.jar") != null) { "${androidAar.name} is missing classes.jar" }
            check(aar.getEntry("proguard.txt") != null) { "${androidAar.name} is missing proguard.txt" }
        }
    }
}

val prepareMavenLocalCoreConsumer = tasks.register("prepareMavenLocalCoreConsumer") {
    group = "verification"
    description = "Generate a small Kotlin/JVM consumer project that depends on the Maven local flowbus-core coordinate."
    val consumerDir = layout.buildDirectory.dir("maven-local-core-consumer")
    outputs.dir(consumerDir)
    doLast {
        val root = consumerDir.get().asFile
        root.deleteRecursively()
        root.mkdirs()
        root.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    mavenLocal()
                    mavenCentral()
                }
            }

            rootProject.name = "FlowBusCoreMavenLocalConsumer"
            include(":app")
            """.trimIndent()
        )
        root.resolve("build.gradle.kts").writeText("")
        val appDir = root.resolve("app")
        appDir.mkdirs()
        val sourceDir = appDir.resolve("src/main/kotlin/com/logan/flowbus/coreconsumer")
        sourceDir.mkdirs()
        appDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                kotlin("jvm") version "${libs.versions.kotlin.get()}"
            }

            kotlin {
                jvmToolchain(17)
            }

            dependencies {
                implementation("$artifactGroup:flowbus-core:$artifactVersion")
            }
            """.trimIndent()
        )
        sourceDir.resolve("CoreConsumer.kt").writeText(
            """
            package com.logan.flowbus.coreconsumer

            import com.logan.flowbus.core.DefaultFlowBus
            import com.logan.flowbus.core.FlowBus
            import com.logan.flowbus.core.eventChannel
            import com.logan.flowbus.core.flow
            import com.logan.flowbus.core.flowOn
            import com.logan.flowbus.core.post
            import com.logan.flowbus.core.send
            import kotlinx.coroutines.flow.Flow

            data class CoreConsumerEvent(val value: String)

            class CoreConsumer {
                private val bus = FlowBus()
                private val channel = eventChannel<CoreConsumerEvent>("consumer.core")

                fun sendGlobal(): Boolean {
                    return CoreConsumerEvent("global").send()
                }

                fun sendLocal(): Boolean {
                    return bus.post(CoreConsumerEvent("local"))
                }

                fun observeGlobal(): Flow<CoreConsumerEvent> {
                    return DefaultFlowBus.flow()
                }

                fun observeChannel(): Flow<CoreConsumerEvent> {
                    return channel.flowOn(bus)
                }
            }
            """.trimIndent()
        )
    }
}

val prepareMavenLocalConsumer = tasks.register("prepareMavenLocalConsumer") {
    group = "verification"
    description = "Generate a small Android consumer project that depends on the Maven local flowbus coordinate."
    val consumerDir = layout.buildDirectory.dir("maven-local-consumer")
    outputs.dir(consumerDir)
    doLast {
        val root = consumerDir.get().asFile
        root.deleteRecursively()
        root.mkdirs()
        root.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement {
                repositories {
                    google()
                    mavenCentral()
                    gradlePluginPortal()
                }
            }

            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories {
                    mavenLocal()
                    google()
                    mavenCentral()
                }
            }

            rootProject.name = "FlowBusMavenLocalConsumer"
            include(":app")
            """.trimIndent()
        )
        root.resolve("build.gradle.kts").writeText("")
        root.resolve("gradle.properties").writeText(
            """
            android.useAndroidX=true
            """.trimIndent()
        )
        val appDir = root.resolve("app")
        appDir.mkdirs()
        val sourceDir = appDir.resolve("src/main/java/com/logan/flowbus/consumer")
        sourceDir.mkdirs()
        appDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") version "${libs.versions.agp.get()}"
                kotlin("android") version "${libs.versions.kotlin.get()}"
            }

            android {
                namespace = "com.logan.flowbus.consumer"
                compileSdk = ${libs.versions.compile.sdk.version.get()}

                defaultConfig {
                    applicationId = "com.logan.flowbus.consumer"
                    minSdk = ${libs.versions.min.sdk.version.get()}
                    targetSdk = ${libs.versions.target.sdk.version.get()}
                    versionCode = 1
                    versionName = "1.0"
                }

                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_1_8
                    targetCompatibility = JavaVersion.VERSION_1_8
                }

                kotlinOptions {
                    jvmTarget = "1.8"
                }

                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro"
                        )
                    }
                }
            }

            dependencies {
                implementation("$artifactGroup:flowbus:$artifactVersion")
            }
            """.trimIndent()
        )
        appDir.resolve("proguard-rules.pro").writeText("")
        appDir.resolve("src/main").mkdirs()
        appDir.resolve("src/main/AndroidManifest.xml").writeText(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:name=".ConsumerApplication">
                    <activity android:name=".ConsumerActivity" />
                </application>
            </manifest>
            """.trimIndent()
        )
        sourceDir.resolve("ConsumerApplication.kt").writeText(
            """
            package com.logan.flowbus.consumer

            import android.app.Application
            import com.logan.flowbus.eventChannel
            import com.logan.flowbus.tryPost

            class ConsumerApplication : Application() {
                override fun onCreate() {
                    super.onCreate()
                    val channel = eventChannel<String>("consumer.smoke")
                    channel.tryPost("ready")
                }
            }
            """.trimIndent()
        )
        sourceDir.resolve("ConsumerActivity.kt").writeText(
            """
            package com.logan.flowbus.consumer

            import android.app.Activity
            import android.os.Bundle
            import androidx.lifecycle.Lifecycle
            import androidx.lifecycle.LifecycleOwner
            import androidx.lifecycle.LifecycleRegistry
            import androidx.lifecycle.ViewModelStore
            import androidx.lifecycle.ViewModelStoreOwner
            import com.logan.flowbus.collectEvent
            import com.logan.flowbus.onEvent
            import com.logan.flowbus.postScopedEvent
            import com.logan.flowbus.scopedEventFlow
            import kotlinx.coroutines.flow.flowOf

            class ConsumerActivity : Activity(), LifecycleOwner, ViewModelStoreOwner {
                private val lifecycleRegistry = LifecycleRegistry(this)
                private val store = ViewModelStore()

                override val lifecycle: Lifecycle
                    get() = lifecycleRegistry

                override val viewModelStore: ViewModelStore
                    get() = store

                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    lifecycleRegistry.currentState = Lifecycle.State.CREATED
                    collectEvent(flowOf("ready")) { value ->
                        check(value.isNotBlank())
                    }
                    onEvent<String>(eventName = "consumer.lifecycle") { value ->
                        check(value.isNotBlank())
                    }
                    postScopedEvent(event = "scoped", eventName = "consumer.scope")
                    scopedEventFlow<String>(eventName = "consumer.scope")
                }

                override fun onStart() {
                    super.onStart()
                    lifecycleRegistry.currentState = Lifecycle.State.STARTED
                }

                override fun onDestroy() {
                    lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
                    store.clear()
                    super.onDestroy()
                }
            }
            """.trimIndent()
        )
    }
}

tasks.register<GradleBuild>("verifyMavenLocalConsumer") {
    group = "verification"
    description = "Build a minified Android consumer from Maven local coordinates."
    dependsOn("publishToMavenLocal", prepareMavenLocalConsumer)
    dir = layout.buildDirectory.dir("maven-local-consumer").get().asFile
    tasks = listOf(":app:assembleRelease")
}

tasks.register<GradleBuild>("verifyMavenLocalCoreConsumer") {
    group = "verification"
    description = "Compile a Kotlin/JVM consumer from the Maven local flowbus-core coordinate."
    dependsOn("publishToMavenLocal", prepareMavenLocalCoreConsumer)
    dir = layout.buildDirectory.dir("maven-local-core-consumer").get().asFile
    tasks = listOf(":app:compileKotlin")
}

tasks.register("publishToMavenCentral") {
    group = "publishing"
    description = "Upload flowbus-core and flowbus to a Maven Central Portal deployment from the main FlowBus project."
    dependsOn(mavenCentralPublishTasks)
}

tasks.register("releaseToMavenCentral") {
    group = "publishing"
    description = "Compatibility release entry: clean, then upload a Central Portal deployment for manual release or drop."
    dependsOn("clean", "publishToMavenCentral")
}

tasks.named("publishToMavenCentral") {
    mustRunAfter("clean")
}
