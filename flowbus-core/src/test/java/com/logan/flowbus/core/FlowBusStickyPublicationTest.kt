package com.logan.flowbus.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalCoroutinesApi::class)
class FlowBusStickyPublicationTest {
    @Test
    fun `all sources preserve ordered replay during nested post and emit`() = runBlocking {
        for (sourceKind in listOf("root", "shared", "owned")) {
            for (suspending in listOf(false, true)) {
                val bus = FlowBus(FlowBusConfig(stickyReplay = 2))
                val key = eventKey<Int>("sticky.reentrant.$sourceKind.$suspending")
                val shared = bus.scoped("shared")
                val owned = bus.openScope("owned")
                val source = when (sourceKind) {
                    "shared" -> Source(shared.stickyFlow(key), { shared.postSticky(key, it) }, { shared.emitSticky(key, it) })
                    "owned" -> Source(owned.stickyFlow(key), { owned.postSticky(key, it) }, { owned.emitSticky(key, it) })
                    else -> Source(bus.stickyFlow(key), { bus.postSticky(key, it) }, { bus.emitSticky(key, it) })
                }
                val received = mutableListOf<Int>()
                val collector = launch(Dispatchers.Unconfined) {
                    source.flow.collect { value ->
                        received += value
                        if (value < 3) assertTrue(source.post(value + 1))
                    }
                }
                try {
                    if (suspending) source.emit(1) else assertTrue(source.post(1))
                    assertEquals(listOf(1, 2, 3), received)
                    assertEquals(listOf(2, 3), source.flow.take(2).toList())
                } finally {
                    collector.cancelAndJoin()
                    owned.closeSuspending()
                }
            }
        }
    }

    @Test
    fun `equal payloads remain separate replay publications`() = runBlocking {
        val bus = FlowBus(FlowBusConfig(stickyReplay = 2))
        val key = eventKey<Int>("sticky.equal")
        assertTrue(bus.postSticky(key, 7))
        bus.emitSticky(key, 7)
        assertEquals(listOf(7, 7), bus.stickyFlow(key).take(2).toList())
    }

    @Test
    fun `subscription notification starts after snapshot and outside publication locks`() = runBlocking {
        val bus = FlowBus(FlowBusConfig(stickyReplay = 2))
        val key = eventKey<Int>("sticky.snapshot.reentrant")
        bus.postSticky(key, 1)
        val received = mutableListOf<Int>()
        val nestedReplay = mutableListOf<Int>()
        val collector = launch(Dispatchers.Unconfined) {
            bus.stickyFlow(key) {
                assertTrue(bus.postSticky(key, 2))
            }.collect { value ->
                received += value
                if (value == 1) {
                    launch(Dispatchers.Unconfined) {
                        bus.stickyFlow(key).take(2).toList(nestedReplay)
                    }
                }
            }
        }
        try {
            assertEquals(listOf(1, 2), received)
            assertEquals(listOf(1, 2), nestedReplay)
            assertEquals(1, bus.inspectChannel(key)?.stickySubscriptionCount)
        } finally {
            collector.cancelAndJoin()
        }
    }

    @Test
    fun `publication at native registration boundary is delivered once after captured history`() = runBlocking {
        val bus = FlowBus(FlowBusConfig(stickyReplay = 2))
        val key = eventKey<Int>("sticky.native.registration")
        bus.postSticky(key, 1)
        val native = nativeStickyBacking(bus, key.name)
        val publisherEntered = CountDownLatch(1)
        val publicationFinished = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val publisher = executor.submit {
            check(publisherEntered.await(5, TimeUnit.SECONDS))
            try { check(bus.postSticky(key, 2)) } finally { publicationFinished.countDown() }
        }
        val immediate = object : CoroutineDispatcher() {
            override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
        }
        val countWatcher = launch(immediate) {
            native.subscriptionCount.collect { count ->
                if (count == 1) publisherEntered.countDown()
            }
        }
        val received = mutableListOf<Int>()
        val collector = launch(Dispatchers.Unconfined) {
            bus.stickyFlow(key) {
                assertTrue(publicationFinished.await(5, TimeUnit.SECONDS))
            }.collect { received += it }
        }
        try {
            publisher.get(5, TimeUnit.SECONDS)
            assertEquals(listOf(1, 2), received)
        } finally {
            collector.cancelAndJoin()
            countWatcher.cancelAndJoin()
            publisher.cancel(true)
            executor.shutdownNow()
        }
    }

