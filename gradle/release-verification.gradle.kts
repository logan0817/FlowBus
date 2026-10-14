import groovy.json.JsonSlurper
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.tasks.GradleBuild

val artifactGroup = providers.gradleProperty("GROUP").get()
val artifactVersion = providers.gradleProperty("VERSION_NAME").get()
val groupPath = artifactGroup.replace('.', '/')
val mavenRepositoryRoot = System.getProperty("maven.repo.local")
    ?.let { file(it) }
    ?: file("${System.getProperty("user.home")}/.m2/repository")
val mavenLocalRoot = mavenRepositoryRoot.resolve(groupPath)
// 生成消费者固定使用本轮发布目录，防止回退到机器上旧的同版本产物。
val consumerRepositoryUri = mavenRepositoryRoot.toURI().toASCIIString()
val libsCatalog = extensions.getByType<VersionCatalogsExtension>().named("libs")

fun VersionCatalog.version(alias: String): String {
    val candidates = listOf(alias, alias.replace('_', '-'), alias.replace('_', '.'))
    candidates.forEach { candidate ->
        val version = findVersion(candidate)
        if (version.isPresent) {
            return version.get().requiredVersion
        }
    }
    error("Missing version catalog alias: $alias")
}

fun Task.trackConsumerAndroidBaseline(compileSdk: Int = libsCatalog.version("compile_sdk_version").toInt()) {
    inputs.property("minSdk", libsCatalog.version("min_sdk_version"))
    inputs.property("compileSdk", compileSdk)
    inputs.property("targetSdk", libsCatalog.version("target_sdk_version"))
}

// 仅控制 core/Android 消费者编译器，库自身以及 Compose 编译器配对保持仓库基线。
val consumerKotlinVersion = providers.gradleProperty("flowbus.consumer.kotlinVersion")
    .orElse(libsCatalog.version("kotlin")).get()

