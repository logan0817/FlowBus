package com.logan.flowbus.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.Executors

class FlowBusSuspendingCollectorTest {
    @Test
    fun `suspending callback finishes before next callback starts`() = runBlocking {
        val firstStarted = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val calls = mutableListOf<String>()
        val collecting = async(start = CoroutineStart.UNDISPATCHED) {
            collectFlowBusSuspendingSequentially<Int>(flowOf(1, 2)) { value ->
                calls += "start:$value"
                if (value == 1) {
                    firstStarted.complete(Unit)
                    releaseFirst.await()
                }
                calls += "end:$value"
            }
        }
        try {
            firstStarted.await()
            assertEquals(listOf("start:1"), calls)
            assertFalse(collecting.isCompleted)
            releaseFirst.complete(Unit)
            withTimeout(1_000) { collecting.await() }
            assertEquals(listOf("start:1", "end:1", "start:2", "end:2"), calls)
        } finally {
            collecting.cancelAndJoin()
        }
    }

    @Test
    fun `cancelling suspended callback cancels subscription without reporting error`() = runBlocking {
        val logger = RecordingLogger()
        val errors = mutableListOf<FlowBusErrorContext>()
        val bus = FlowBus(FlowBusConfig(logger = logger, errorHandler = FlowBusErrorHandler { context, _ -> errors += context }))
        val key = eventKey<Int>("suspending.cancel")
        val callbackStarted = CompletableDeferred<Unit>()
        val callbackFinished = CompletableDeferred<Unit>()
        val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.collectSuspending(key) {
                callbackStarted.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    callbackFinished.complete(Unit)
                }
            }
        }
        try {
            bus.emit(key, 1)
            withTimeout(1_000) { callbackStarted.await() }
            withTimeout(1_000) { collecting.cancelAndJoin() }
            assertTrue(callbackFinished.isCompleted)
            assertEquals(0, bus.subscriptionCount(key.name))
            assertTrue(errors.isEmpty())
            assertTrue(logger.failures.isEmpty())
        } finally {
            collecting.cancelAndJoin()
        }
    }

    @Test
    fun `callback cancellation exception propagates unchanged`() = runBlocking {
        val cancellation = CancellationException("stop")
        val logger = RecordingLogger()
        val errors = mutableListOf<FlowBusErrorContext>()
        try {
            collectFlowBusSuspendingSequentially<Int>(
                flowOf(1), logger = logger,
                errorHandler = FlowBusErrorHandler { context, _ -> errors += context }
            ) {
                yield()
                throw cancellation
            }
            fail("Cancellation must propagate")
        } catch (actual: CancellationException) {
            assertTrue(actual === cancellation)
        }
        assertTrue(errors.isEmpty())
        assertTrue(logger.failures.isEmpty())
    }

    @Test
    fun `suspending errors preserve type checks context and continue when handled`() = runBlocking {
        val logger = RecordingLogger()
        val errors = mutableListOf<FlowBusErrorContext>()
        val received = mutableListOf<String>()
        collectFlowBusSuspendingSequentially<String>(
            flow = flowOf(123, "boom", "ok"),
            eventKey = eventKey<String>("suspending.errors"),
            scopeName = "session",
            isSticky = true,
            logger = logger,
            errorHandler = FlowBusErrorHandler { context, _ -> errors += context }
        ) { value ->
            yield()
            if (value == "boom") throw IllegalStateException("boom")
            received += value
        }
        assertEquals(listOf("ok"), received)
        assertEquals(listOf(FlowBusErrorPhase.ValueCast, FlowBusErrorPhase.SubscriberCallback), errors.map { it.phase })
        assertTrue(errors.all { it.eventName == "suspending.errors" && it.scopeName == "session" && it.isSticky })
        assertTrue(errors.all { it.expectedValueType == String::class })
        assertEquals(listOf(Int::class, String::class), errors.map { it.actualValueType })
        assertEquals(2, logger.failures.size)
    }

    @Test
    fun `default rethrow policy stops after suspending callback failure`() = runBlocking {
        val failure = IllegalStateException("boom")
        val received = mutableListOf<Int>()
        try {
            collectFlowBusSuspendingSequentially<Int>(flowOf(1, 2)) {
                received += it
                yield()
                throw failure
            }
            fail("Default error policy must propagate the failure")
        } catch (actual: IllegalStateException) {
            assertTrue(actual === failure)
        }
        assertEquals(listOf(1), received)
    }

    @Test
    fun `callback resumes on selected dispatcher and error context identifies dispatcher`() = runBlocking {
        val executor = Executors.newSingleThreadExecutor { Thread(it, "flowbus-suspending-test") }
        val dispatcher = executor.asCoroutineDispatcher()
        val threads = mutableListOf<Thread>()
        val callbackThread = executor.submit<Thread> { Thread.currentThread() }.get()
        val errors = mutableListOf<FlowBusErrorContext>()
        try {
            collectFlowBusSuspendingSequentially<Int>(
                flowOf(1, 2), dispatcher = dispatcher,
                errorHandler = FlowBusErrorHandler { context, _ -> errors += context }
            ) {
                threads += Thread.currentThread()
                yield()
                threads += Thread.currentThread()
                if (it == 1) throw IllegalStateException("handled")
            }
            assertEquals(4, threads.size)
            assertTrue(threads.all { it === callbackThread })
            assertEquals(dispatcher.toString(), errors.single().dispatcher)
        } finally {
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `scoped sticky subscribers inherit config and isolate stores`() = runBlocking {
        val errors = mutableListOf<FlowBusErrorContext>()
        val bus = FlowBus(FlowBusConfig(errorHandler = FlowBusErrorHandler { context, _ -> errors += context }))
        val key = eventKey<String>("suspending.scoped.sticky")
        val scoped = bus.scoped("shared")
        val owned = bus.openScope("owned")
        bus.postSticky(key, "root")
        scoped.postSticky(key, "shared")
        owned.postSticky(key, "owned")
        val handled = List(2) { CompletableDeferred<String>() }
        val subscriptions = listOf(
            launch(start = CoroutineStart.UNDISPATCHED) {
                scoped.collectStickySuspending(key) {
                    yield()
                    handled[0].complete(it)
                    throw IllegalStateException("shared")
                }
            },
            launch(start = CoroutineStart.UNDISPATCHED) {
                owned.collectStickySuspending(key) {
                    yield()
                    handled[1].complete(it)
                    throw IllegalStateException("owned")
                }
            }
        )
        try {
            withTimeout(1_000) {
                assertEquals("shared", handled[0].await())
                assertEquals("owned", handled[1].await())
            }
            subscriptions.forEach { it.cancelAndJoin() }
            assertEquals(setOf("shared", "owned"), errors.map { it.scopeName }.toSet())
            assertTrue(errors.all { it.isSticky && it.eventName == key.name })
        } finally {
            subscriptions.forEach { it.cancelAndJoin() }
            owned.close()
        }
    }

    @Test
    fun `key reified and channel entry points delegate to chosen target`() = runBlocking {
        DefaultFlowBus.resetForTests()
        val bus = FlowBus()
        DefaultFlowBus.install(bus)
        val scoped = bus.scoped("shared")
        val owned = bus.openScope("owned")
        val channel = eventChannel<Int>("suspending.entrypoints")
        val key = channel.asEventKey()
        val targets = listOf(
            Target({ bus.collectSuspending(key, onReceived = it) }, { bus.emit(key, it) }),
            Target({ scoped.collectSuspending(key, onReceived = it) }, { scoped.emit(key, it) }),
            Target({ owned.collectSuspending(key, onReceived = it) }, { owned.emit(key, it) }),
            Target({ DefaultFlowBus.collectSuspending(key, onReceived = it) }, { DefaultFlowBus.emit(key, it) }),
            Target({ bus.collectSuspending(eventName = key.name, onReceived = it) }, { bus.emit(key, it) }),
            Target({ scoped.collectSuspending(eventName = key.name, onReceived = it) }, { scoped.emit(key, it) }),
            Target({ owned.collectSuspending(eventName = key.name, onReceived = it) }, { owned.emit(key, it) }),
            Target({ DefaultFlowBus.collectSuspending(eventName = key.name, onReceived = it) }, { DefaultFlowBus.emit(key, it) }),
            Target({ channel.collectSuspending(onReceived = it) }, { channel.emit(it) }),
            Target({ channel.collectSuspendingOn(bus, onReceived = it) }, { channel.emitOn(bus, it) }),
            Target({ channel.collectSuspendingOn(scoped, onReceived = it) }, { channel.emitOn(scoped, it) }),
            Target({ channel.collectSuspendingOn(owned, onReceived = it) }, { channel.emitOn(owned, it) }),
            Target({ bus.collectStickySuspending(key, onReceived = it) }, { bus.emitSticky(key, it) }),
            Target({ scoped.collectStickySuspending(key, onReceived = it) }, { scoped.emitSticky(key, it) }),
            Target({ owned.collectStickySuspending(key, onReceived = it) }, { owned.emitSticky(key, it) }),
            Target({ DefaultFlowBus.collectStickySuspending(key, onReceived = it) }, { DefaultFlowBus.emitSticky(key, it) }),
            Target({ bus.collectStickySuspending(eventName = key.name, onReceived = it) }, { bus.emitSticky(key, it) }),
            Target({ scoped.collectStickySuspending(eventName = key.name, onReceived = it) }, { scoped.emitSticky(key, it) }),
            Target({ owned.collectStickySuspending(eventName = key.name, onReceived = it) }, { owned.emitSticky(key, it) }),
            Target({ DefaultFlowBus.collectStickySuspending(eventName = key.name, onReceived = it) }, { DefaultFlowBus.emitSticky(key, it) }),
            Target({ channel.collectStickySuspending(onReceived = it) }, { channel.emitSticky(it) }),
            Target({ channel.collectStickySuspendingOn(bus, onReceived = it) }, { channel.emitStickyOn(bus, it) }),
            Target({ channel.collectStickySuspendingOn(scoped, onReceived = it) }, { channel.emitStickyOn(scoped, it) }),
            Target({ channel.collectStickySuspendingOn(owned, onReceived = it) }, { channel.emitStickyOn(owned, it) })
        )
        try {
            targets.forEachIndexed { index, target ->
                bus.clearSticky(key)
                scoped.clearSticky(key)
                owned.clearSticky(key)
                val received = CompletableDeferred<Int>()
                val subscription = launch(start = CoroutineStart.UNDISPATCHED) {
                    target.collect {
                        yield()
                        received.complete(it)
                    }
                }
                try {
                    target.emit(index)
                    assertEquals(index, withTimeout(1_000) { received.await() })
                } finally {
                    subscription.cancelAndJoin()
                }
            }
        } finally {
            owned.close()
            DefaultFlowBus.resetForTests()
        }
    }

    private class Target(
        val collect: suspend (suspend (Int) -> Unit) -> Unit,
        val emit: suspend (Int) -> Unit
    )

    private class RecordingLogger : FlowBusLogger {
        val failures = mutableListOf<Throwable?>()

        override fun warn(tag: String, message: String, throwable: Throwable?) {
            failures += throwable
        }
    }
}