    @Test
    fun `cancelled subscription and throwing notification release native slots`() = runTest {
        val bus = FlowBus()
        val key = eventKey<Int>("sticky.subscription.failure")
        bus.postSticky(key, 1)
        val expected = IllegalStateException("notification")
        val actual = assertThrows(IllegalStateException::class.java) {
            runBlocking { bus.stickyFlow(key) { throw expected }.take(1).toList() }
        }
        assertTrue(actual === expected)
        assertEquals(0, bus.inspectChannel(key)?.stickySubscriptionCount)
        val parent = Job()
        val cancelled = launch(parent, start = CoroutineStart.UNDISPATCHED) {
            cancel()
            bus.stickyFlow(key) { error("Cancelled subscriber must not be announced") }.collect {}
        }
        cancelled.join()
        assertTrue(cancelled.isCancelled)
        assertEquals(0, bus.inspectChannel(key)?.stickySubscriptionCount)
        parent.cancel()
        assertEquals(listOf(1), bus.stickyFlow(key).take(1).toList())
    }

    @Test
    fun `one immediate subscriber failure and another early completion do not strand remaining subscribers`() = runBlocking {
        supervisorScope {
            val bus = FlowBus()
            val key = eventKey<Int>("sticky.resumption.isolation")
            val immediate = object : CoroutineDispatcher() {
                override fun dispatch(context: CoroutineContext, block: Runnable) = block.run()
            }
            val expected = IllegalStateException("first subscriber failed")
            val failures = mutableListOf<Throwable>()
            val errorHandler = CoroutineExceptionHandler { _, error -> failures += error }
            var failingCalls = 0
            var firstOnlyCompletions = 0
            val firstOnlyValues = mutableListOf<Int>()
            val remainingValues = mutableListOf<Int>()
            val failing = launch(immediate + errorHandler) {
                bus.stickyFlow(key).collect {
                    failingCalls++
                    throw expected
                }
            }
            val firstOnly = launch(immediate) {
                bus.stickyFlow(key).take(1).toList(firstOnlyValues)
                firstOnlyCompletions++
            }
            val remaining = launch(immediate) {
                bus.stickyFlow(key).collect { remainingValues += it }
            }
            try {
                assertEquals(3, bus.inspectChannel(key)?.stickySubscriptionCount)
                assertTrue(bus.postSticky(key, 1))
                assertTrue(failing.isCompleted)
                assertTrue(firstOnly.isCompleted)
                assertEquals(1, failingCalls)
                assertSame(expected, failures.single())
                assertEquals(1, firstOnlyCompletions)
                assertEquals(listOf(1), firstOnlyValues)
                assertEquals(listOf(1), remainingValues)
                assertEquals(1, bus.inspectChannel(key)?.stickySubscriptionCount)
                bus.emitSticky(key, 2)
                assertEquals(listOf(1, 2), remainingValues)
                assertEquals(listOf(2), bus.stickyReplayCache(key.name))
            } finally {
                failing.cancelAndJoin()
                firstOnly.cancelAndJoin()
                remaining.cancelAndJoin()
            }
            assertEquals(0, bus.inspectChannel(key)?.stickySubscriptionCount)
        }
    }

