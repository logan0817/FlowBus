package com.logan.flowbus.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalCoroutinesApi::class)
class FlowBusSubscriptionTest {
    private val key = eventKey<Int>("subscription.event")

    @Test
    fun `notification belongs to each actual collection instead of another subscriber`() = runTest {
        val bus = FlowBus()
        val existing = launch(start = CoroutineStart.UNDISPATCHED) { bus.flow(key).collect {} }
        var notifications = 0
        val ready = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val flow = bus.flow(key) {
            notifications++
            ready.complete(Unit)
        }
        val collector = launch { flow.collect { received += it } }
        try {
            assertEquals(0, notifications)
            assertFalse(ready.isCompleted)
            runCurrent()
            ready.await()
            assertEquals(1, notifications)
            bus.emit(key, 1)
            runCurrent()
            assertEquals(listOf(1), received)
            collector.cancelAndJoin()
            val restarted = launch(start = CoroutineStart.UNDISPATCHED) { flow.collect {} }
            try {
                assertEquals(2, notifications)
            } finally {
                restarted.cancelAndJoin()
            }
        } finally {
            collector.cancelAndJoin()
            existing.cancelAndJoin()
        }
    }

    @Test
    fun `flow notifications are isolated across sources and ordinary versus sticky`() = runTest {
        val bus = FlowBus()
        val shared = bus.scoped("shared")
        val owned = bus.openScope("owned")
        val notifications = IntArray(6)
        val flows = listOf(
            bus.flow(key) { notifications[0]++ },
            bus.stickyFlow(key) { notifications[1]++ },
            shared.flow(key) { notifications[2]++ },
            shared.stickyFlow(key) { notifications[3]++ },
            owned.flow(key) { notifications[4]++ },
            owned.stickyFlow(key) { notifications[5]++ }
        )
        val jobs = mutableListOf<Job>()
        try {
            assertTrue(notifications.all { it == 0 })
            flows.forEachIndexed { index, flow ->
                jobs += launch(start = CoroutineStart.UNDISPATCHED) { flow.collect {} }
                assertEquals(1, notifications[index])
                assertTrue(notifications.drop(index + 1).all { it == 0 })
            }
        } finally {
            jobs.forEach { it.cancelAndJoin() }
            owned.closeSuspending()
        }
    }

