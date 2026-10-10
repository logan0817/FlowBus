package com.logan.flowbus.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.io.DataInputStream

class FlowBusCompatibilityTest {
    @Test
    fun `store bytecode does not call concurrent map methods introduced in Android API 24`() {
        listOf(FlowBus::class.java, FlowBusStore::class.java).forEach { type ->
            val resource = "/${type.name.replace('.', '/')}.class"
            val stream = checkNotNull(type.getResourceAsStream(resource))
            DataInputStream(stream).use { input ->
                assertEquals(0xCAFEBABE.toInt(), input.readInt())
                input.readUnsignedShort()
                assertEquals(52, input.readUnsignedShort())
                val constants = arrayOfNulls<Any>(input.readUnsignedShort())
                val methods = mutableListOf<Pair<Int, Int>>()
                var index = 1
                while (index < constants.size) {
                    when (val tag = input.readUnsignedByte()) {
                        1 -> constants[index] = input.readUTF()
                        3, 4 -> input.readInt()
                        5, 6 -> { input.readLong(); index++ }
                        7, 8, 16, 19, 20 -> constants[index] = input.readUnsignedShort()
                        9, 10, 11, 12, 17, 18 -> {
                            val entry = input.readUnsignedShort() to input.readUnsignedShort()
                            constants[index] = entry
                            if (tag == 10 || tag == 11) methods += entry
                        }
                        15 -> { input.readUnsignedByte(); input.readUnsignedShort() }
                        else -> error("Unsupported class constant tag $tag")
                    }
                    index++
                }
                // 检查编译产物的方法引用，避免源码检查遗漏扩展函数编译后的平台调用。
                methods.forEach { (ownerIndex, signatureIndex) ->
                    val owner = constants[constants[ownerIndex] as Int] as String
                    @Suppress("UNCHECKED_CAST")
                    val signature = constants[signatureIndex] as Pair<Int, Int>
                    val name = constants[signature.first] as String
                    assertFalse(
                        "${type.name} calls API 24 method $owner.$name",
                        owner == "java/util/concurrent/ConcurrentHashMap" &&
                            name in setOf("compute", "computeIfPresent", "computeIfAbsent", "merge")
                    )
                }
            }
        }
    }

    @Test
    fun `concurrent same name normal channels and scoped stores share one flow`() {
        val bus = FlowBus()
        val key = eventKey<Int>("compat.concurrent")
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        try {
            val results = List(32) {
                executor.submit<List<kotlinx.coroutines.flow.Flow<Int>>> {
                    check(start.await(1, TimeUnit.SECONDS))
                    listOf(bus.flow(key), bus.scoped("compat.scope").flow(key))
                }
            }
            start.countDown()
            val flows = results.map { it.get(5, TimeUnit.SECONDS) }
            flows.forEach {
                assertSame(flows.first()[0], it[0])
                assertSame(flows.first()[1], it[1])
            }
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
    }

    @Test
    fun `concurrent sticky creation keeps every collector on the same live channel`() = runBlocking {
        val bus = FlowBus()
        val key = eventKey<Int>("compat.concurrent.sticky")
        val executor = Executors.newFixedThreadPool(8)
        val start = CountDownLatch(1)
        val flows = try {
            val results = List(32) {
                executor.submit<kotlinx.coroutines.flow.Flow<Int>> {
                    check(start.await(1, TimeUnit.SECONDS))
                    bus.stickyFlow(key)
                }
            }
            start.countDown()
            results.map { it.get(5, TimeUnit.SECONDS) }
        } finally {
            start.countDown()
            executor.shutdownNow()
        }
        val received = flows.map { CompletableDeferred<Int>() }
        val jobs = flows.mapIndexed { index, flow ->
            launch(start = CoroutineStart.UNDISPATCHED) { received[index].complete(flow.first()) }
        }
        try {
            assertEquals(32, bus.subscriptionCount(key.name))
            bus.postSticky(key, 42)
            withTimeout(1_000) { received.forEach { assertEquals(42, it.await()) } }
        } finally {
            jobs.forEach { it.cancelAndJoin() }
        }
    }

    @Test
    fun `normal emit without subscribers does not retain value even under suspend policy`() = runBlocking {
        val bus = FlowBus(FlowBusConfig(overflowPolicy = BufferOverflow.SUSPEND))
        val key = eventKey<Int>("compat.no.subscriber")
        bus.emit(key, 1)
        val received = CompletableDeferred<Int>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) { received.complete(bus.flow(key).first()) }
        try {
            assertFalse(received.isCompleted)
            bus.emit(key, 2)
            assertEquals(2, withTimeout(1_000) { received.await() })
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `drop latest sticky replay tracks latest accepted publish independently of live delivery`() = runBlocking {
        val bus = FlowBus(FlowBusConfig(overflowPolicy = BufferOverflow.DROP_LATEST))
        val key = eventKey<Int>("compat.sticky.drop.latest")
        val first = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val second = CompletableDeferred<Unit>()
        val live = mutableListOf<Int>()
        val job = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.stickyFlow(key).collect {
                live += it
                if (it == 1) { first.complete(Unit); release.await() }
                if (it == 2) second.complete(Unit)
            }
        }
        try {
            assertTrue(bus.postSticky(key, 1))
            first.await()
            assertTrue(bus.postSticky(key, 2))
            assertTrue(bus.postSticky(key, 3))
            assertEquals(3, bus.stickyFlow(key).first())
            release.complete(Unit)
            withTimeout(1_000) { second.await() }
            assertEquals(listOf(1, 2), live)
        } finally {
            release.complete(Unit)
            job.cancelAndJoin()
        }
    }
}