    @Test
    fun `replay live notification and collection completion keep caller dispatcher and context`() = runBlocking {
        val bus = FlowBus()
        val key = eventKey<Int>("sticky.caller.context")
        bus.postSticky(key, 1)
        val collectorThread = AtomicReference<Thread>()
        val dispatcher = Executors.newSingleThreadExecutor { task ->
            Thread(task, "sticky-collector").also(collectorThread::set)
        }.asCoroutineDispatcher()
        val marker = ThreadLocal<String>()
        val replayReceived = CompletableDeferred<Unit>()
        val trace = mutableListOf<String>()
        val collection = async(dispatcher + CoroutineName("sticky-context") + marker.asContextElement("collector")) {
            fun record(stage: String) {
                assertSame(collectorThread.get(), Thread.currentThread())
                assertEquals("collector", marker.get())
                trace += stage
            }
            bus.stickyFlow(key) { record("subscribed") }.take(2).collect { value ->
                assertEquals("sticky-context", currentCoroutineContext()[CoroutineName]?.name)
                record("value:$value")
                if (value == 1) replayReceived.complete(Unit)
            }
            record("complete")
        }
        try {
            withTimeout(5_000) { replayReceived.await() }
            assertTrue(bus.postSticky(key, 2))
            withTimeout(5_000) { collection.await() }
            assertEquals(listOf("subscribed", "value:1", "value:2", "complete"), trace)
            assertEquals(0, bus.inspectChannel(key)?.stickySubscriptionCount)
            assertNull(marker.get())
        } finally {
            collection.cancelAndJoin()
            dispatcher.close()
        }
    }

