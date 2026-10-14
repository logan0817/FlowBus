import java.security.MessageDigest

plugins { kotlin("jvm") version "1.9.25" }
repositories { mavenCentral() }
kotlin { jvmToolchain(17) }
val candidateJar = rootDir.resolve("../../build/candidate-maven/io/github/logan0817/flowbus-core/1.2.0/flowbus-core-1.2.0.jar")
val target = providers.gradleProperty("benchmark.target").orElse("candidate").get()
require(target in setOf("baseline", "candidate", "shared"))
val baseline by configurations.creating
configurations.runtimeClasspath { exclude(group = "io.github.logan0817", module = "flowbus-core") }
dependencies {
    compileOnly(files(candidateJar))
    baseline("io.github.logan0817:flowbus-core:1.1.0") { isTransitive = false }
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.openjdk.jmh:jmh-core:1.37")
    annotationProcessor("org.openjdk.jmh:jmh-generator-annprocess:1.37")
}
val verifyArtifact by tasks.registering {
    inputs.file(candidateJar)
    doLast {
        check(candidateJar.isFile) { "Publish the isolated 1.2.0 candidate first." }
        val released = baseline.singleFile
        val digest = MessageDigest.getInstance("SHA-256").digest(released.readBytes()).joinToString("") { "%02x".format(it) }
        check(digest == "ffafa4c77ed1e7b8183f53de26a14904b37a5d6958b49fa6930d2273173fcb9f") { "Unexpected baseline artifact: $digest" }
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
    val regex = when (target) {
        "shared" -> ".*BusBenchmark.*"
        "baseline" -> ".*(BusBenchmark|BusLifecycleBenchmark).*"
        else -> ".*Benchmark.*"
    }
    args(regex, "-p", "adapter=" + if (target == "shared") "shared" else "bus",
        "-f", if (smoke) "1" else "3", "-wi", if (smoke) "1" else "5",
        "-i", if (smoke) "1" else "5", "-w", if (smoke) "100ms" else "1s",
        "-r", if (smoke) "100ms" else "1s", "-foe", "true", "-jvmArgs", "-Xms512m -Xmx512m", "-prof", "gc", "-rf", "json",
        "-rff", layout.buildDirectory.file("reports/$target${if (smoke) "-smoke" else ""}.json").get().asFile.absolutePath)
    doFirst { layout.buildDirectory.dir("reports").get().asFile.mkdirs() }
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