// 独立评估普通 Android 消费者的编译 SDK，不改变基础库、targetSdk 或 Compose 配置。
val consumerAndroidCompileSdk = providers.gradleProperty("flowbus.consumer.compileSdk")
    .map { it.toInt().also { sdk -> require(sdk > 0) { "Consumer compileSdk must be positive." } } }
    .orElse(libsCatalog.version("compile_sdk_version").toInt()).get()

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
            scope = "compile",
            version = libsCatalog.version("kotlinx_coroutines")
        )
        requireModuleDependency(
            module = coreModule,
            group = "org.jetbrains.kotlinx",
            moduleNames = setOf("kotlinx-coroutines-core", "kotlinx-coroutines-core-jvm"),
            version = libsCatalog.version("kotlinx_coroutines")
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
            scope = "compile",
            version = libsCatalog.version("lifecycle_version")
        )
        requirePomDependency(
            pom = androidPom,
            groupId = "androidx.lifecycle",
            artifactId = "lifecycle-viewmodel-ktx",
            scope = "compile",
            version = libsCatalog.version("lifecycle_version")
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
            moduleName = "lifecycle-runtime-ktx",
            version = libsCatalog.version("lifecycle_version")
        )
        requireModuleDependency(
            module = androidModule,
            group = "androidx.lifecycle",
            moduleName = "lifecycle-viewmodel-ktx",
            version = libsCatalog.version("lifecycle_version")
        )

        listOf(corePom to coreModule, androidPom to androidModule).forEach { (pom, module) ->
            requirePomDependency(
                pom, "org.jetbrains.kotlin", "kotlin-stdlib", "compile", libsCatalog.version("kotlin")
            )
            requireModuleDependency(
                module, "org.jetbrains.kotlin", "kotlin-stdlib", libsCatalog.version("kotlin")
            )
        }

        // 真实产物门禁同时检查字节码和样例隔离，避免只验证源码构建目标。
        ZipFile(coreJar).use { jar ->
            val entries = jar.entries().asSequence().toList()
            check(entries.none { it.name.startsWith("com/logan/flowbus/compatibility/") }) {
                "Compatibility consumer must not be packaged in the core artifact."
            }
            val classes = entries.filter { it.name.endsWith(".class") }
            check(classes.isNotEmpty()) { "Core artifact contains no classes." }
            classes.forEach { entry ->
                java.io.DataInputStream(jar.getInputStream(entry)).use { input ->
                    check(input.readInt() == 0xCAFEBABE.toInt()) { "Invalid class: ${entry.name}" }
                    input.readUnsignedShort()
                    check(input.readUnsignedShort() == 52) { "Non-Java-8 class: ${entry.name}" }
                }
            }
        }
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
    inputs.property("artifactGroup", artifactGroup)
    inputs.property("artifactVersion", artifactVersion)
    inputs.property("repository", consumerRepositoryUri)
    inputs.property("kotlin", consumerKotlinVersion)
    inputs.file(rootProject.file(
        "flowbus-core/src/compatibility/kotlin/com/logan/flowbus/compatibility/CoreRuntimeConsumer.kt"
    ))
    inputs.file(rootProject.file(
        "flowbus-core/src/compatibility/java/com/logan/flowbus/compatibility/CoreJavaConsumer.java"
    ))
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
                    exclusiveContent {
                        forRepository { maven { url = uri("$consumerRepositoryUri") } }
                        filter { includeGroup("$artifactGroup") }
                    }
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
            import java.time.Duration

            plugins {
                kotlin("jvm") version "$consumerKotlinVersion"
                application
            }

            kotlin {
                jvmToolchain(17)
            }

            java {
                sourceCompatibility = JavaVersion.VERSION_1_8
                targetCompatibility = JavaVersion.VERSION_1_8
            }

            tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
                kotlinOptions.jvmTarget = "1.8"
            }

            application {
                mainClass.set("com.logan.flowbus.compatibility.CoreRuntimeConsumer")
            }

            tasks.named<JavaExec>("run") {
                providers.gradleProperty("flowbus.consumer.java").orNull?.let { executable = it }
                providers.gradleProperty("flowbus.consumer.expectedJava").orNull?.let {
                    systemProperty("flowbus.consumer.expectedJava", it)
                }
                timeout.set(Duration.ofSeconds(30))
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
        val runtimeSource = rootProject.file(
            "flowbus-core/src/compatibility/kotlin/com/logan/flowbus/compatibility/CoreRuntimeConsumer.kt"
        )
        runtimeSource.copyTo(sourceDir.resolve("CoreRuntimeConsumer.kt"), overwrite = true)
        val javaSourceDir = appDir.resolve("src/main/java/com/logan/flowbus/compatibility")
        javaSourceDir.mkdirs()
        rootProject.file(
            "flowbus-core/src/compatibility/java/com/logan/flowbus/compatibility/CoreJavaConsumer.java"
        ).copyTo(javaSourceDir.resolve("CoreJavaConsumer.java"), overwrite = true)
    }
}

// 原版 Android fixture 的编译输入独立于候选消费者的运行图。
val legacyAndroidArtifacts = configurations.create("legacyAndroidArtifacts") {
    isCanBeResolved = true
    isCanBeConsumed = false
    isTransitive = false
}
dependencies {
    add(legacyAndroidArtifacts.name, "$artifactGroup:flowbus:1.1.0@aar")
    add(legacyAndroidArtifacts.name, "$artifactGroup:flowbus-core:1.1.0")
}

