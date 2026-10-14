package com.logan.flowbus.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class FlowBusChannelConfigTest {
    private val key = eventKey<Int>("configured.event")
    private val suspendConfig = FlowBusChannelConfig(
        normalBufferCapacity = 0,
        normalOverflowPolicy = BufferOverflow.SUSPEND
    )

    @Test
    fun `configuration binds metadata without creating flows or send metrics`() {
        val bus = FlowBus()
        bus.configureChannel(key, suspendConfig)
        val snapshot = bus.inspect()
        val event = snapshot.root.events.single()
        assertEquals(key.name, event.eventName)
        assertEquals(Int::class.qualifiedName, event.valueTypeName)
        assertFalse(event.hasNormalFlow)
        assertFalse(event.hasStickyFlow)
        assertEquals(0, event.subscriptionCount)
        assertEquals(0L, event.metrics.acceptedPostCount)
        assertEquals(BufferOverflow.DROP_OLDEST, snapshot.config.overflowPolicy)
    }

    @Test
    fun `partial overrides inherit bus defaults and equivalent values are idempotent`() = runTest {
        val bus = FlowBus(FlowBusConfig(stickyReplay = 2, overflowPolicy = BufferOverflow.SUSPEND))
        bus.configureChannel(key, FlowBusChannelConfig(normalBufferCapacity = 1))
        bus.postSticky(key, 1)
        bus.postSticky(key, 2)
        bus.postSticky(key, 3)
        assertEquals(listOf(2, 3), bus.stickyFlow(key).take(2).toList())
        bus.configureChannel(key, FlowBusChannelConfig(
            normalBufferCapacity = 1,
            normalOverflowPolicy = BufferOverflow.SUSPEND,
            stickyReplay = 2,
            stickyExtraBufferCapacity = 0,
            stickyOverflowPolicy = BufferOverflow.SUSPEND
        ))
        assertEquals(BufferOverflow.SUSPEND, bus.tryPostResult(key, 4).overflowPolicy)
    }

    @Test
    fun `invalid configurations do not bind keys flows or counters`() {
        val invalid = listOf(
            FlowBusChannelConfig(normalBufferCapacity = -1),
            FlowBusChannelConfig(stickyReplay = -1),
            FlowBusChannelConfig(stickyExtraBufferCapacity = -1),
            FlowBusChannelConfig(normalBufferCapacity = 0),
            FlowBusChannelConfig(stickyReplay = 0, stickyExtraBufferCapacity = 0),
            FlowBusChannelConfig(stickyReplay = Int.MAX_VALUE, stickyExtraBufferCapacity = 1)
        )
        invalid.forEach { config ->
            val bus = FlowBus()
            assertThrows(IllegalArgumentException::class.java) { bus.configureChannel(key, config) }
            assertTrue(bus.inspect().root.events.isEmpty())
            bus.configureChannel(eventKey<String>(key.name), FlowBusChannelConfig())
            assertTrue(bus.post(eventKey<String>(key.name), "valid after failure"))
        }
    }

    @Test
    fun `first use fixes defaults and both type and policy conflicts are rejected`() {
        val bus = FlowBus()
        bus.flow(key)
        assertThrows(IllegalStateException::class.java) { bus.configureChannel(key, suspendConfig) }
        assertThrows(IllegalArgumentException::class.java) {
            bus.configureChannel(eventKey<String>(key.name), FlowBusChannelConfig())
        }
        bus.configureChannel(key, FlowBusChannelConfig())
        assertEquals(BufferOverflow.DROP_OLDEST, bus.tryPostResult(key, 1).overflowPolicy)
    }

    @Test
    fun `failed typed override of raw key does not partially bind a type`() {
        val bus = FlowBus()
        bus.flow(rawEventKey(key.name))
        assertThrows(IllegalStateException::class.java) { bus.configureChannel(key, suspendConfig) }
        assertNull(bus.inspect().root.events.single().valueTypeName)
        val stringKey = eventKey<String>(key.name)
        bus.configureChannel(stringKey, FlowBusChannelConfig())
        assertEquals(String::class.qualifiedName, bus.inspect().root.events.single().valueTypeName)
        assertThrows(IllegalArgumentException::class.java) { bus.flow(key) }
    }

    @Test
    fun `normal zero buffer rejects tryPost and emit waits for active slow collector`() = runTest {
        val bus = FlowBus()
        bus.configureChannel(key, suspendConfig)
        val release = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.flow(key).collect {
                received += it
                if (it == 1) release.await()
            }
        }
        try {
            bus.emit(key, 1)
            assertEquals(listOf(1), received)
            val result = bus.tryPostResult(key, 2)
            assertFalse(result.accepted)
            assertEquals(FlowBusPostOutcome.RejectedByBuffer, result.outcome)
            assertEquals(BufferOverflow.SUSPEND, result.overflowPolicy)
            val sending = async(start = CoroutineStart.UNDISPATCHED) { bus.emit(key, 3) }
            assertFalse(sending.isCompleted)
            release.complete(Unit)
            sending.await()
            runCurrent()
            assertEquals(listOf(1, 3), received)
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun `capacity one drops oldest independently of another suspend channel`() = runTest {
        val bus = FlowBus()
        val other = eventKey<Int>("other.event")
        bus.configureChannel(key, FlowBusChannelConfig(normalBufferCapacity = 1))
        bus.configureChannel(other, suspendConfig)
        val release = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.flow(key).collect {
                received += it
                if (it == 1) release.await()
            }
        }
        val otherCollector = launch(start = CoroutineStart.UNDISPATCHED) { bus.flow(other).collect {} }
        try {
            bus.post(key, 1)
            runCurrent()
            bus.post(key, 2)
            val dropped = bus.tryPostResult(key, 3)
            assertEquals(FlowBusPostOutcome.AcceptedWithDropOldestPolicy, dropped.outcome)
            assertFalse(bus.post(other, 4))
            release.complete(Unit)
            runCurrent()
            assertEquals(listOf(1, 3), received)
        } finally {
            collector.cancelAndJoin()
            otherCollector.cancelAndJoin()
        }
    }

    @Test
    fun `sticky policy is independent and drop latest replay keeps latest publish`() = runTest {
        val bus = FlowBus()
        bus.configureChannel(key, suspendConfig.copy(stickyOverflowPolicy = BufferOverflow.DROP_LATEST))
        val release = CompletableDeferred<Unit>()
        val live = mutableListOf<Int>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.stickyFlow(key).collect {
                live += it
                if (it == 1) release.await()
            }
        }
        try {
            bus.postSticky(key, 1)
            runCurrent()
            bus.postSticky(key, 2)
            val result = bus.tryPostStickyResult(key, 3)
            assertTrue(result.accepted)
            assertEquals(BufferOverflow.DROP_LATEST, result.overflowPolicy)
            assertEquals(FlowBusPostOutcome.AcceptedWithDropLatestPolicy, result.outcome)
            assertEquals(3, bus.stickyFlow(key).first())
            assertEquals(BufferOverflow.SUSPEND, bus.tryPostResult(key, 4).overflowPolicy)
            release.complete(Unit)
            runCurrent()
            assertEquals(listOf(1, 2), live)
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun `sticky no buffer suspend override applies to emit and does not retain offline values`() = runTest {
        val bus = FlowBus()
        bus.configureChannel(key, FlowBusChannelConfig(
            stickyReplay = 0,
            stickyExtraBufferCapacity = 0,
            stickyOverflowPolicy = BufferOverflow.SUSPEND
        ))
        bus.emitSticky(key, 0)
        val received = mutableListOf<Int>()
        val release = CompletableDeferred<Unit>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.stickyFlow(key).collect {
                received += it
                release.await()
            }
        }
        try {
            assertTrue(received.isEmpty())
            bus.emitSticky(key, 1)
            val sending = async(start = CoroutineStart.UNDISPATCHED) { bus.emitSticky(key, 2) }
            assertFalse(sending.isCompleted)
            assertFalse(bus.tryPostStickyResult(key, 3).accepted)
            release.complete(Unit)
            sending.await()
            runCurrent()
            assertEquals(listOf(1, 2), received)
            assertEquals(0, bus.inspect().root.events.single().stickyReplayCount)
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun `removing flows retains policy and old flow references`() {
        val bus = FlowBus()
        bus.configureChannel(key, suspendConfig)
        val normal = bus.flow(key)
        val sticky = bus.stickyFlow(key)
        bus.clearSticky(key)
        bus.removeSticky(key)
        bus.removeEvent(key)
        assertNotSame(normal, bus.flow(key))
        assertNotSame(sticky, bus.stickyFlow(key))
        assertThrows(IllegalStateException::class.java) { bus.configureChannel(key, FlowBusChannelConfig()) }
        assertEquals(BufferOverflow.SUSPEND, bus.tryPostResult(key, 1).overflowPolicy)
    }

    @Test
    fun `unknown cleanup does not fix defaults or bind a type`() {
        val bus = FlowBus()
        bus.clearSticky(key)
        bus.removeSticky(key)
        bus.removeEvent(key)
        assertNull(bus.consumeStickyLatest(key))
        assertTrue(bus.inspect().root.events.isEmpty())
        bus.configureChannel(eventKey<String>(key.name), suspendConfig)
    }

    @Test
    fun `same named keys stay isolated by bus and scope and rebuild resets override`() {
        val bus = FlowBus()
        val scope = bus.openScope("session")
        scope.configureChannel(key, suspendConfig)
        assertEquals(BufferOverflow.SUSPEND, scope.tryPostResult(key, 1).overflowPolicy)
        assertEquals(BufferOverflow.DROP_OLDEST, bus.tryPostResult(key, 1).overflowPolicy)
        assertEquals(BufferOverflow.DROP_OLDEST, bus.scoped("other").tryPostResult(key, 1).overflowPolicy)
        assertEquals(BufferOverflow.DROP_OLDEST, FlowBus().tryPostResult(key, 1).overflowPolicy)
        val shared = bus.scoped("session")
        shared.configureChannel(key, suspendConfig)
        bus.removeScope("session")
        assertTrue(scope.isClosed)
        assertThrows(IllegalStateException::class.java) { scope.configureChannel(key, suspendConfig) }
        assertEquals(BufferOverflow.DROP_OLDEST, shared.tryPostResult(key, 2).overflowPolicy)
        val reopened = bus.openScope("session")
        try {
            reopened.configureChannel(key, FlowBusChannelConfig())
        } finally {
            reopened.close()
        }
    }

    @Test
    fun `job bound scope releases configuration metadata on close`() = runBlocking {
        val bus = FlowBus()
        val job = Job()
        val scope = bus.openScope("bound", job)
        scope.configureChannel(key, suspendConfig)
        assertEquals(1, bus.inspectScope("bound")!!.events.size)
        job.cancel()
        assertTrue(withTimeout(1_000) { scope.closeSuspending() }.closed)
        assertNull(bus.inspectScope("bound"))
    }

    @Test
    fun `configuration competing with first use is atomic`() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            repeat(100) {
                val bus = FlowBus()
                val start = CountDownLatch(1)
                val configured = executor.submit<Boolean> {
                    check(start.await(5, TimeUnit.SECONDS))
                    try {
                        bus.configureChannel(key, suspendConfig)
                        true
                    } catch (_: IllegalStateException) {
                        false
                    }
                }
                val sent = executor.submit<FlowBusPostResult> {
                    check(start.await(5, TimeUnit.SECONDS))
                    bus.tryPostResult(key, 1)
                }
                start.countDown()
                val policy = if (configured.get(5, TimeUnit.SECONDS)) BufferOverflow.SUSPEND else BufferOverflow.DROP_OLDEST
                assertEquals(policy, sent.get(5, TimeUnit.SECONDS).overflowPolicy)
                assertEquals(policy, bus.tryPostResult(key, 2).overflowPolicy)
                assertEquals(1, bus.inspect().root.events.size)
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `competing typed configurations publish one complete binding`() {
        val executor = Executors.newFixedThreadPool(2)
        try {
            repeat(100) {
                val bus = FlowBus()
                val start = CountDownLatch(1)
                val intResult = executor.submit<Boolean> {
                    check(start.await(5, TimeUnit.SECONDS))
                    runCatching { bus.configureChannel(key, suspendConfig) }.isSuccess
                }
                val stringKey = eventKey<String>(key.name)
                val stringResult = executor.submit<Boolean> {
                    check(start.await(5, TimeUnit.SECONDS))
                    runCatching { bus.configureChannel(stringKey, FlowBusChannelConfig()) }.isSuccess
                }
                start.countDown()
                val intWon = intResult.get(5, TimeUnit.SECONDS)
                assertNotEquals(intWon, stringResult.get(5, TimeUnit.SECONDS))
                if (intWon) {
                    assertEquals(BufferOverflow.SUSPEND, bus.tryPostResult(key, 1).overflowPolicy)
                    assertThrows(IllegalArgumentException::class.java) { bus.post(stringKey, "wrong") }
                } else {
                    assertEquals(BufferOverflow.DROP_OLDEST, bus.tryPostResult(stringKey, "ok").overflowPolicy)
                    assertThrows(IllegalArgumentException::class.java) { bus.post(key, 1) }
                }
            }
        } finally {
            executor.shutdownNow()
        }
    }

    @Test
    fun `close defers pinned configuration cleanup and never configures the rebuilt store`() = runBlocking {
        val store = FlowBusStore(FlowBusConfig())
        val rebuiltStore = FlowBusStore(FlowBusConfig())
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cleaned = CompletableDeferred<Unit>()
        val scope = FlowBusScope(
            busScopeName = "configuring",
            scopedBus = ScopedFlowBus("configuring", { rebuiltStore }, {}),
            operationScopedBusProvider = {
                ScopedFlowBus("configuring", {
                    entered.complete(Unit)
                    runBlocking { release.await() }
                    store
                }, {})
            },
            closeAction = { _, _ -> store.clearAll(); cleaned.complete(Unit) }
        )
        val configuring = async(Dispatchers.Default) { scope.configureChannel(key, suspendConfig) }
        try {
            withTimeout(1_000) { entered.await() }
            scope.close()
            assertTrue(scope.isClosed)
            assertFalse(cleaned.isCompleted)
            // 同名当前视图已指向新 store；旧在途配置只能作用于 operation provider 固定的原 store。
            assertEquals(BufferOverflow.DROP_OLDEST, rebuiltStore.postResult(key, 1, false, "configuring").overflowPolicy)
            assertThrows(IllegalStateException::class.java) { scope.configureChannel(key, FlowBusChannelConfig()) }
            release.complete(Unit)
            withTimeout(1_000) { configuring.await(); cleaned.await() }
            assertTrue(store.snapshot().isEmpty())
            rebuiltStore.configureChannel(key, FlowBusChannelConfig())
            assertEquals(BufferOverflow.DROP_OLDEST, rebuiltStore.postResult(key, 2, false, "configuring").overflowPolicy)
        } finally {
            release.complete(Unit)
            configuring.cancelAndJoin()
            scope.close()
        }
    }
}
