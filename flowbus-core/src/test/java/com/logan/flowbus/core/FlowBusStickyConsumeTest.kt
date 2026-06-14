package com.logan.flowbus.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections
import java.io.File

class FlowBusStickyConsumeTest {

    @After
    fun tearDown() {
        DefaultFlowBus.resetForTests()
    }

    @Test
    fun `consume sticky latest returns latest value and clears replay`() {
        val bus = FlowBus()
        val key = eventKey<String>("sticky.consume.latest")

        bus.postSticky(key, "first")
        bus.postSticky(key, "second")

        assertEquals("second", bus.consumeStickyLatest(key))
        assertEquals(emptyList<Any>(), bus.stickyReplayCache(key.name))
        assertNull(bus.consumeStickyLatest(key))
    }

    @Test
    fun `consume sticky latest keeps normal event flow intact`() {
        val bus = FlowBus()
        val key = eventKey<String>("sticky.consume.normal.intact")

        bus.post(key, "normal")
        bus.postSticky(key, "sticky")

        assertEquals("sticky", bus.consumeStickyLatest(key))
        assertTrue(bus.hasEventFlow(key.name, isSticky = false))
    }

    @Test
    fun `consume sticky latest works on scoped scope default bus and channel`() {
        val bus = FlowBus()
        val key = eventKey<String>("sticky.consume.matrix")
        val scoped = bus.scoped("feature")
        val scope = bus.openScope("session")
        val channel = eventChannel<String>("sticky.consume.channel")

        try {
            scoped.postSticky(key, "scoped")
            scope.postSticky(key, "scope")
            DefaultFlowBus.postSticky("default", eventName = "sticky.consume.default")
            channel.postStickyOn(bus, "channel")

            assertEquals("scoped", scoped.consumeStickyLatest(key))
            assertEquals("scope", scope.consumeStickyLatest(key))
            assertEquals("default", DefaultFlowBus.consumeStickyLatest<String>("sticky.consume.default"))
            assertEquals("channel", channel.consumeStickyLatestOn(bus))
        } finally {
            scope.close()
            DefaultFlowBus.resetForTests()
        }
    }

