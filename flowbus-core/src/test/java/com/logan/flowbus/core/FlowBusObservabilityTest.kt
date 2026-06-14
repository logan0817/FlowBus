package com.logan.flowbus.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FlowBusObservabilityTest {

    @After
    fun tearDown() {
        DefaultFlowBus.resetForTests()
    }

    @Test
    fun `try post result reports accepted event without active subscriber`() {
        val bus = FlowBus()
        val key = eventKey<String>("observability.no.subscriber")

        val result = bus.tryPostResult(key, "value")

        assertTrue(result.accepted)
        assertEquals(0, result.subscriptionCount)
        assertEquals(FlowBusPostOutcome.AcceptedWithoutActiveSubscriber, result.outcome)
        assertEquals("observability.no.subscriber", result.eventName)
        assertNull(result.scopeName)
        assertFalse(result.isSticky)
    }

    @Test
    fun `try post result reports active subscriber`() = runBlocking {
        val bus = FlowBus(
            FlowBusConfig(
                normalBufferCapacity = 1,
                overflowPolicy = BufferOverflow.SUSPEND
            )
        )
        val key = eventKey<String>("observability.active.subscriber")
        val flow = bus.flow(key) as MutableSharedFlow<String>
        val received = async { flow.first() }

        flow.subscriptionCount.first { it > 0 }
        val result = bus.tryPostResult(key, "value")

        assertTrue(result.accepted)
        assertEquals(1, result.subscriptionCount)
        assertEquals(FlowBusPostOutcome.Accepted, result.outcome)
        assertEquals("value", received.await())
    }

    @Test
    fun `try post result works on root scoped scope default bus and channel`() {
        val bus = FlowBus()
        val key = eventKey<String>("observability.api.matrix")
        val scoped = bus.scoped("feature")
        val scope = bus.openScope("session")
        val channel = eventChannel<String>("observability.channel")

        try {
            assertTrue(bus.tryPostResult(key, "root").accepted)
            assertEquals("feature", scoped.tryPostResult(key, "scoped").scopeName)
            assertEquals("session", scope.tryPostResult(key, "scope").scopeName)
            assertTrue(DefaultFlowBus.tryPostResult("default", "observability.default").accepted)
            assertTrue(channel.tryPostResultOn(bus, "channel").accepted)
        } finally {
            scope.close()
            DefaultFlowBus.resetForTests()
        }
    }

    @Test
    fun `inspector exposes subscription and post metrics without payload`() = runBlocking {
        val bus = FlowBus()
        val key = eventKey<String>("diagnostics.metrics")
        val flow = bus.flow(key) as MutableSharedFlow<String>
        val job = launch { flow.collect {} }

        try {
            flow.subscriptionCount.first { it > 0 }
            bus.tryPostResult(key, "secret-payload")

            val event = bus.inspect().root.events.single { it.eventName == "diagnostics.metrics" }

            assertEquals(1, event.subscriptionCount)
            assertEquals(1, event.metrics.acceptedPostCount)
            assertEquals(0, event.metrics.rejectedPostCount)
            assertNotNull(event.metrics.lastPostTimeMillis)
            assertFalse(event.toString().contains("secret-payload"))
        } finally {
            job.cancel()
        }
    }

    @Test
    fun `try post result reports drop oldest policy for active subscribers`() = runBlocking {
        val bus = FlowBus(
            FlowBusConfig(
                normalBufferCapacity = 1,
                overflowPolicy = BufferOverflow.DROP_OLDEST
            )
        )
        val key = eventKey<String>("observability.drop.oldest")
        val flow = bus.flow(key) as MutableSharedFlow<String>
        val job = launch { flow.collect {} }

        try {
            flow.subscriptionCount.first { it > 0 }

            val result = bus.tryPostResult(key, "value")

            assertTrue(result.accepted)
            assertEquals(FlowBusPostOutcome.AcceptedWithDropOldestPolicy, result.outcome)
        } finally {
            job.cancelAndJoin()
        }
    }

    @Test
    fun `inspector keeps normal and sticky metrics separated for same event name`() {
        val bus = FlowBus()
        val key = eventKey<String>("observability.same.name")

        bus.tryPostResult(key, "normal")
        bus.tryPostStickyResult(key, "sticky")

        val event = bus.inspect().root.events.single { it.eventName == "observability.same.name" }

        assertEquals(2, event.metrics.acceptedPostCount)
        assertEquals(1, event.normalMetrics.acceptedPostCount)
        assertEquals(1, event.stickyMetrics.acceptedPostCount)
    }

    @Test
    fun `emit records inspector metrics like non suspending post`() = runBlocking {
        val bus = FlowBus(
            FlowBusConfig(
                normalBufferCapacity = 1,
                overflowPolicy = BufferOverflow.SUSPEND
            )
        )
        val key = eventKey<String>("observability.emit.metrics")

        bus.emit(key, "value")

        val event = bus.inspect().root.events.single { it.eventName == "observability.emit.metrics" }
        assertEquals(1, event.normalMetrics.acceptedPostCount)
        assertEquals(0, event.normalMetrics.rejectedPostCount)
        assertEquals(1, event.metrics.acceptedPostCount)
        assertNotNull(event.metrics.lastPostTimeMillis)
    }

    @Test
    fun `try post result reports rejected by buffer when active subscriber is busy`() = runBlocking {
        val bus = FlowBus(
            FlowBusConfig(
                normalBufferCapacity = 0,
                overflowPolicy = BufferOverflow.SUSPEND
            )
        )
        val key = eventKey<Int>("observability.rejected.buffer")
        val flow = bus.flow(key) as MutableSharedFlow<Int>
        val allowFirstToFinish = CompletableDeferred<Unit>()
        val collectorJob = launch {
            flow.collect {
                allowFirstToFinish.await()
            }
        }

        try {
            flow.subscriptionCount.first { it > 0 }
            val rejected = bus.tryPostResult(key, 1)

            assertFalse(rejected.accepted)
            assertEquals(FlowBusPostOutcome.RejectedByBuffer, rejected.outcome)

            val event = bus.inspect().root.events.single { it.eventName == "observability.rejected.buffer" }
            assertEquals(0, event.normalMetrics.acceptedPostCount)
            assertEquals(1, event.normalMetrics.rejectedPostCount)
            assertEquals(FlowBusPostOutcome.RejectedByBuffer, event.normalMetrics.lastRejectedReason)
        } finally {
            allowFirstToFinish.complete(Unit)
            collectorJob.cancelAndJoin()
        }
    }
}