    @Test
    fun `cancelled collection does not announce and releases its ordinary or sticky slot`() = runTest {
        for (sticky in listOf(false, true)) {
            val bus = FlowBus()
            var notifications = 0
            val flow = if (sticky) bus.stickyFlow(key) { notifications++ }
            else bus.flow(key) { notifications++ }
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                currentCoroutineContext().cancel()
                flow.collect {}
            }
            collector.join()
            assertTrue(collector.isCancelled)
            assertEquals(0, notifications)
            assertEquals(0, bus.inspectChannel(key)?.normalSubscriptionCount)
            assertEquals(0, bus.inspectChannel(key)?.stickySubscriptionCount)
        }
    }

    @Test
    fun `notification does not bypass zero capacity suspend policy`() = runTest {
        for (sticky in listOf(false, true)) {
            val bus = FlowBus(FlowBusConfig(
                normalBufferCapacity = 0,
                stickyReplay = 0,
                stickyExtraBufferCapacity = 0,
                overflowPolicy = BufferOverflow.SUSPEND
            ))
            val received = mutableListOf<Int>()
            val onSubscribed = {
                val result = if (sticky) bus.tryPostStickyResult(key, 1) else bus.tryPostResult(key, 1)
                assertEquals(1, result.subscriptionCount)
                assertFalse(result.accepted)
            }
            val flow = if (sticky) bus.stickyFlow(key, onSubscribed) else bus.flow(key, onSubscribed)
            val collector = launch(start = CoroutineStart.UNDISPATCHED) { flow.collect { received += it } }
            try {
                if (sticky) bus.emitSticky(key, 2) else bus.emit(key, 2)
                runCurrent()
                assertEquals(listOf(2), received)
            } finally {
                collector.cancelAndJoin()
            }
        }
    }

    @Test
    fun `suspending collectors on each source receive events posted after their own notification`() = runTest {
        val bus = FlowBus()
        val shared = bus.scoped("shared")
        val owned = bus.openScope("owned")
        val collectors: List<suspend (() -> Unit, suspend (Int) -> Unit) -> Unit> = listOf(
            { ready, receive -> bus.collectSuspending(key, ready, onReceived = receive) },
            { ready, receive -> bus.collectStickySuspending(key, ready, onReceived = receive) },
            { ready, receive -> shared.collectSuspending(key, ready, onReceived = receive) },
            { ready, receive -> shared.collectStickySuspending(key, ready, onReceived = receive) },
            { ready, receive -> owned.collectSuspending(key, ready, onReceived = receive) },
            { ready, receive -> owned.collectStickySuspending(key, ready, onReceived = receive) }
        )
        val senders: List<() -> Unit> = listOf(
            { bus.post(key, 1) }, { bus.postSticky(key, 1) },
            { shared.post(key, 1) }, { shared.postSticky(key, 1) },
            { owned.post(key, 1) }, { owned.postSticky(key, 1) }
        )
        try {
            collectors.forEachIndexed { index, collect ->
                val received = CompletableDeferred<Int>()
                val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                    collect(senders[index]) { received.complete(it) }
                }
                try {
                    assertEquals(1, received.await())
                } finally {
                    collector.cancelAndJoin()
                }
            }
        } finally {
            owned.closeSuspending()
        }
    }

    @Test
    fun `event callback failures still use the configured source policy after notification`() = runTest {
        val contexts = mutableListOf<FlowBusErrorContext>()
        val bus = FlowBus(FlowBusConfig(errorHandler = FlowBusErrorHandler { context, _ -> contexts += context }))
        val shared = bus.scoped("feature")
        val ready = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val collector = launch {
            shared.collectSuspending(key, onSubscribed = { ready.complete(Unit) }) {
                if (it == 1) throw IllegalArgumentException("callback failed")
                received += it
            }
        }
        try {
            ready.await()
            shared.post(key, 1)
            shared.post(key, 2)
            runCurrent()
            assertEquals(listOf(2), received)
            assertEquals(FlowBusErrorPhase.SubscriberCallback, contexts.single().phase)
            assertEquals("feature", contexts.single().scopeName)
        } finally {
            collector.cancelAndJoin()
        }
        assertEquals(1, contexts.size)
    }

    @Test
    fun `reentrant post from a plain callback is collected sequentially without recursive invocation`() = runTest {
        val bus = FlowBus(FlowBusConfig(normalBufferCapacity = 1, overflowPolicy = BufferOverflow.SUSPEND))
        val ready = CompletableDeferred<Unit>()
        val trace = mutableListOf<String>()
        var callbackActive = false
        val receive: (Int) -> Unit = { value ->
            assertFalse("The preceding callback must return before another invocation", callbackActive)
            callbackActive = true
            try {
                trace += "begin:$value"
                if (value == 1) assertTrue(bus.post(key, 2))
                trace += "end:$value"
            } finally {
                callbackActive = false
            }
        }
        val collector = launch {
            collectFlowBusSequentially(
                flow = bus.flow(key, onSubscribed = { ready.complete(Unit) }),
                eventKey = key,
                onReceived = receive
            )
        }
        try {
            ready.await()
            assertTrue(bus.post(key, 1))
            runCurrent()
            assertEquals(listOf("begin:1", "end:1", "begin:2", "end:2"), trace)
            assertFalse(callbackActive)
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun `subscription exceptions propagate before replay without using event error policy`() = runTest {
        var handledErrors = 0
        val bus = FlowBus(FlowBusConfig(errorHandler = FlowBusErrorHandler { _, _ -> handledErrors++ }))
        bus.postSticky(key, 1)
        val expected = IllegalStateException("subscription failed")
        val received = mutableListOf<Int>()
        val normalFailure = runCatching {
            bus.collectSuspending(key, onSubscribed = { throw expected }) { received += it }
        }.exceptionOrNull()
        val stickyFailure = runCatching {
            bus.collectStickySuspending(key, onSubscribed = { throw expected }) { received += it }
        }.exceptionOrNull()
        assertSame(expected, normalFailure)
        assertSame(expected, stickyFailure)
        assertTrue(received.isEmpty())
        assertEquals(0, handledErrors)
        assertEquals(0, bus.inspectChannel(key)?.normalSubscriptionCount)
        assertEquals(0, bus.inspectChannel(key)?.stickySubscriptionCount)
    }

    @Test
    fun `sticky captures history before notification and allows concurrent live publishing without locks`() = runTest {
        val bus = FlowBus()
        bus.postSticky(key, 1)
        val entered = CountDownLatch(1)
        val published = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val publisher = executor.submit {
            check(entered.await(5, TimeUnit.SECONDS))
            try {
                check(bus.postSticky(key, 2))
            } finally {
                published.countDown()
            }
        }
        val received = mutableListOf<Int>()
        val collector = launch {
            bus.stickyFlow(key) {
                assertTrue(received.isEmpty())
                entered.countDown()
                assertTrue("Publishing must not wait for a lock held during notification", published.await(5, TimeUnit.SECONDS))
            }.collect { received += it }
        }
        try {
            runCurrent()
            publisher.get(5, TimeUnit.SECONDS)
            assertEquals(listOf(1, 2), received)
        } finally {
            collector.cancelAndJoin()
            publisher.cancel(true)
            executor.shutdownNow()
        }
    }

    @Test
    fun `clearing or consuming during sticky notification invalidates only captured history`() = runTest {
        for (consume in listOf(false, true)) {
            val bus = FlowBus()
            bus.postSticky(key, 1)
            var consumed: Int? = null
            val received = bus.stickyFlow(key) {
                if (consume) consumed = bus.consumeStickyLatest(key) else bus.clearSticky(key)
                assertTrue(bus.postSticky(key, 2))
            }.take(1).toList()
            assertEquals(if (consume) 1 else null, consumed)
            assertEquals(listOf(2), received)
            assertEquals(0, bus.inspectChannel(key)?.stickySubscriptionCount)
        }
    }

    @Test
    fun `cancellation from notification prevents captured sticky replay delivery`() = runTest {
        val bus = FlowBus()
        bus.postSticky(key, 1)
        val subscription = Job()
        val received = mutableListOf<Int>()
        val collector = launch(subscription, start = CoroutineStart.UNDISPATCHED) {
            bus.stickyFlow(key) { subscription.cancel() }.collect { received += it }
        }
        collector.join()
        assertTrue(collector.isCancelled)
        assertTrue(received.isEmpty())
        assertEquals(0, bus.inspectChannel(key)?.stickySubscriptionCount)
    }

    @Test
    fun `notification does not attach an old owned flow to a reopened store`() = runTest {
        val bus = FlowBus()
        val owned = bus.openScope("session")
        var notifications = 0
        val oldFlow = owned.flow(key) { notifications++ }
        owned.closeSuspending()
        assertThrows(IllegalStateException::class.java) { owned.flow(key) {} }
        val reopened = bus.openScope("session")
        val received = mutableListOf<Int>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) { oldFlow.collect { received += it } }
        try {
            assertEquals(1, notifications)
            reopened.post(key, 1)
            runCurrent()
            assertTrue(received.isEmpty())
            assertEquals(0, reopened.inspectChannel(key)?.normalSubscriptionCount)
        } finally {
            collector.cancelAndJoin()
            reopened.closeSuspending()
        }
    }
}