    @Test
    fun `consume sticky latest preserves same-name type protection`() {
        val bus = FlowBus()
        val stringKey = eventKey<String>("sticky.consume.type")
        val intKey = eventKey<Int>("sticky.consume.type")

        bus.postSticky(stringKey, "value")

        try {
            bus.consumeStickyLatest(intKey)
            fail("Expected conflicting sticky consume type to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("sticky.consume.type"))
        }
    }

    @Test
    fun `emit sticky waits when sticky extra buffer is full`() = runBlocking {
        val bus = FlowBus(
            FlowBusConfig(
                stickyReplay = 0,
                stickyExtraBufferCapacity = 1,
                overflowPolicy = BufferOverflow.SUSPEND
            )
        )
        val key = eventKey<Int>("sticky.consume.emit.suspends")
        val flow = bus.stickyFlow(key)
        val received = Collections.synchronizedList(mutableListOf<Int>())
        val firstReceived = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val collectorJob = launch {
            flow.collect { value ->
                received += value
                if (value == 1 && !firstReceived.isCompleted) {
                    firstReceived.complete(Unit)
                    releaseFirst.await()
                }
            }
        }

        try {
            bus.awaitSubscriber(key.name)
            bus.emitSticky(key, 1)
            firstReceived.await()
            bus.emitSticky(key, 2)

            val thirdEmitFinished = CompletableDeferred<Unit>()
            val thirdEmitJob = launch {
                bus.emitSticky(key, 3)
                thirdEmitFinished.complete(Unit)
            }

            yield()
            assertFalse(thirdEmitFinished.isCompleted)

            releaseFirst.complete(Unit)
            withTimeout(1_000) {
                thirdEmitFinished.await()
            }
            thirdEmitJob.cancelAndJoin()
        } finally {
            releaseFirst.complete(Unit)
            collectorJob.cancelAndJoin()
        }
    }

    @Test
    fun `clear sticky invalidates suspended sticky emit before replay is committed`() = runBlocking {
        verifySuspendedStickyEmitIsInvalidated(
            eventName = "sticky.consume.clear.concurrent.emit",
            invalidate = { bus, key ->
                bus.clearSticky(key)
                null
            }
        )
    }

    @Test
    fun `remove sticky invalidates suspended sticky emit on existing flow`() = runBlocking {
        verifySuspendedStickyEmitIsInvalidated(
            eventName = "sticky.consume.remove.concurrent.emit",
            invalidate = { bus, key ->
                bus.removeSticky(key)
                null
            }
        )
    }

    @Test
    fun `consume sticky latest invalidates suspended sticky emit before replay is committed`() = runBlocking {
        verifySuspendedStickyEmitIsInvalidated(
            eventName = "sticky.consume.latest.concurrent.emit",
            expectedConsumedValue = 2,
            invalidate = { bus, key ->
                bus.consumeStickyLatest(key)
            }
        )
    }

    @Test
    fun `sticky internals do not implement kotlinx mutable shared flow`() {
        val mutableSharedFlowImplementations = FlowBusStore::class.java.declaredClasses
            .filter { MutableSharedFlow::class.java.isAssignableFrom(it) }

        assertEquals(emptyList<Class<*>>(), mutableSharedFlowImplementations)
    }

    @Test
    fun `sticky store keeps experimental coroutines opt in local`() {
        val source = File("src/main/java/com/logan/flowbus/core/FlowBusStore.kt").readText()

        assertFalse(source.contains("@file:OptIn(ExperimentalCoroutinesApi::class)"))
    }

    @Test
    fun `sticky consume latest uses one atomic replay reset primitive`() {
        val source = File("src/main/java/com/logan/flowbus/core/FlowBusStore.kt").readText()

        assertTrue(source.contains("consumeLatestAndReset()"))
        assertFalse(source.contains("val latest = flow.replayCache.lastOrNull()"))
    }

    @Test
    fun `sticky replay injection validates generation separately from live emissions`() {
        val source = File("src/main/java/com/logan/flowbus/core/FlowBusStore.kt").readText()

        assertTrue(source.contains("val replayGeneration = delivery.replayGeneration"))
        assertTrue(source.contains("if (replayGeneration == null)"))
        assertTrue(source.contains("replayGeneration == generation &&"))
        assertFalse(source.contains(".transform { stickyValue ->"))
    }

    private suspend fun CoroutineScope.verifySuspendedStickyEmitIsInvalidated(
        eventName: String,
        expectedConsumedValue: Int? = null,
        invalidate: (FlowBus, EventKey<Int>) -> Int?
    ) {
        val bus = FlowBus(
            FlowBusConfig(
                stickyReplay = 1,
                stickyExtraBufferCapacity = 0,
                overflowPolicy = BufferOverflow.SUSPEND
            )
        )
        val key = eventKey<Int>(eventName)
        val flow = bus.stickyFlow(key)
        val received = Collections.synchronizedList(mutableListOf<Int>())
        val firstReceived = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val collectorJob = launch {
            flow.collect { value ->
                received += value
                if (value == 1 && !firstReceived.isCompleted) {
                    firstReceived.complete(Unit)
                    releaseFirst.await()
                }
            }
        }

        try {
            bus.awaitSubscriber(key.name)
            bus.emitSticky(key, 1)
            firstReceived.await()
            bus.emitSticky(key, 2)

            val thirdEmitStarted = CompletableDeferred<Unit>()
            val thirdEmitFinished = CompletableDeferred<Unit>()
            val thirdEmitJob = launch {
                thirdEmitStarted.complete(Unit)
                bus.emitSticky(key, 3)
                thirdEmitFinished.complete(Unit)
            }

            thirdEmitStarted.await()
            yield()
            assertFalse(thirdEmitFinished.isCompleted)

            val consumedValue = invalidate(bus, key)
            if (expectedConsumedValue != null) {
                assertEquals(expectedConsumedValue, consumedValue)
            }
            assertEquals(emptyList<Any>(), bus.stickyReplayCache(key.name))

            releaseFirst.complete(Unit)
            withTimeout(1_000) {
                thirdEmitFinished.await()
            }

            assertEquals(emptyList<Any>(), bus.stickyReplayCache(key.name))
            assertEquals(listOf(1, 2), received)
            thirdEmitJob.cancelAndJoin()
        } finally {
            releaseFirst.complete(Unit)
            collectorJob.cancelAndJoin()
        }
    }

    private suspend fun FlowBus.awaitSubscriber(eventName: String) {
        withTimeout(1_000) {
            while (subscriptionCount(eventName) == 0) {
                yield()
            }
        }
    }
}