val prepareMavenLocalConsumer = tasks.register("prepareMavenLocalConsumer") {
    group = "verification"
    description = "Generate a small Android consumer project that depends on the Maven local flowbus coordinate."
    val consumerDir = layout.buildDirectory.dir("maven-local-consumer")
    inputs.property("artifactGroup", artifactGroup)
    inputs.property("artifactVersion", artifactVersion)
    inputs.property("repository", consumerRepositoryUri)
    inputs.property("agp", libsCatalog.version("agp"))
    inputs.property("kotlin", consumerKotlinVersion)
    inputs.file(rootProject.file("compatibility/android-consumer/app/src/main/java/com/logan/flowbus/consumer/ConsumerActivity.kt"))
    inputs.dir(rootProject.file("compatibility/android-consumer/legacy/src"))
    trackConsumerAndroidBaseline(consumerAndroidCompileSdk)
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
                    exclusiveContent {
                        forRepository { maven { url = uri("$consumerRepositoryUri") } }
                        filter { includeGroup("$artifactGroup") }
                    }
                    google()
                    mavenCentral()
                }
            }

            rootProject.name = "FlowBusMavenLocalConsumer"
            include(":app", ":legacy")
            """.trimIndent()
        )
        root.resolve("build.gradle.kts").writeText("")
        root.resolve("gradle.properties").writeText(
            """
            android.useAndroidX=true
            """.trimIndent()
        )
        val legacy = root.resolve("legacy")
        legacy.resolve("baseline").mkdirs()
        legacyAndroidArtifacts.files.forEach { artifact ->
            val expected = if (artifact.extension == "aar")
                "bc7eb2750832da36f49f6be797ea76e922b864093b8ff0d3513b41a3f3450b6a"
            else "ffafa4c77ed1e7b8183f53de26a14904b37a5d6958b49fa6930d2273173fcb9f"
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(artifact.readBytes())
                .joinToString("") { "%02x".format(it.toInt() and 255) }
            check(digest == expected) { "Unexpected legacy compile artifact: ${artifact.name}" }
            if (artifact.extension == "aar") ZipFile(artifact).use { zip ->
                legacy.resolve("baseline/android.jar").writeBytes(zip.getInputStream(zip.getEntry("classes.jar")).readBytes())
            } else artifact.copyTo(legacy.resolve("baseline/core.jar"), overwrite = true)
        }
        rootProject.file("compatibility/android-consumer/legacy/src").copyRecursively(legacy.resolve("src"), overwrite = true)
        legacy.resolve("src/main/AndroidManifest.xml").writeText("<manifest />")
        legacy.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.library") version "${libsCatalog.version("agp")}"
                kotlin("android") version "$consumerKotlinVersion"
            }
            android {
                namespace = "com.logan.flowbus.consumer.legacy"
                compileSdk = $consumerAndroidCompileSdk
                defaultConfig { minSdk = 21 }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_1_8
                    targetCompatibility = JavaVersion.VERSION_1_8
                }
                kotlinOptions { jvmTarget = "1.8" }
            }
            dependencies {
                compileOnly(files("baseline/core.jar", "baseline/android.jar"))
                compileOnly("androidx.lifecycle:lifecycle-runtime-ktx:${libsCatalog.version("lifecycle_version")}")
                compileOnly("androidx.lifecycle:lifecycle-viewmodel-ktx:${libsCatalog.version("lifecycle_version")}")
                testImplementation("$artifactGroup:flowbus:$artifactVersion")
                testImplementation("junit:junit:4.13.2")
                testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${libsCatalog.version("kotlinx_coroutines")}")
            }
            tasks.withType<Test>().configureEach {
                doFirst { layout.buildDirectory.file("reports/binary-android-compatibility.txt").get().asFile.delete() }
                classpath = classpath.filter { !it.canonicalPath.startsWith(file("baseline").canonicalPath + File.separator) }
                systemProperty("flowbus.candidate.core", "${mavenLocalRoot.resolve("flowbus-core/$artifactVersion/flowbus-core-$artifactVersion.jar").invariantSeparatorsPath}")
                systemProperty("flowbus.candidate.android", "${mavenLocalRoot.resolve("flowbus/$artifactVersion/flowbus-$artifactVersion.aar").invariantSeparatorsPath}")
                systemProperty("flowbus.legacy.classes", layout.buildDirectory.dir("tmp/kotlin-classes/release").get().asFile.absolutePath)
                systemProperty("flowbus.legacy.report", layout.buildDirectory.file("reports/binary-android-compatibility.txt").get().asFile.absolutePath)
            }
            """.trimIndent()
        )
        val appDir = root.resolve("app")
        appDir.mkdirs()
        val sourceDir = appDir.resolve("src/main/java/com/logan/flowbus/consumer")
        sourceDir.mkdirs()
        appDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") version "${libsCatalog.version("agp")}"
                kotlin("android") version "$consumerKotlinVersion"
            }

            android {
                namespace = "com.logan.flowbus.consumer"
                compileSdk = $consumerAndroidCompileSdk

                defaultConfig {
                    applicationId = "com.logan.flowbus.consumer"
                    minSdk = ${libsCatalog.version("min_sdk_version")}
                    targetSdk = ${libsCatalog.version("target_sdk_version")}
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
                implementation(project(":legacy"))
            }
            """.trimIndent()
        )
        appDir.resolve("proguard-rules.pro").writeText("")
        appDir.resolve("src/main").mkdirs()
        appDir.resolve("src/main/AndroidManifest.xml").writeText(
            """
            <manifest xmlns:android="http://schemas.android.com/apk/res/android">
                <application android:name=".ConsumerApplication">
                    <activity android:name=".ConsumerActivity" android:exported="true">
                        <intent-filter>
                            <action android:name="android.intent.action.MAIN" />
                            <category android:name="android.intent.category.LAUNCHER" />
                        </intent-filter>
                    </activity>
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
        rootProject.file(
            "compatibility/android-consumer/app/src/main/java/com/logan/flowbus/consumer/ConsumerActivity.kt"
        ).copyTo(sourceDir.resolve("ConsumerActivity.kt"), overwrite = true)
    }
}