    @Test
    fun `rejected and cancelled pending emissions do not replace replay`() = runTest {
        val bus = FlowBus(FlowBusConfig(stickyReplay = 1, overflowPolicy = BufferOverflow.SUSPEND))
        val key = eventKey<Int>("sticky.cancelled")
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.stickyFlow(key).collect {
                received += it
                if (it == 1) {
                    entered.complete(Unit)
                    release.await()
                }
            }
        }
        var pending: kotlinx.coroutines.Job? = null
        try {
            bus.emitSticky(key, 1)
            runCurrent()
            entered.await()
            bus.emitSticky(key, 2)
            assertFalse(bus.postSticky(key, 3))
            pending = launch(start = CoroutineStart.UNDISPATCHED) { bus.emitSticky(key, 4) }
            assertFalse(pending.isCompleted)
            pending.cancelAndJoin()
            assertEquals(listOf(2), bus.stickyReplayCache(key.name))
            release.complete(Unit)
            runCurrent()
            bus.emitSticky(key, 5)
            runCurrent()
            assertEquals(listOf(1, 2, 5), received)
            assertEquals(listOf(5), bus.stickyReplayCache(key.name))
        } finally {
            release.complete(Unit)
            pending?.cancelAndJoin()
            collector.cancelAndJoin()
        }
    }

    @Test
    fun `emitter cancelled before dispatched acceptance continuation never commits replay`() = runTest {
        val bus = FlowBus(FlowBusConfig(stickyReplay = 1, overflowPolicy = BufferOverflow.SUSPEND))
        val key = eventKey<Int>("sticky.cancel.before.resume")
        val release = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.stickyFlow(key).collect {
                received += it
                if (it == 1) release.await()
            }
        }
        val dispatcher = PausedDispatcher()
        try {
            bus.emitSticky(key, 1)
            runCurrent()
            bus.emitSticky(key, 2)
            val pending = launch(dispatcher, start = CoroutineStart.UNDISPATCHED) { bus.emitSticky(key, 3) }
            try {
                assertFalse(pending.isCompleted)
                release.complete(Unit)
                runCurrent()
                assertEquals(listOf(1, 2, 3), received)
                pending.cancel()
                dispatcher.runAll()
                pending.join()
                assertTrue(pending.isCancelled)
                // native 已被实时收集不等于发送协程成功返回；prompt cancellation 保持原契约。
                assertEquals(listOf(2), bus.stickyReplayCache(key.name))
            } finally {
                pending.cancel()
                dispatcher.runAll()
                pending.join()
            }
        } finally {
            release.complete(Unit)
            collector.cancelAndJoin()
        }
    }

    @Test
    fun `live callback permits another thread to publish and configure`() = runBlocking {
        val bus = FlowBus(FlowBusConfig(stickyReplay = 2))
        val key = eventKey<Int>("sticky.callback.unlock")
        val entered = CountDownLatch(1)
        val published = CountDownLatch(1)
        val executor = Executors.newSingleThreadExecutor()
        val worker = executor.submit {
            check(entered.await(5, TimeUnit.SECONDS))
            try {
                bus.configureChannel(eventKey<Int>("sticky.other"), FlowBusChannelConfig())
                check(bus.postSticky(key, 2))
            } finally {
                published.countDown()
            }
        }
        val received = mutableListOf<Int>()
        val collector = launch(Dispatchers.Unconfined) {
            bus.stickyFlow(key).collect {
                received += it
                if (it == 1) {
                    entered.countDown()
                    assertTrue("User callback must run outside publication locks", published.await(5, TimeUnit.SECONDS))
                }
            }
        }
        try {
            assertTrue(bus.postSticky(key, 1))
            worker.get(5, TimeUnit.SECONDS)
            assertEquals(listOf(1, 2), received)
            assertEquals(listOf(1, 2), bus.stickyReplayCache(key.name))
        } finally {
            collector.cancelAndJoin()
            worker.cancel(true)
            executor.shutdownNow()
        }
    }

    @Test
    fun `accepted emit order survives reversed producer continuation order`() = runTest {
        for (replay in 1..2) {
            val bus = FlowBus(FlowBusConfig(
                stickyReplay = replay, stickyExtraBufferCapacity = 2 - replay,
                overflowPolicy = BufferOverflow.SUSPEND
            ))
            val key = eventKey<Int>("sticky.producer.order")
            val release = CompletableDeferred<Unit>()
            val received = mutableListOf<Int>()
            val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                bus.stickyFlow(key).collect {
                    received += it
                    if (it == 0) release.await()
                }
            }
            val firstDispatcher = PausedDispatcher()
            val secondDispatcher = PausedDispatcher()
            val completionOrder = mutableListOf<Int>()
            try {
                bus.emitSticky(key, 0)
                runCurrent()
                bus.emitSticky(key, 1)
                bus.emitSticky(key, 2)
                val third = async(firstDispatcher, start = CoroutineStart.UNDISPATCHED) {
                    bus.emitSticky(key, 3)
                    completionOrder += 3
                }
                val fourth = async(secondDispatcher, start = CoroutineStart.UNDISPATCHED) {
                    bus.emitSticky(key, 4)
                    completionOrder += 4
                }
                try {
                    assertFalse(third.isCompleted)
                    assertFalse(fourth.isCompleted)
                    release.complete(Unit)
                    runCurrent()
                    assertEquals(listOf(0, 1, 2, 3, 4), received)
                    assertTrue(completionOrder.isEmpty())
                    secondDispatcher.runAll()
                    firstDispatcher.runAll()
                    third.await()
                    fourth.await()
                    assertEquals(listOf(4, 3), completionOrder)
                    assertEquals(listOf(3, 4).takeLast(replay), bus.stickyReplayCache(key.name))
                    assertTrue(bus.postSticky(key, 5))
                    runCurrent()
                    assertEquals(listOf(4, 5).takeLast(replay), bus.stickyReplayCache(key.name))
                } finally {
                    third.cancel()
                    fourth.cancel()
                    firstDispatcher.runAll()
                    secondDispatcher.runAll()
                    third.join()
                    fourth.join()
                }
            } finally {
                release.complete(Unit)
                collector.cancelAndJoin()
            }
        }
    }

    private class Source(val flow: Flow<Int>, val post: (Int) -> Boolean, val emit: suspend (Int) -> Unit)

    @Suppress("UNCHECKED_CAST")
    private fun nativeStickyBacking(bus: FlowBus, name: String): kotlinx.coroutines.flow.MutableSharedFlow<Any> {
        val rootStore = FlowBus::class.java.getDeclaredField("rootStore").apply { isAccessible = true }.get(bus)
        val flows = rootStore.javaClass.getDeclaredField("stickyEventFlows").apply { isAccessible = true }.get(rootStore)
            as Map<String, Any>
        val flow = checkNotNull(flows[name])
        return flow.javaClass.getDeclaredField("backing").apply { isAccessible = true }.get(flow)
            as kotlinx.coroutines.flow.MutableSharedFlow<Any>
    }

    private class PausedDispatcher : CoroutineDispatcher() {
        private val tasks = ArrayDeque<Runnable>()

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            tasks.addLast(block)
        }

        fun runAll() {
            while (tasks.isNotEmpty()) tasks.removeFirst().run()
        }
    }
}
