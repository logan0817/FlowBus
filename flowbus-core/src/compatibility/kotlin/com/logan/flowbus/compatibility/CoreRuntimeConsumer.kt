package com.logan.flowbus.compatibility

import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.collectSuspendingOn
import com.logan.flowbus.core.collectStickySuspending
import com.logan.flowbus.core.collectSuspending
import com.logan.flowbus.core.emitOn
import com.logan.flowbus.core.eventChannel
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/**
 * 可由独立 Kotlin 编译器复用的真实消费者；仅依赖公开 core API 与 Coroutines。
 * 通过 flowbus.consumer.expectedJava 系统属性检查 JVM，不改变库的编译工具链。
 */
object CoreRuntimeConsumer {
    @JvmStatic
    fun main(args: Array<String>) {
        val runtimeVersion = System.getProperty("java.specification.version")
        System.getProperty("flowbus.consumer.expectedJava")?.let { expected ->
            check(runtimeVersion == expected) {
                "Expected Java $expected, but consumer runs on Java $runtimeVersion"
            }
        }
        CoreJavaConsumer.verify()
        runBlocking {
            withTimeout(5_000) {
                verifyNormalAndSticky()
                verifyScopedOwnership()
                verifyCollectorsAndChannel()
            }
        }
        println("Core consumer passed on Java $runtimeVersion")
    }

    private suspend fun verifyNormalAndSticky() = coroutineScope {
        val bus = FlowBus()
        val key = eventKey<Int>("consumer.key")
        // UNDISPATCHED 确保普通订阅先就绪，避免用任意延时掩盖发送竞态。
        val normal = async(start = CoroutineStart.UNDISPATCHED) { bus.flow(key).first() }
        try {
            check(bus.post(key, 11))
            check(normal.await() == 11)
        } finally {
            normal.cancelAndJoin()
        }
        check(bus.postSticky(key, 12))
        check(bus.stickyFlow(key).first() == 12)
        check(bus.consumeStickyLatest(key) == 12)
        check(bus.consumeStickyLatest(key) == null)
    }

    private suspend fun verifyScopedOwnership() = coroutineScope {
        val bus = FlowBus()
        val key = eventKey<String>("consumer.scope")
        val scoped = bus.scoped("shared")
        val owned = bus.openScope("owned")
        try {
            check(bus.postSticky(key, "root"))
            check(scoped.postSticky(key, "shared"))
            check(owned.postSticky(key, "owned"))
            check(bus.stickyFlow(key).first() == "root")
            check(scoped.stickyFlow(key).first() == "shared")
            check(owned.stickyFlow(key).first() == "owned")
            check(owned.closeSuspending().closed)
            check(owned.isClosed)
            val reopened = bus.openScope("owned")
            try {
                check(reopened.consumeStickyLatest(key) == null)
                check(reopened.postSticky(key, "new"))
                check(reopened.stickyFlow(key).first() == "new")
            } finally {
                reopened.close()
            }
            check(scoped.consumeStickyLatest(key) == "shared")
            scoped.removeScope()
            check(scoped.consumeStickyLatest(key) == null)
        } finally {
            owned.close()
            scoped.removeScope()
        }
    }

    private suspend fun verifyCollectorsAndChannel() = coroutineScope {
        val bus = FlowBus()
        val key = eventKey<Int>("consumer.old.collect")
        val oldReceived = CompletableDeferred<Int>()
        val oldCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.collect(key) { oldReceived.complete(it) }
        }
        try {
            bus.emit(key, 21)
            check(oldReceived.await() == 21)
        } finally {
            oldCollector.cancelAndJoin()
        }

        val channel = eventChannel<Int>("consumer.suspending.channel")
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val bothHandled = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val channelCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            channel.collectSuspendingOn(bus) {
                if (it == 22) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                received += it
                if (received.size == 2) bothHandled.complete(Unit)
            }
        }
        try {
            channel.emitOn(bus, 22)
            firstStarted.await()
            channel.emitOn(bus, 23)
            check(received.isEmpty())
            releaseFirst.complete(Unit)
            bothHandled.await()
            check(received == listOf(22, 23))
        } finally {
            channelCollector.cancelAndJoin()
        }

        val reifiedReceived = CompletableDeferred<String>()
        val reifiedCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.collectSuspending<String>(eventName = "consumer.reified") {
                yield()
                reifiedReceived.complete(it)
            }
        }
        try {
            bus.emit(eventKey<String>("consumer.reified"), "reified")
            check(reifiedReceived.await() == "reified")
        } finally {
            reifiedCollector.cancelAndJoin()
        }

        val stickyReceived = CompletableDeferred<String>()
        val stickyKey = eventKey<String>("consumer.reified.sticky")
        check(bus.postSticky(stickyKey, "sticky"))
        val stickyCollector = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.collectStickySuspending<String>(eventName = stickyKey.name) {
                yield()
                stickyReceived.complete(it)
            }
        }
        try {
            check(stickyReceived.await() == "sticky")
        } finally {
            stickyCollector.cancelAndJoin()
        }
    }
}
