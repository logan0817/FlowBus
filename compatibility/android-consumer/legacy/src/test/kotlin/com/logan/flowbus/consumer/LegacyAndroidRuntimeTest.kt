package com.logan.flowbus.consumer

import com.logan.flowbus.FlowBusAndroid
import com.logan.flowbus.FlowEventBus
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.FlowBusLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Test
import java.io.File
import java.io.ByteArrayInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** 编译好的旧 main class 在候选 core/Android runtime 上执行；不依赖设备或 Android 方法 stub。 */
@OptIn(ExperimentalCoroutinesApi::class)
class LegacyAndroidRuntimeTest {
    @Test fun original110BytecodeRunsAgainstCandidate() = runTest {
        fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        fun assertRuntime(type: Class<*>, artifact: File, aar: Boolean) {
            val name = type.name.replace('.', '/') + ".class"
            val expected = ZipFile(artifact).use { zip ->
                if (aar) ZipInputStream(ByteArrayInputStream(zip.getInputStream(zip.getEntry("classes.jar")).readBytes())).use { jar ->
                    var entry = jar.nextEntry
                    while (entry != null && entry.name != name) entry = jar.nextEntry
                    check(entry != null)
                    jar.readBytes()
                } else zip.getInputStream(zip.getEntry(name)).readBytes()
            }
            val actual = checkNotNull(type.getResourceAsStream("/$name")).use { it.readBytes() }
            check(hash(actual) == hash(expected)) { "Wrong runtime for ${type.name}" }
        }
        val candidateCore = File(checkNotNull(System.getProperty("flowbus.candidate.core")))
        val candidateAndroid = File(checkNotNull(System.getProperty("flowbus.candidate.android")))
        assertRuntime(FlowBus::class.java, candidateCore, false)
        assertRuntime(FlowEventBus::class.java, candidateAndroid, true)
        val classes = File(checkNotNull(System.getProperty("flowbus.legacy.classes")))
        fun snapshot() = classes.walkTopDown().filter { it.extension == "class" }.associate { it.relativeTo(classes).path to hash(it.readBytes()) }
        val before = snapshot()
        check(before.isNotEmpty())
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        FlowBusAndroid.configure(FlowBusConfig(logger = FlowBusLogger.None))
        try {
            LegacyAndroidCalls.verify()
            check(snapshot() == before) { "Legacy classes changed at runtime" }
            val report = File(checkNotNull(System.getProperty("flowbus.legacy.report")))
            checkNotNull(report.parentFile).mkdirs()
            report.writeText(buildString {
                appendLine("PASS unchanged 1.1.0 Android suspend/explicit-instance consumer")
                appendLine("candidateCoreSha256=${hash(candidateCore.readBytes())}")
                appendLine("candidateAndroidSha256=${hash(candidateAndroid.readBytes())}")
                before.toSortedMap().forEach { (name, digest) -> appendLine("$digest  $name") }
            })
        } finally {
            Dispatchers.resetMain()
        }
    }
}