tasks.register<GradleBuild>("verifyMavenLocalConsumer") {
    group = "verification"
    description = "Build a minified Android consumer from Maven local coordinates."
    dependsOn("publishToMavenLocal", prepareMavenLocalConsumer)
    dir = layout.buildDirectory.dir("maven-local-consumer").get().asFile
    tasks = listOf(":legacy:testReleaseUnitTest", ":app:assembleRelease")
}

tasks.register<GradleBuild>("verifyMavenLocalCoreConsumer") {
    group = "verification"
    description = "Compile and run a Kotlin/JVM consumer from the Maven local flowbus-core coordinate."
    dependsOn("publishToMavenLocal", prepareMavenLocalCoreConsumer)
    dir = layout.buildDirectory.dir("maven-local-core-consumer").get().asFile
    tasks = listOf(":app:compileKotlin", ":app:run")
    // GradleBuild 不自动继承 -P 参数，显式传递运行 JVM，避免 Java 8 门禁实际在构建 JVM 上运行。
    startParameter.projectProperties = startParameter.projectProperties + listOf(
        "flowbus.consumer.java", "flowbus.consumer.expectedJava"
    ).mapNotNull { name -> providers.gradleProperty(name).orNull?.let { name to it } }.toMap()
}

val binaryConsumerBaselinesFile = rootProject.file("compatibility/core-binary-consumer/baselines.json")
val binaryConsumerBaselines = JsonSlurper().parse(binaryConsumerBaselinesFile) as Map<*, *>
val binaryConsumerBaselineVersion = providers.gradleProperty("flowbus.consumer.baselineVersion").orElse("1.0.7").get()
require(binaryConsumerBaselines.containsKey(binaryConsumerBaselineVersion)) { "Unsupported binary consumer baseline: $binaryConsumerBaselineVersion" }
val binaryConsumerDirectory = layout.buildDirectory.dir(
    if (binaryConsumerBaselineVersion == "1.0.7") "maven-local-binary-core-consumer"
    else "maven-local-binary-core-consumer-$binaryConsumerBaselineVersion"
)

