import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import java.security.MessageDigest
import java.util.Properties

plugins { kotlin("jvm") version "1.9.25" }
repositories { mavenCentral() }
kotlin { jvmToolchain(17) }
val candidateVersion = Properties().apply {
    rootDir.resolve("../../gradle.properties").inputStream().use { load(it) }
}.getProperty("VERSION_NAME")
val candidateJar = rootDir.resolve("../../build/candidate-maven/io/github/logan0817/flowbus-core/$candidateVersion/flowbus-core-$candidateVersion.jar")
val target = providers.gradleProperty("benchmark.target").orElse("candidate").get()
require(target in setOf("baseline", "candidate", "shared"))
val baselineVersion = "1.2.0"
val baselineSha256 = "525c10846d00e7a41f932ac0ce45dba47b7b8b1da58bc68266e33df4a4b974f2"
val baseline by configurations.creating
configurations.runtimeClasspath { exclude(group = "io.github.logan0817", module = "flowbus-core") }
dependencies {
    compileOnly(files(candidateJar))
    baseline("io.github.logan0817:flowbus-core:$baselineVersion") { isTransitive = false }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.openjdk.jmh:jmh-core:1.37")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}
fun sha256(file: File): String = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
    .joinToString("") { "%02x".format(it.toInt() and 255) }
fun benchmarkIdentity(): Map<String, Any> {
    val repository = rootDir.resolve("../..").canonicalFile
    val core = if (target == "baseline") baseline.singleFile else candidateJar
    fun sources(directory: File) = directory.walkTopDown().filter { it.isFile }.toList()
        .sortedBy { it.invariantSeparatorsPath }
        .associate { it.relativeTo(repository).invariantSeparatorsPath to sha256(it) }
    return mapOf(
        "schemaVersion" to 1,
        "target" to target,
        "coreJar" to mapOf("path" to core.canonicalPath, "sha256" to sha256(core),
            "version" to if (target == "baseline") baselineVersion else candidateVersion),
        "baselineVersion" to baselineVersion,
        "baselineSha256" to baselineSha256,
        "candidateVersion" to candidateVersion,
        "candidateSha256" to sha256(candidateJar),
        "sourceSha256" to sources(repository.resolve("flowbus-core/src/main")),
        "benchmarkSourceSha256" to sources(rootDir.resolve("src")),
        "benchmarkBuildSha256" to listOf("build.gradle.kts", "settings.gradle.kts").associateWith {
            sha256(rootDir.resolve(it))
        }
    )
}
val verifyArtifact by tasks.registering {
    inputs.file(candidateJar)
    doLast {
        check(candidateJar.isFile) { "Publish the isolated $candidateVersion candidate first." }
        val released = baseline.singleFile
        val digest = sha256(released)
        check(digest == baselineSha256) { "Unexpected baseline artifact: $digest" }
    }
}
fun JavaExec.benchmarkClasspath() {
    dependsOn(verifyArtifact, tasks.classes)
    classpath = sourceSets.main.get().runtimeClasspath + files(if (target == "baseline") baseline.singleFile else candidateJar)
}
tasks.register<JavaExec>("selfCheck") {
    benchmarkClasspath()
    mainClass.set("com.logan.flowbus.benchmark.Workload")
    args(target)
}
tasks.register<JavaExec>("jmh") {
    benchmarkClasspath()
    mainClass.set("org.openjdk.jmh.Main")
    val smoke = providers.gradleProperty("benchmark.smoke").isPresent
    val regex = providers.gradleProperty("benchmark.filter").orElse(when (target) {
        "shared" -> ".*BusBenchmark.*"
        "baseline" -> ".*(BusBenchmark|BusLifecycleBenchmark).*"
        else -> ".*(BusBenchmark|BusLifecycleBenchmark|CandidateConfigBenchmark).*"
    }).get()
    val report = providers.gradleProperty("benchmark.report").orElse("$target${if (smoke) "-smoke" else ""}").get()
    val reportFile = layout.buildDirectory.file("reports/$report.json")
    val identityFile = layout.buildDirectory.file("reports/$report.identity.json")
    var measuredIdentity: Map<String, Any>? = null
    var previousReport: Pair<Long, String>? = null
    args(regex, "-p", "adapter=" + if (target == "shared") "shared" else "bus",
        "-f", if (smoke) "1" else "3", "-wi", if (smoke) "1" else "5",
        "-i", if (smoke) "1" else "5", "-w", if (smoke) "100ms" else "1s",
        "-r", if (smoke) "100ms" else "1s", "-foe", "true", "-jvmArgs", "-Xms512m -Xmx512m", "-prof", "gc", "-rf", "json",
        "-rff", reportFile.get().asFile.absolutePath)
    providers.gradleProperty("benchmark.scenarios").orNull?.let { args("-p", "scenario=$it") }
    doFirst {
        reportFile.get().asFile.parentFile.mkdirs()
        delete(identityFile)
        val output = reportFile.get().asFile
        previousReport = if (output.isFile) output.lastModified() to sha256(output) else null
        measuredIdentity = benchmarkIdentity()
    }
    doLast {
        val identity = checkNotNull(measuredIdentity)
        check(identity == benchmarkIdentity()) { "Benchmark inputs changed during measurement." }
        val output = reportFile.get().asFile
        check(output.isFile && (JsonSlurper().parse(output) as? List<*>)?.isNotEmpty() == true) {
            "JMH did not produce a non-empty report."
        }
        check(previousReport != (output.lastModified() to sha256(output))) { "JMH did not replace the previous report." }
        identityFile.get().asFile.writeText(JsonOutput.prettyPrint(JsonOutput.toJson(
            identity + ("reportSha256" to sha256(output))
        )) + "\n")
    }
}

// 正式对照发现注册成本变化后使用；仅采样分配来源，不替代完整基准。
tasks.register<JavaExec>("profileRegistration") {
    benchmarkClasspath()
    mainClass.set("org.openjdk.jmh.Main")
    args(".*BusLifecycleBenchmark.*", "-p", "adapter=bus", "-p", "scenario=registration",
        "-bm", "thrpt", "-f", "1", "-wi", "3", "-i", "3", "-w", "1s", "-r", "1s",
        "-foe", "true", "-jvmArgs", "-Xms512m -Xmx512m", "-prof", "jfr:dir=" +
            layout.buildDirectory.dir("profiles/$target").get().asFile.absolutePath)
}
