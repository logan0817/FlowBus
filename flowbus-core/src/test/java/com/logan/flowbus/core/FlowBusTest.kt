package com.logan.flowbus.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.yield
import kotlinx.coroutines.Job
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlin.coroutines.EmptyCoroutineContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections

class FlowBusTest {

    @Test
    fun `flowbus rejects negative buffer config`() {
        try {
            FlowBus(config = FlowBusConfig(normalBufferCapacity = -1))
            fail("Expected negative normalBufferCapacity to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("normalBufferCapacity"))
        }

        try {
            FlowBus(config = FlowBusConfig(stickyReplay = -1))
            fail("Expected negative stickyReplay to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("stickyReplay"))
        }

        try {
            FlowBus(config = FlowBusConfig(stickyExtraBufferCapacity = -1))
            fail("Expected negative stickyExtraBufferCapacity to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("stickyExtraBufferCapacity"))
        }
    }

    @Test
    fun `flowbus rejects non suspending overflow when normal events have no buffer`() {
        try {
            FlowBus(
                config = FlowBusConfig(
                    normalBufferCapacity = 0,
                    overflowPolicy = BufferOverflow.DROP_OLDEST
                )
            )
            fail("Expected zero normal buffer with DROP_OLDEST to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("normalBufferCapacity"))
            assertTrue(expected.message.orEmpty().contains("overflowPolicy"))
        }
    }

    @Test
    fun `flowbus rejects non suspending overflow when sticky events have no replay or buffer`() {
        try {
            FlowBus(
                config = FlowBusConfig(
                    stickyReplay = 0,
                    stickyExtraBufferCapacity = 0,
                    overflowPolicy = BufferOverflow.DROP_LATEST
                )
            )
            fail("Expected zero sticky replay and buffer with DROP_LATEST to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("stickyReplay"))
            assertTrue(expected.message.orEmpty().contains("stickyExtraBufferCapacity"))
            assertTrue(expected.message.orEmpty().contains("overflowPolicy"))
        }
    }

    @Test
    fun `flowbus rejects blank scope name across public scope apis`() {
        val bus = FlowBus()

        try {
            bus.scoped("   ")
            fail("Expected scoped(blank) to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("scopeName"))
        }

        try {
            bus.openScope("   ")
            fail("Expected openScope(blank) to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("scopeName"))
        }

        try {
            bus.removeScope("   ")
            fail("Expected removeScope(blank) to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("scopeName"))
        }
    }

    @Test
    fun `flowbus owner rejects blank scope name`() {
        try {
            flowBusOwner("   ")
            fail("Expected flowBusOwner(blank) to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("scopeName"))
        }
    }

    @Test
    fun `flowbus string sugar apis reject blank event name across bus variants`() = runBlocking {
        val bus = FlowBus()
        val scopedBus = bus.scoped("feature")
        val scope = bus.openScope("session")

        assertRejectsBlankEventName { bus.post(value = "root", eventName = "   ") }
        assertRejectsBlankEventNameSuspend { bus.emit(value = "root", eventName = "   ") }
        assertRejectsBlankEventName { bus.postSticky(value = "root", eventName = "   ") }
        assertRejectsBlankEventNameSuspend { bus.emitSticky(value = "root", eventName = "   ") }
        assertRejectsBlankEventName { bus.flow<String>(eventName = "   ") }
        assertRejectsBlankEventName { bus.stickyFlow<String>(eventName = "   ") }
        assertRejectsBlankEventName { bus.removeEvent<String>(eventName = "   ") }
        assertRejectsBlankEventName { bus.clearSticky<String>(eventName = "   ") }
        assertRejectsBlankEventName { bus.removeSticky<String>(eventName = "   ") }

        assertRejectsBlankEventName { scopedBus.post(value = "scoped", eventName = "   ") }
        assertRejectsBlankEventNameSuspend { scopedBus.emit(value = "scoped", eventName = "   ") }
        assertRejectsBlankEventName { scopedBus.postSticky(value = "scoped", eventName = "   ") }
        assertRejectsBlankEventNameSuspend { scopedBus.emitSticky(value = "scoped", eventName = "   ") }
        assertRejectsBlankEventName { scopedBus.flow<String>(eventName = "   ") }
        assertRejectsBlankEventName { scopedBus.stickyFlow<String>(eventName = "   ") }
        assertRejectsBlankEventName { scopedBus.removeEvent<String>(eventName = "   ") }
        assertRejectsBlankEventName { scopedBus.clearSticky<String>(eventName = "   ") }
        assertRejectsBlankEventName { scopedBus.removeSticky<String>(eventName = "   ") }

        assertRejectsBlankEventName { scope.post(value = "scope", eventName = "   ") }
        assertRejectsBlankEventNameSuspend { scope.emit(value = "scope", eventName = "   ") }
        assertRejectsBlankEventName { scope.postSticky(value = "scope", eventName = "   ") }
        assertRejectsBlankEventNameSuspend { scope.emitSticky(value = "scope", eventName = "   ") }
        assertRejectsBlankEventName { scope.flow<String>(eventName = "   ") }
        assertRejectsBlankEventName { scope.stickyFlow<String>(eventName = "   ") }
        assertRejectsBlankEventName { scope.removeEvent<String>(eventName = "   ") }
        assertRejectsBlankEventName { scope.clearSticky<String>(eventName = "   ") }
        assertRejectsBlankEventName { scope.removeSticky<String>(eventName = "   ") }
    }

    @Test
    fun `normal post only creates normal flow`() {
        val bus = FlowBus()
        val key = eventKey<String>("demo")

        bus.post(key, "normal")

        assertTrue(bus.hasEventFlow(eventName = key.name, isSticky = false))
        assertFalse(bus.hasEventFlow(eventName = key.name, isSticky = true))
    }

    @Test
    fun `sticky post caches latest value`() {
        val bus = FlowBus()
        val key = eventKey<String>("demo")

        bus.postSticky(key, "first")
        bus.postSticky(key, "second")

        assertTrue(bus.hasEventFlow(eventName = key.name, isSticky = true))
        assertEquals(listOf("second"), bus.stickyReplayCache(key.name))
    }

    @Test
    fun `clear sticky event only clears replay cache`() {
        val bus = FlowBus()
        val key = eventKey<String>("demo")

        bus.postSticky(key, "sticky")
        bus.clearSticky(key)

        assertTrue(bus.hasEventFlow(eventName = key.name, isSticky = true))
        assertEquals(emptyList<Any>(), bus.stickyReplayCache(key.name))
    }

    @Test
    fun `remove sticky event removes cached flow`() {
        val bus = FlowBus()
        val key = eventKey<String>("demo")

        bus.postSticky(key, "sticky")
        bus.removeSticky(key)

        assertFalse(bus.hasEventFlow(eventName = key.name, isSticky = true))
        assertEquals(emptyList<Any>(), bus.stickyReplayCache(key.name))
    }

    @Test
    fun `remove sticky event clears replay cache of existing sticky flow reference`() = runBlocking {
        val bus = FlowBus()
        val key = eventKey<String>("demo")

        bus.postSticky(key, "sticky")
        val existingFlow = bus.stickyFlow(key)

        assertEquals("sticky", existingFlow.first())

        bus.removeSticky(key)

        assertNull(withTimeoutOrNull(100) { existingFlow.first() })
    }

    @Test
    fun `remove event removes only normal flow and keeps sticky and type guard`() {
        val bus = FlowBus()
        val key = eventKey<String>("demo.remove.normal")

        bus.post(key, "normal")
        bus.postSticky(key, "sticky")

        bus.removeEvent(key)

        assertFalse(bus.hasEventFlow(eventName = key.name, isSticky = false))
        assertTrue(bus.hasEventFlow(eventName = key.name, isSticky = true))
        assertEquals(listOf("sticky"), bus.stickyReplayCache(key.name))

        try {
            bus.flow(eventKey<Int>(key.name))
            fail("Expected removeEvent to keep the original key type guard.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains(key.name))
        }
    }

    @Test
    fun `remove event creates a fresh normal flow for later access`() {
        val bus = FlowBus()
        val key = eventKey<String>("demo.remove.recreate")
        val existingFlow = bus.flow(key) as MutableSharedFlow<String>

        bus.removeEvent(key)
        val newFlow = bus.flow(key) as MutableSharedFlow<String>

        assertFalse(existingFlow === newFlow)
        assertTrue(bus.hasEventFlow(eventName = key.name, isSticky = false))
    }

    @Test
    fun `scoped remove event only affects current scope normal flow`() = runBlocking {
        val bus = FlowBus()
        val key = eventKey<String>("demo.remove.scoped")
        val featureA = bus.scoped("feature-a")
        val featureB = bus.scoped("feature-b")
        val featureAFlow = featureA.flow(key) as MutableSharedFlow<String>
        val featureBFlow = featureB.flow(key) as MutableSharedFlow<String>

        bus.post(key, "root")
        featureA.postSticky(key, "sticky-a")

        featureA.removeEvent(key)

        assertTrue(bus.hasEventFlow(eventName = key.name, isSticky = false))
        assertFalse(featureAFlow === featureA.flow(key))
        assertTrue(featureBFlow === featureB.flow(key))
        assertEquals("sticky-a", featureA.stickyFlow(key).first())
    }

    @Test
    fun `closed scope rejects remove event`() {
        val bus = FlowBus()
        val scope = bus.openScope("session")
        val key = eventKey<String>("demo.remove.closed")

        scope.close()

        try {
            scope.removeEvent(key)
            fail("Expected closed FlowBusScope to reject removeEvent.")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("closed"))
        }
    }

    @Test
    fun `scoped bus isolates events from root and other scopes`() = runBlocking {
        val bus = FlowBus()
        val key = eventKey<String>("demo")
        val featureA = bus.scoped("feature-a")
        val featureB = bus.scoped("feature-b")

        bus.postSticky(key, "root")
        featureA.postSticky(key, "feature-a")
        featureB.postSticky(key, "feature-b")

        assertEquals("root", bus.stickyFlow(key).first())
        assertEquals("feature-a", featureA.stickyFlow(key).first())
        assertEquals("feature-b", featureB.stickyFlow(key).first())
    }

    @Test
    fun `owner-backed scope points to the same scoped bus`() = runBlocking {
        val bus = FlowBus()
        val key = eventKey<String>("demo")
        val owner = flowBusOwner("session")

        bus.scoped(owner).postSticky(key, "owner-value")

        assertEquals("owner-value", bus.scoped("session").stickyFlow(key).first())
    }

    @Test
    fun `remove scope clears scoped cache`() {
        val bus = FlowBus()
        val key = eventKey<String>("demo")
        val scope = bus.scoped("session")

        scope.postSticky(key, "cached")
        scope.removeScope()

        assertFalse(bus.hasScope("session"))
    }

    @Test
    fun `remove scope clears replay cache of existing scoped sticky flow reference`() = runBlocking {
        val bus = FlowBus()
        val key = eventKey<String>("demo")
        val scope = bus.scoped("session")

        scope.postSticky(key, "cached")
        val existingFlow = scope.stickyFlow(key)

        assertEquals("cached", existingFlow.first())

        scope.removeScope()

        assertNull(withTimeoutOrNull(100) { existingFlow.first() })
        assertFalse(bus.hasScope("session"))
    }

    @Test
    fun `remove scope closes an open scope handle and releases the name`() {
        val bus = FlowBus()
        val key = eventKey<String>("demo.remove.open.scope")
        val first = bus.openScope("session")

        first.postSticky(key, "cached")

        bus.removeScope("session")

        assertTrue(first.isClosed)
        assertFalse(bus.hasScope("session"))

        try {
            first.post(key, "after-remove")
            fail("Expected removeScope to close the open FlowBusScope handle.")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("closed"))
        }

        val second = bus.openScope("session")
        assertFalse(second.isClosed)
        second.close()
    }

    @Test
    fun `typed event key rejects conflicting payload type for same channel name`() {
        val bus = FlowBus()
        bus.flow(eventKey<String>("user.state"))

        try {
            bus.flow(eventKey<Int>("user.state"))
            fail("Expected an IllegalArgumentException for conflicting event key types.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("user.state"))
        }
    }

    @Test
    fun `normal event flow keeps bounded pending events and drops oldest when overloaded`() = runBlocking {
        val bus = FlowBus(config = FlowBusConfig(normalBufferCapacity = 2))
        val key = eventKey<Int>("demo")
        val flow = bus.flow(key) as MutableSharedFlow<Int>
        val received = Collections.synchronizedList(mutableListOf<Int>())
        val firstReceived = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()

        val job = launch {
            flow.collect { value ->
                received += value
                if (received.size == 1 && !firstReceived.isCompleted) {
                    firstReceived.complete(Unit)
                    releaseFirst.await()
                }
                if (received.size == 3 && !completed.isCompleted) {
                    completed.complete(Unit)
                }
            }
        }

        flow.subscriptionCount.first { it > 0 }
        bus.post(key, 0)
        firstReceived.await()

        bus.post(key, 1)
        bus.post(key, 2)
        bus.post(key, 3)
        bus.post(key, 4)

        releaseFirst.complete(Unit)

        withTimeout(1_000) {
            completed.await()
        }

        assertEquals(listOf(0, 3, 4), received)
        job.cancelAndJoin()
    }

    @Test
    fun `emit suspends until collector is ready when no buffer is available`() = runBlocking {
        val bus = FlowBus(
            config = FlowBusConfig(
                normalBufferCapacity = 0,
                overflowPolicy = kotlinx.coroutines.channels.BufferOverflow.SUSPEND
            )
        )
        val key = eventKey<Int>("demo")
        val flow = bus.flow(key) as MutableSharedFlow<Int>
        val firstHandled = CompletableDeferred<Unit>()
        val allowFirstToFinish = CompletableDeferred<Unit>()
        val secondEmitFinished = CompletableDeferred<Unit>()
        val received = Collections.synchronizedList(mutableListOf<Int>())

        val collectorJob = launch {
            flow.collect { value ->
                received += value
                if (value == 1 && !firstHandled.isCompleted) {
                    firstHandled.complete(Unit)
                    allowFirstToFinish.await()
                }
            }
        }

        flow.subscriptionCount.first { it > 0 }
        val firstEmitJob = launch { bus.emit(key, 1) }
        firstHandled.await()

        val secondEmitJob = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.emit(key, 2)
            secondEmitFinished.complete(Unit)
        }

        assertFalse(secondEmitFinished.isCompleted)

        allowFirstToFinish.complete(Unit)

        withTimeout(1_000) {
            secondEmitFinished.await()
        }

        assertEquals(listOf(1, 2), received)
        firstEmitJob.cancelAndJoin()
        secondEmitJob.cancelAndJoin()
        collectorJob.cancelAndJoin()
    }

    @Test
    fun `scoped emit sticky only updates scoped bus`() = runBlocking {
        val bus = FlowBus()
        val key = eventKey<String>("demo")
        val feature = bus.scoped("feature")

        feature.emitSticky(key, "scoped-value")

        assertEquals("scoped-value", feature.stickyFlow(key).first())
        assertFalse(bus.hasEventFlow(eventName = key.name, isSticky = true))
    }

    @Test
    fun `emit sticky reaches collector when sticky flow has no replay or extra buffer`() = runBlocking {
        val key = eventKey<String>("sticky.rendezvous")
        val bus = FlowBus(
            FlowBusConfig(
                stickyReplay = 0,
                stickyExtraBufferCapacity = 0,
                overflowPolicy = kotlinx.coroutines.channels.BufferOverflow.SUSPEND
            )
        )
        val flow = bus.stickyFlow(key)
        val received = async(start = CoroutineStart.UNDISPATCHED) { flow.first() }

        withTimeout(1_000) {
            bus.emitSticky(key, "value")
            assertEquals("value", received.await())
        }
    }

    @Test
    fun `open scope closes cache and rejects further use`() {
        val bus = FlowBus()
        val key = eventKey<String>("demo")
        val scope = bus.openScope("session")

        scope.postSticky(key, "cached")
        assertTrue(bus.hasScope("session"))

        scope.close()

        assertTrue(scope.isClosed)
        assertFalse(bus.hasScope("session"))

        try {
            scope.post(key, "again")
            fail("Expected closed FlowBusScope to reject further use.")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("closed"))
        }
    }

    @Test
    fun `open scope rejects duplicate closeable owner for the same name`() {
        val bus = FlowBus()
        val first = bus.openScope("session")

        try {
            bus.openScope("session")
            fail("Expected duplicate FlowBusScope owner to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("session"))
        } finally {
            first.close()
        }
    }

    @Test
    fun `closed scope name can be opened again`() {
        val bus = FlowBus()
        val first = bus.openScope("session")

        first.close()
        val second = bus.openScope("session")

        assertFalse(second.isClosed)
        second.close()
    }

    @Test
    fun `close returns immediately and defers cleanup until in-flight scope operations finish`() = runBlocking {
        val key = eventKey<String>("demo")
        val store = FlowBusStore(FlowBusConfig())
        val storeLookupStarted = CompletableDeferred<Unit>()
        val releaseStoreLookup = CompletableDeferred<Unit>()
        val closeActionCalled = CompletableDeferred<Unit>()
        val closeCompleted = CompletableDeferred<Unit>()
        val closeStarted = CompletableDeferred<Unit>()
        val scope = FlowBusScope(
            busScopeName = "session",
            scopedBus = ScopedFlowBus(
                scopeName = "session",
                storeProvider = {
                    if (!storeLookupStarted.isCompleted) {
                        storeLookupStarted.complete(Unit)
                    }
                    runBlocking { releaseStoreLookup.await() }
                    store
                },
                removeScopeAction = {}
            ),
            closeAction = { _, _ ->
                closeActionCalled.complete(Unit)
            }
        )

        val postJob = launch(Dispatchers.Default) {
            scope.post(key, "value")
        }

        storeLookupStarted.await()

        val closeJob = launch(Dispatchers.Default) {
            closeStarted.complete(Unit)
            scope.close()
            closeCompleted.complete(Unit)
        }

        try {
            closeStarted.await()
            yield()
            closeCompleted.await()
            assertTrue(scope.isClosed)
            assertFalse(closeActionCalled.isCompleted)
        } finally {
            if (!releaseStoreLookup.isCompleted) {
                releaseStoreLookup.complete(Unit)
            }
        }

        withTimeout(1_000) {
            closeActionCalled.await()
        }

        postJob.cancelAndJoin()
        closeJob.cancelAndJoin()
        assertTrue(scope.isClosed)
    }

    @Test
    fun `close invalidates scope immediately and removes store after suspended scope emit finishes`() = runBlocking {
        val bus = FlowBus(
            config = FlowBusConfig(
                normalBufferCapacity = 0,
                overflowPolicy = kotlinx.coroutines.channels.BufferOverflow.SUSPEND
            )
        )
        val key = eventKey<Int>("demo")
        val scope = bus.openScope("session")
        val flow = scope.flow(key) as MutableSharedFlow<Int>
        val firstHandled = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val secondEmitCompleted = CompletableDeferred<Unit>()
        val closeCompleted = CompletableDeferred<Unit>()

        val collectorJob = launch {
            flow.collect { value ->
                if (value == 1 && !firstHandled.isCompleted) {
                    firstHandled.complete(Unit)
                    releaseFirst.await()
                }
            }
        }

        flow.subscriptionCount.first { it > 0 }
        val firstEmitJob = launch { scope.emit(key, 1) }
        firstHandled.await()

        val secondEmitJob = launch(start = CoroutineStart.UNDISPATCHED) {
            scope.emit(key, 2)
            secondEmitCompleted.complete(Unit)
        }

        assertFalse(secondEmitCompleted.isCompleted)

        val closeStarted = CompletableDeferred<Unit>()
        val closeJob = launch(Dispatchers.Default) {
            closeStarted.complete(Unit)
            scope.close()
            closeCompleted.complete(Unit)
        }

        try {
            closeStarted.await()
            yield()
            closeCompleted.await()
            assertTrue(scope.isClosed)
            assertTrue(bus.hasScope("session"))
        } finally {
            if (!releaseFirst.isCompleted) {
                releaseFirst.complete(Unit)
            }
        }

        withTimeout(1_000) {
            secondEmitCompleted.await()
            closeCompleted.await()
        }

        firstEmitJob.cancelAndJoin()
        secondEmitJob.cancelAndJoin()
        closeJob.cancelAndJoin()
        collectorJob.cancelAndJoin()
        assertTrue(scope.isClosed)
        assertFalse(bus.hasScope("session"))
    }

    @Test
    fun `closed scope cannot be reopened through scoped owner api`() {
        val bus = FlowBus()
        val scope = bus.openScope("session")
        scope.close()

        try {
            bus.scoped(scope)
            fail("Expected closed FlowBusScope to be rejected by scoped(owner).")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("closed"))
        }
    }
    @Test
    fun `error handler receives scoped sticky context for type cast failure`() = runBlocking {
        var capturedContext: FlowBusErrorContext? = null
        var capturedThrowable: Throwable? = null

        handleReceivedFlowBusEventSequentially<String>(
            value = 123,
            eventKey = eventKey<String>("demo"),
            scopeName = "session",
            isSticky = true,
            errorHandler = FlowBusErrorHandler { context, throwable ->
                capturedContext = context
                capturedThrowable = throwable
            },
            onReceived = { }
        )

        assertTrue(capturedThrowable is ClassCastException)
        assertEquals("demo", capturedContext?.eventName)
        assertEquals(String::class, capturedContext?.expectedValueType)
        assertEquals(Int::class, capturedContext?.actualValueType)
        assertEquals("session", capturedContext?.scopeName)
        assertTrue(capturedContext?.isSticky == true)
        assertEquals(FlowBusErrorPhase.ValueCast, capturedContext?.phase)
        assertNull(capturedContext?.dispatcher)
    }

    @Test
    fun `error handler receives context for subscriber callback failure`() = runBlocking {
        var capturedContext: FlowBusErrorContext? = null
        var capturedThrowable: Throwable? = null

        handleReceivedFlowBusEventSequentially<String>(
            value = "payload",
            eventKey = eventKey<String>("demo.callback"),
            scopeName = "feature-a",
            isSticky = false,
            errorHandler = FlowBusErrorHandler { context, throwable ->
                capturedContext = context
                capturedThrowable = throwable
            },
            onReceived = {
                throw IllegalStateException("boom")
            }
        )

        assertTrue(capturedThrowable is IllegalStateException)
        assertEquals("demo.callback", capturedContext?.eventName)
        assertEquals(String::class, capturedContext?.expectedValueType)
        assertEquals(String::class, capturedContext?.actualValueType)
        assertEquals("feature-a", capturedContext?.scopeName)
        assertTrue(capturedContext?.isSticky == false)
        assertEquals(FlowBusErrorPhase.SubscriberCallback, capturedContext?.phase)
        assertNull(capturedContext?.dispatcher)
    }

    @Test
    fun `class cast inside subscriber callback is reported as subscriber callback failure`() = runBlocking {
        var capturedContext: FlowBusErrorContext? = null
        var capturedThrowable: Throwable? = null

        handleReceivedFlowBusEventSequentially<String>(
            value = "payload",
            eventKey = eventKey<String>("demo.callback.cast"),
            errorHandler = FlowBusErrorHandler { context, throwable ->
                capturedContext = context
                capturedThrowable = throwable
            },
            onReceived = {
                val raw: Any = 123
                raw as String
            }
        )

        assertTrue(capturedThrowable is ClassCastException)
        assertEquals(FlowBusErrorPhase.SubscriberCallback, capturedContext?.phase)
        assertEquals(String::class, capturedContext?.expectedValueType)
        assertEquals(String::class, capturedContext?.actualValueType)
    }
    @Test
    fun `scope bound to job closes automatically when job completes`() {
        val bus = FlowBus()
        val key = eventKey<String>("demo")
        val job = Job()
        val scope = bus.openScope("session", closeWhen = job)

        scope.postSticky(key, "cached")
        assertTrue(bus.hasScope("session"))

        job.complete()

        try {
            scope.post(key, "again")
            fail("Expected auto-closed FlowBusScope to reject further use.")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("closed"))
        }
        scope.awaitClosedForTest()
        assertTrue(scope.isClosed)
        assertFalse(bus.hasScope("session"))
    }

    @Test
    fun `scope bound to coroutine scope closes automatically when parent scope is cancelled`() {
        val bus = FlowBus()
        val parentScope = CoroutineScope(SupervisorJob())
        val scope = bus.openScope("worker", closeWhen = parentScope)

        assertFalse(scope.isClosed)
        parentScope.coroutineContext[Job]!!.cancel()

        scope.awaitClosedForTest()
        assertTrue(scope.isClosed)
        assertFalse(bus.hasScope("worker"))
    }

    @Test
    fun `binding to completed job eventually closes scope`() {
        val bus = FlowBus()
        val job = Job().apply { complete() }
        val scope = bus.openScope("finished").bindTo(job)

        scope.awaitClosedForTest()
        assertTrue(scope.isClosed)
        assertFalse(bus.hasScope("finished"))
        assertEquals(0, scope.lifecycleBindingCountForTest())
    }

    @Test
    fun `binding to coroutine scope without job is rejected`() {
        val bus = FlowBus()
        val scope = bus.openScope("invalid")
        val key = eventKey<String>("invalid.binding.event")
        val noJobScope = object : CoroutineScope {
            override val coroutineContext = EmptyCoroutineContext
        }

        try {
            val error = assertThrows(IllegalArgumentException::class.java) {
                scope.bindTo(noJobScope)
            }
            assertTrue(error.message.orEmpty().contains("Job"))
            assertFalse(scope.isClosed)
            assertEquals(0, scope.lifecycleBindingCountForTest())
            assertTrue(scope.postSticky(key, "still-open"))
            assertEquals("still-open", scope.consumeStickyLatest(key))
        } finally {
            scope.close()
        }
    }

    @Test
    fun `opening scope without job must not retain an unreachable handle`() {
        val bus = FlowBus()
        val noJobScope = object : CoroutineScope {
            override val coroutineContext = EmptyCoroutineContext
        }

        try {
            val error = assertThrows(IllegalArgumentException::class.java) {
                bus.openScope("invalid.creation", closeWhen = noJobScope)
            }
            assertTrue(error.message.orEmpty().contains("Job"))
            assertNull(bus.inspectScope("invalid.creation"))
            bus.openScope("invalid.creation").close()
        } finally {
            bus.removeScope("invalid.creation")
        }
    }

    @Test
    fun `opening owner scope without job does not retain a handle`() {
        val bus = FlowBus()
        val owner = flowBusOwner("invalid.owner.creation")
        val noJobScope = object : CoroutineScope {
            override val coroutineContext = EmptyCoroutineContext
        }

        try {
            val error = assertThrows(IllegalArgumentException::class.java) {
                bus.openScope(owner, closeWhen = noJobScope)
            }
            assertTrue(error.message.orEmpty().contains("Job"))
            assertNull(bus.inspectScope(owner.busScopeName))
            bus.openScope(owner).close()
        } finally {
            bus.removeScope(owner.busScopeName)
        }
    }

    @Test
    fun `failed scope creation preserves existing shared sticky values and configuration`() = runBlocking {
        val bus = FlowBus()
        val owner = flowBusOwner("shared.creation.failure")
        val shared = bus.scoped(owner)
        val key = eventKey<String>("shared.creation.event")
        val noJobScope = object : CoroutineScope {
            override val coroutineContext = EmptyCoroutineContext
        }
        shared.configureChannel(key, FlowBusChannelConfig(stickyReplay = 2, normalBufferCapacity = 3))
        shared.postSticky(key, "cached")
        val originalConfig = shared.inspectChannel(key)

        try {
            assertThrows(IllegalArgumentException::class.java) {
                bus.openScope(owner.busScopeName, closeWhen = noJobScope)
            }
            assertFalse(requireNotNull(bus.inspectScope(owner.busScopeName)).hasOpenScopeHandle)
            assertThrows(IllegalArgumentException::class.java) {
                bus.openScope(owner, closeWhen = noJobScope)
            }
            assertFalse(requireNotNull(bus.inspectScope(owner.busScopeName)).hasOpenScopeHandle)
            assertEquals(originalConfig, shared.inspectChannel(key))
            assertEquals("cached", withTimeout(1_000) { shared.stickyFlow(key).first() })
            val owned = bus.openScope(owner)
            try {
                assertEquals(originalConfig, owned.inspectChannel(key))
                assertEquals("cached", owned.consumeStickyLatest(key))
            } finally {
                owned.close()
            }
        } finally {
            bus.removeScope(owner.busScopeName)
        }
    }

    @Test
    fun `failed scope creation cannot replace or close an existing owned handle`() {
        val bus = FlowBus()
        val owner = flowBusOwner("owned.creation.failure")
        val original = bus.openScope(owner)
        val key = eventKey<String>("owned.creation.event")
        val noJobScope = object : CoroutineScope {
            override val coroutineContext = EmptyCoroutineContext
        }

        try {
            assertThrows(IllegalArgumentException::class.java) {
                bus.openScope(owner.busScopeName, closeWhen = noJobScope)
            }
            assertThrows(IllegalArgumentException::class.java) {
                bus.openScope(owner, closeWhen = noJobScope)
            }
            assertFalse(original.isClosed)
            assertTrue(requireNotNull(bus.inspectScope(owner.busScopeName)).hasOpenScopeHandle)
            assertTrue(original.postSticky(key, "original"))
            assertEquals("original", original.consumeStickyLatest(key))
            assertThrows(IllegalArgumentException::class.java) { bus.openScope(owner) }
        } finally {
            original.close()
        }
        assertNull(bus.inspectScope(owner.busScopeName))
    }

    @Test
    fun `coroutine scope overloads release handles when their jobs complete or cancel`() = runBlocking {
        for (useOwner in listOf(false, true)) {
            for (cancelParent in listOf(false, true)) {
                val bus = FlowBus()
                val owner = flowBusOwner("active.parent.$useOwner.$cancelParent")
                val parent = Job()
                val parentScope = CoroutineScope(parent)
                val scope = if (useOwner) {
                    bus.openScope(owner, parentScope)
                } else {
                    bus.openScope(owner.busScopeName, parentScope)
                }
                try {
                    assertEquals(1, scope.lifecycleBindingCountForTest())
                    if (cancelParent) parent.cancel() else parent.complete()
                    assertTrue(scope.isClosed)
                    assertTrue(withTimeout(1_000) { scope.closeSuspending() }.closed)
                    assertNull(bus.inspectScope(owner.busScopeName))
                    assertEquals(0, scope.lifecycleBindingCountForTest())
                    bus.openScope(owner).close()
                } finally {
                    parent.cancel()
                    scope.close()
                }
            }
        }
    }

    @Test
    fun `coroutine scope overloads with completed jobs leave no handle or binding`() = runBlocking {
        for (useOwner in listOf(false, true)) {
            for (cancelParent in listOf(false, true)) {
                val bus = FlowBus()
                val owner = flowBusOwner("finished.parent.$useOwner.$cancelParent")
                val parent = Job()
                if (cancelParent) parent.cancel() else parent.complete()
                val parentScope = CoroutineScope(parent)
                val scope = if (useOwner) {
                    bus.openScope(owner, parentScope)
                } else {
                    bus.openScope(owner.busScopeName, parentScope)
                }
                try {
                    assertTrue(scope.isClosed)
                    assertTrue(withTimeout(1_000) { scope.closeSuspending() }.closed)
                    assertNull(bus.inspectScope(owner.busScopeName))
                    assertEquals(0, scope.lifecycleBindingCountForTest())
                    bus.openScope(owner).close()
                } finally {
                    scope.close()
                }
            }
        }
    }

    @Test
    fun `closed scope owner is rejected before checking the binding job`() {
        val bus = FlowBus()
        val owner = bus.openScope("closed.binding.owner")
        owner.close()
        val noJobScope = object : CoroutineScope {
            override val coroutineContext = EmptyCoroutineContext
        }
        val parent = Job()

        try {
            for (parentScope in listOf(noJobScope, CoroutineScope(parent))) {
                val error = assertThrows(IllegalArgumentException::class.java) {
                    bus.openScope(owner, parentScope)
                }
                assertTrue(error.message.orEmpty().contains("closed"))
                assertNull(bus.inspectScope(owner.busScopeName))
            }
            bus.openScope(owner.busScopeName).close()
        } finally {
            parent.cancel()
            bus.removeScope(owner.busScopeName)
        }
    }

    @Test
    fun `blank scope names are rejected before checking the binding job`() {
        val bus = FlowBus()
        val noJobScope = object : CoroutineScope {
            override val coroutineContext = EmptyCoroutineContext
        }
        val invalidOwner = NamedFlowBusOwner("   ")

        val nameError = assertThrows(IllegalArgumentException::class.java) {
            bus.openScope("   ", noJobScope)
        }
        assertTrue(nameError.message.orEmpty().contains("scopeName"))
        val ownerError = assertThrows(IllegalArgumentException::class.java) {
            bus.openScope(invalidOwner, noJobScope)
        }
        assertTrue(ownerError.message.orEmpty().contains("scopeName"))
        assertTrue(bus.inspector().snapshot().scopes.isEmpty())
    }

    private fun assertRejectsBlankEventName(action: () -> Unit) {
        try {
            action()
            fail("Expected blank eventName to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("name"))
        }
    }

    private fun FlowBusScope.lifecycleBindingCountForTest(): Int {
        val field = FlowBusScope::class.java.getDeclaredField("lifecycleBindings").apply { isAccessible = true }
        return (field.get(this) as Collection<*>).size
    }

    private fun FlowBusScope.awaitClosedForTest() {
        runBlocking {
            withTimeout(1_000) {
                while (!isClosed) {
                    yield()
                }
            }
        }
    }

    private suspend fun assertRejectsBlankEventNameSuspend(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected blank eventName to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("name"))
        }
    }
}