val prepareMavenLocalBinaryCoreConsumer = tasks.register("prepareMavenLocalBinaryCoreConsumer") {
    group = "verification"
    description = "Generate a consumer compiled only against the published $binaryConsumerBaselineVersion core JAR."
    val consumerDir = binaryConsumerDirectory
    val templateDir = rootProject.file("compatibility/core-binary-consumer")
    val candidateJar = mavenLocalRoot.resolve("flowbus-core/$artifactVersion/flowbus-core-$artifactVersion.jar")
    inputs.dir(templateDir.resolve("src"))
    inputs.dir(templateDir.resolve("src-1.1.0"))
    inputs.file(binaryConsumerBaselinesFile)
    inputs.property("baselineVersion", binaryConsumerBaselineVersion)
    inputs.file(templateDir.resolve("build.gradle.kts.template"))
    inputs.property("artifactGroup", artifactGroup)
    inputs.property("candidateJar", candidateJar.absolutePath)
    inputs.property("kotlin", consumerKotlinVersion)
    inputs.property("stdlib", libsCatalog.version("kotlin"))
    inputs.property("coroutines", libsCatalog.version("kotlinx_coroutines"))
    outputs.dir(consumerDir)
    doLast {
        val root = consumerDir.get().asFile
        root.mkdirs()
        root.resolve("settings.gradle.kts").writeText(
            """
            pluginManagement { repositories { mavenCentral(); gradlePluginPortal() } }
            dependencyResolutionManagement {
                repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
                repositories { mavenCentral() }
            }
            rootProject.name = "FlowBusBinaryCoreConsumer"
            """.trimIndent()
        )
        templateDir.resolve("src").copyRecursively(root.resolve("src"), overwrite = true)
        if (binaryConsumerBaselineVersion == "1.1.0") {
            templateDir.resolve("src-1.1.0").copyRecursively(root.resolve("src"), overwrite = true)
        }
        val consumerSource = root.resolve("src/main/kotlin/com/logan/flowbus/binaryconsumer/BinaryCoreConsumer.kt")
        consumerSource.writeText(consumerSource.readText().replace(
            "// @VERSION_SPECIFIC_CHECK@",
            if (binaryConsumerBaselineVersion == "1.1.0") "BinaryCoreConsumer110.verify()" else ""
        ))
        val baselineMetadata = checkNotNull(binaryConsumerBaselines[binaryConsumerBaselineVersion]) as Map<*, *>
        val replacements = mapOf(
            "@GROUP@" to artifactGroup,
            "@KOTLIN@" to consumerKotlinVersion,
            "@STDLIB@" to libsCatalog.version("kotlin"),
            "@COROUTINES@" to libsCatalog.version("kotlinx_coroutines"),
            "@BASELINE@" to binaryConsumerBaselineVersion,
            "@BASELINE_SHA256@" to checkNotNull(baselineMetadata["coreSha256"]).toString(),
            "@CANDIDATE@" to candidateJar.absolutePath.replace('\\', '/')
        )
        val script = replacements.entries.fold(templateDir.resolve("build.gradle.kts.template").readText()) {
            text, (token, value) -> text.replace(token, value)
        }
        root.resolve("build.gradle.kts").writeText(script)
    }
}

tasks.register<GradleBuild>("verifyMavenLocalBinaryCoreConsumer") {
    group = "verification"
    description = "Run unchanged $binaryConsumerBaselineVersion Kotlin/Java consumer bytecode on published and candidate core JARs."
    dependsOn("publishToMavenLocal", prepareMavenLocalBinaryCoreConsumer)
    dir = binaryConsumerDirectory.get().asFile
    tasks = listOf("verifyBinaryCompatibility")
    startParameter.projectProperties = startParameter.projectProperties + listOf(
        "flowbus.consumer.java", "flowbus.consumer.expectedJava"
    ).mapNotNull { name -> providers.gradleProperty(name).orNull?.let { name to it } }.toMap()
}

