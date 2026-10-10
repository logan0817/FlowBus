package com.logan.flowbus.binaryconsumer

import com.logan.flowbus.core.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.security.MessageDigest

/** 只使用已发布 1.0.7 入口；编译一次，同一批字节码分别运行原始与候选 JAR。 */
object BinaryCoreConsumer {
    @JvmStatic
    fun main(args: Array<String>) {
        val expectedJar = File(checkNotNull(System.getProperty("flowbus.binary.expectedJar"))).canonicalFile
        val actualJar = File(FlowBus::class.java.protectionDomain.codeSource.location.toURI()).canonicalFile
        check(actualJar == expectedJar) { "Expected $expectedJar, loaded $actualJar" }
        val javaVersion = System.getProperty("java.specification.version")
        System.getProperty("flowbus.consumer.expectedJava")?.let { check(javaVersion == it) }
        LegacyJavaCaller.verify()
        runBlocking {
            withTimeout(5_000) {
                verifyInlineAndChannelCalls()
                verifyScopeLifecycle()
                verifyOldCollectorErrorContract()
            }
        }
        val sha = MessageDigest.getInstance("SHA-256").digest(actualJar.readBytes())
            .joinToString("") { "%02x".format(it.toInt() and 255) }
        println("PASS binary consumer Java=$javaVersion coreSha256=$sha")
    }

    private suspend fun verifyInlineAndChannelCalls() = coroutineScope {
        val bus = FlowBus()
        DefaultFlowBus.install(bus)
        val shared = bus.scoped("binary.shared")
        val owned = bus.openScope("binary.owned")
        val channel = eventChannel<Int>("binary.channel")
        // 旧 reified/default-argument 路径会把 @PublishedApi JVM 引用写进调用方字节码。
        val targets = listOf(
            Target({ bus.collect<Int>(eventName = "binary.inline", onReceived = it) },
                { bus.emit(it, eventName = "binary.inline") }),
            Target({ shared.collect<Int>(eventName = "binary.inline", onReceived = it) },
                { shared.emit(it, eventName = "binary.inline") }),
            Target({ owned.collect<Int>(eventName = "binary.inline", onReceived = it) },
                { owned.emit(it, eventName = "binary.inline") }),
            Target({ DefaultFlowBus.collect<Int>(eventName = "binary.inline", onReceived = it) },
                { it.awaitSend(eventName = "binary.inline") }),
            Target({ channel.collectOn(bus, onReceived = it) }, { channel.emitOn(bus, it) }),
            Target({ channel.collectOn(shared, onReceived = it) }, { channel.emitOn(shared, it) }),
            Target({ channel.collectOn(owned, onReceived = it) }, { channel.emitOn(owned, it) }),
            Target({ channel.collect(onReceived = it) }, { channel.emit(it) })
        )
        try {
            for ((index, target) in targets.withIndex()) {
                val received = CompletableDeferred<Int>()
                val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
                    target.collect { received.complete(it) }
                }
                try {
                    target.emit(index)
                    check(received.await() == index)
                } finally { collecting.cancelAndJoin() }
            }
            check("root".sendSticky(eventName = "binary.sticky"))
            check("shared".sendStickyOn(shared, eventName = "binary.sticky"))
            check("owned".sendStickyOn(owned, eventName = "binary.sticky"))
            check(DefaultFlowBus.stickyFlow<String>("binary.sticky").first() == "root")
            check(shared.stickyFlow<String>("binary.sticky").first() == "shared")
            check(owned.stickyFlow<String>("binary.sticky").first() == "owned")
            val replay = CompletableDeferred<String>()
            val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
                owned.collectSticky<String>(eventName = "binary.sticky") { replay.complete(it) }
            }
            try { check(replay.await() == "owned") } finally { collecting.cancelAndJoin() }
            check(owned.consumeStickyLatest<String>("binary.sticky") == "owned")
            check(owned.consumeStickyLatest<String>("binary.sticky") == null)
            channel.postStickyOn(shared, 42)
            check(channel.stickyFlowOn(shared).first() == 42)
            channel.clearStickyOn(shared)
            check(channel.consumeStickyLatestOn(shared) == null)
            channel.removeStickyOn(shared)
            channel.removeEventOn(shared)
        } finally {
            owned.close()
            shared.removeScope()
        }
    }

    private suspend fun verifyScopeLifecycle() = coroutineScope {
        val bus = FlowBus()
        val key = eventKey<String>("binary.scope")
        val owned = bus.openScope("binary.reopen")
        check(owned.postSticky(key, "old"))
        check(owned.closeSuspending().closed && owned.isClosed)
        val reopened = bus.openScope("binary.reopen")
        try { check(reopened.consumeStickyLatest(key) == null) } finally { reopened.close() }
        val parent = Job()
        val bound = bus.openScope("binary.bound", parent)
        parent.complete()
        check(bound.isClosed)
        check(bound.closeSuspending().closed)
        val plain = bus.scoped("binary.remove")
        check(plain.postSticky(key, "removed"))
        bus.removeScope("binary.remove")
        check(plain.consumeStickyLatest(key) == null)
    }

    private suspend fun verifyOldCollectorErrorContract() = coroutineScope {
        val errors = mutableListOf<Throwable>()
        val bus = FlowBus(FlowBusConfig(errorHandler = FlowBusErrorHandler.fromThrowableHandler { errors.add(it) }))
        val key = eventKey<Int>("binary.errors")
        val received = CompletableDeferred<Int>()
        val failure = IllegalStateException("expected legacy callback failure")
        val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.collect(key) {
                if (it == 1) throw failure
                received.complete(it)
            }
        }
        try {
            bus.emit(key, 1)
            bus.emit(key, 2)
            check(received.await() == 2 && errors.single() === failure)
        } finally { collecting.cancelAndJoin() }
        val normal = async(start = CoroutineStart.UNDISPATCHED) { bus.flow<Int>("binary.final").first() }
        try {
            check(7.sendOn(bus, eventName = "binary.final"))
            check(normal.await() == 7)
        } finally { normal.cancelAndJoin() }
    }

    private class Target(val collect: suspend ((Int) -> Unit) -> Unit, val emit: suspend (Int) -> Unit)
}