val prepareMavenLocalComposeConsumer = tasks.register("prepareMavenLocalComposeConsumer") {
    group = "verification"
    description = "Generate an isolated Compose consumer using the current candidate Maven coordinates."
    val consumerDir = layout.buildDirectory.dir("maven-local-compose-consumer")
    // 默认先验证与 Lifecycle 2.6.2 基线接近的消费者；较新 Compose 作为独立实验选择。
    val composeVersion = providers.gradleProperty("flowbus.consumer.composeVersion").orElse("1.5.4").get()
    // 导航验证仅在显式选择时加入消费者，默认组合保持原有依赖与源码。
    val navigationVersion = providers.gradleProperty("flowbus.consumer.navigationVersion").orNull
    val navHostEnabled = providers.gradleProperty("flowbus.consumer.navHost").map { value ->
        when (value) {
            "true" -> true
            "false" -> false
            else -> error("flowbus.consumer.navHost must be true or false.")
        }
    }.orElse(false).get()
    require(!navHostEnabled || navigationVersion != null) {
        "NavHost verification requires flowbus.consumer.navigationVersion."
    }
    inputs.dir(rootProject.file("compatibility/compose-consumer/app"))
    if (navigationVersion != null) {
        inputs.dir(rootProject.file("compatibility/navigation-consumer/app"))
    }
    if (navHostEnabled) inputs.dir(rootProject.file("compatibility/navhost-consumer/app"))
    inputs.property("artifactGroup", artifactGroup)
    inputs.property("artifactVersion", artifactVersion)
    inputs.property("repository", consumerRepositoryUri)
    inputs.property("agp", libsCatalog.version("agp"))
    inputs.property("kotlin", libsCatalog.version("kotlin"))
    inputs.property("lifecycle", libsCatalog.version("lifecycle_version"))
    inputs.property("composeVersion", composeVersion)
    inputs.property("navigationVersion", navigationVersion ?: "none")
    inputs.property("navHost", navHostEnabled)
    trackConsumerAndroidBaseline()
    outputs.dir(consumerDir)
    doLast {
        val root = consumerDir.get().asFile
        root.deleteRecursively()
        root.mkdirs()
        rootProject.file("compatibility/compose-consumer/app")
            .copyRecursively(root.resolve("app"), overwrite = true)
        if (navigationVersion != null) {
            rootProject.file("compatibility/navigation-consumer/app")
                .copyRecursively(root.resolve("app"), overwrite = true)
        }
        if (navHostEnabled) {
            rootProject.file("compatibility/navhost-consumer/app")
                .copyRecursively(root.resolve("app"), overwrite = true)
            root.resolve("app/src/main/java/com/logan/flowbus/composeconsumer/NavHostVerificationProfile.kt").writeText(
                """
                package com.logan.flowbus.composeconsumer

                // 仅对本轮已选择的新版组合要求观察默认动画期间的真实 owner 阈值。
                internal const val NAV_HOST_EXPECT_ANIMATED_TRANSITION = ${navigationVersion == "2.8.3"}
                """.trimIndent()
            )
        }
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
                    exclusiveContent {
                        forRepository { maven { url = uri("$consumerRepositoryUri") } }
                        filter { includeGroup("$artifactGroup") }
                    }
                    google()
                    mavenCentral()
                }
            }
            rootProject.name = "FlowBusComposeMavenLocalConsumer"
            include(":app")
            """.trimIndent()
        )
        root.resolve("build.gradle.kts").writeText("")
        root.resolve("gradle.properties").writeText("android.useAndroidX=true")
        root.resolve("app/build.gradle.kts").writeText(
            """
            plugins {
                id("com.android.application") version "${libsCatalog.version("agp")}"
                kotlin("android") version "${libsCatalog.version("kotlin")}"
            }
            android {
                namespace = "com.logan.flowbus.composeconsumer"
                compileSdk = ${libsCatalog.version("compile_sdk_version")}
                defaultConfig {
                    applicationId = "com.logan.flowbus.composeconsumer"
                    minSdk = ${libsCatalog.version("min_sdk_version")}
                    targetSdk = ${libsCatalog.version("target_sdk_version")}
                    versionCode = 1
                    versionName = "1.0"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_1_8
                    targetCompatibility = JavaVersion.VERSION_1_8
                }
                kotlinOptions { jvmTarget = "1.8" }
                buildFeatures { compose = true }
                composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }
                buildTypes {
                    getByName("release") {
                        isMinifyEnabled = true
                        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
                    }
                }
            }
            dependencies {
                implementation("$artifactGroup:flowbus:$artifactVersion")
                implementation("androidx.activity:activity-compose:1.8.2")
                implementation("androidx.compose.runtime:runtime:$composeVersion")
                implementation("androidx.compose.ui:ui:$composeVersion")
                implementation("androidx.lifecycle:lifecycle-runtime-ktx:${libsCatalog.version("lifecycle_version")}")
                ${navigationVersion?.let { "implementation(\"androidx.navigation:navigation-runtime:$it\")" } ?: ""}
                ${if (navHostEnabled) "implementation(\"androidx.navigation:navigation-compose:$navigationVersion\")" else ""}
            }
            """.trimIndent()
        )
    }
}

tasks.register<GradleBuild>("verifyMavenLocalComposeConsumer") {
    group = "verification"
    description = "Build a minified Compose consumer; Compose dependencies stay outside the base library."
    dependsOn("publishToMavenLocal", prepareMavenLocalComposeConsumer)
    dir = layout.buildDirectory.dir("maven-local-compose-consumer").get().asFile
    tasks = listOf(":app:assembleRelease")
}
