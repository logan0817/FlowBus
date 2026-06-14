package com.logan.flowbus.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections
import java.util.concurrent.Executors

class FlowBusCollectorTest {

    @Test
    fun `flow bus collect uses configured logger and error handler`() = runBlocking {
        val logger = RecordingLogger()
        val errorHandler = RecordingErrorHandler()
        val bus = FlowBus(
            config = FlowBusConfig(
                logger = logger,
                errorHandler = errorHandler
            )
        )
        val key = eventKey<String>("core.collect")
        val flow = bus.flow(key) as MutableSharedFlow<String>
        val received = Collections.synchronizedList(mutableListOf<String>())
        val completed = CompletableDeferred<Unit>()

        val job = launch {
            bus.collect(key) { value ->
                if (value == "boom") {
                    throw IllegalStateException("boom")
                }
                received += value
                completed.complete(Unit)
            }
        }

        flow.subscriptionCount.first { it > 0 }
        bus.emit(key, "boom")
        bus.emit(key, "ok")

        withTimeout(1_000) {
            completed.await()
        }

        assertEquals(listOf("ok"), received)
        assertEquals(1, logger.records.size)
        assertEquals(1, errorHandler.records.size)
        assertEquals(FlowBusErrorPhase.SubscriberCallback, errorHandler.records.single().context.phase)
        assertEquals("core.collect", errorHandler.records.single().context.eventName)
        assertEquals(String::class, errorHandler.records.single().context.expectedValueType)
        assertEquals(String::class, errorHandler.records.single().context.actualValueType)
        job.cancelAndJoin()
    }

    @Test
    fun `scoped flow bus collect reports scope name from configured error handler`() = runBlocking {
        val errorHandler = RecordingErrorHandler()
        val bus = FlowBus(
            config = FlowBusConfig(
                errorHandler = errorHandler
            )
        )
        val key = eventKey<String>("scoped.collect")
        val scoped = bus.scoped("session")
        val flow = scoped.flow(key) as MutableSharedFlow<String>
        val completed = CompletableDeferred<Unit>()

        val job = launch {
            scoped.collect(key) {
                completed.complete(Unit)
                throw IllegalStateException("boom")
            }
        }

        flow.subscriptionCount.first { it > 0 }
        scoped.emit(key, "boom")

        withTimeout(1_000) {
            completed.await()
        }

        assertEquals(1, errorHandler.records.size)
        assertEquals("session", errorHandler.records.single().context.scopeName)
        assertEquals("scoped.collect", errorHandler.records.single().context.eventName)
        job.cancelAndJoin()
    }

    @Test
    fun `collectFlowBusSequentially preserves handler order`() = runBlocking {
        val flow = MutableSharedFlow<Any>(replay = 2)
        val received = Collections.synchronizedList(mutableListOf<Int>())
        val completed = CompletableDeferred<Unit>()

        val job = launch {
            collectFlowBusSequentially<Int>(
                flow = flow,
                dispatcher = Dispatchers.Default
            ) { value ->
                received += value
                if (received.size == 2 && !completed.isCompleted) {
                    completed.complete(Unit)
                }
            }
        }

        flow.emit(1)
        flow.emit(2)

        withTimeout(1_000) {
            completed.await()
        }

        assertEquals(listOf(1, 2), received)
        job.cancelAndJoin()
    }

    @Test
    fun `collectFlowBusSequentially reports value cast mismatch and continues with next event`() = runBlocking {
        val flow = MutableSharedFlow<Any>(replay = 2)
        val logger = RecordingLogger()
        val errorHandler = RecordingErrorHandler()
        val received = Collections.synchronizedList(mutableListOf<String>())
        val completed = CompletableDeferred<Unit>()

        val job = launch {
            collectFlowBusSequentially<String>(
                flow = flow,
                eventKey = eventKey<String>("demo.cast"),
                scopeName = "session",
                isSticky = true,
                logger = logger,
                errorHandler = errorHandler
            ) { value ->
                received += value
                if (!completed.isCompleted) {
                    completed.complete(Unit)
                }
            }
        }

        flow.emit(123)
        flow.emit("ok")

        withTimeout(1_000) {
            completed.await()
        }

        assertEquals(listOf("ok"), received)
        assertEquals(1, logger.records.size)
        assertEquals("FlowBus", logger.records.single().tag)
        assertTrue(logger.records.single().throwable is ClassCastException)
        assertEquals(1, errorHandler.records.size)
        assertEquals(FlowBusErrorPhase.ValueCast, errorHandler.records.single().context.phase)
        assertEquals("demo.cast", errorHandler.records.single().context.eventName)
        assertEquals(String::class, errorHandler.records.single().context.expectedValueType)
        assertEquals(Int::class, errorHandler.records.single().context.actualValueType)
        assertEquals("session", errorHandler.records.single().context.scopeName)
        assertTrue(errorHandler.records.single().context.isSticky)
        job.cancelAndJoin()
    }

    @Test
    fun `collectFlowBusSequentially reports subscriber callback failure and continues with next event`() = runBlocking {
        val flow = MutableSharedFlow<Any>(replay = 2)
        val logger = RecordingLogger()
        val errorHandler = RecordingErrorHandler()
        val received = Collections.synchronizedList(mutableListOf<String>())
        val completed = CompletableDeferred<Unit>()

        val job = launch {
            collectFlowBusSequentially<String>(
                flow = flow,
                eventKey = eventKey<String>("demo.callback"),
                scopeName = "feature-a",
                logger = logger,
                errorHandler = errorHandler
            ) { value ->
                if (value == "boom") {
                    throw IllegalStateException("boom")
                }
                received += value
                if (!completed.isCompleted) {
                    completed.complete(Unit)
                }
            }
        }

        flow.emit("boom")
        flow.emit("ok")

        withTimeout(1_000) {
            completed.await()
        }

        assertEquals(listOf("ok"), received)
        assertEquals(1, logger.records.size)
        assertTrue(logger.records.single().message.contains("callback failed"))
        assertTrue(logger.records.single().throwable is IllegalStateException)
        assertEquals(1, errorHandler.records.size)
        assertEquals(FlowBusErrorPhase.SubscriberCallback, errorHandler.records.single().context.phase)
        assertEquals("demo.callback", errorHandler.records.single().context.eventName)
        assertEquals(String::class, errorHandler.records.single().context.expectedValueType)
        assertEquals(String::class, errorHandler.records.single().context.actualValueType)
        assertEquals("feature-a", errorHandler.records.single().context.scopeName)
        assertEquals("boom", errorHandler.records.single().throwable.message)
        job.cancelAndJoin()
    }

    @Test
    fun `collectFlowBusSequentially rethrows cancellation exception without logging or error handling`() = runBlocking {
        val logger = RecordingLogger()
        val errorHandler = RecordingErrorHandler()

        try {
            collectFlowBusSequentially<String>(
                flow = flowOf("payload"),
                eventKey = eventKey<String>("demo.cancel"),
                logger = logger,
                errorHandler = errorHandler
            ) {
                throw CancellationException("stop")
            }
            fail("Expected CancellationException to be rethrown.")
        } catch (expected: CancellationException) {
            assertEquals("stop", expected.message)
        }

        assertTrue(logger.records.isEmpty())
        assertTrue(errorHandler.records.isEmpty())
    }

    @Test
    fun `collectFlowBusSequentially dispatches callbacks on provided dispatcher sequentially`() = runBlocking {
        val flow = MutableSharedFlow<Any>(replay = 2)
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "flowbus-collector-test")
        }
        val dispatcher = executor.asCoroutineDispatcher()
        val received = Collections.synchronizedList(mutableListOf<Int>())
        val threadNames = Collections.synchronizedList(mutableListOf<String>())
        val completed = CompletableDeferred<Unit>()

        val job = launch {
            collectFlowBusSequentially<Int>(
                flow = flow,
                dispatcher = dispatcher
            ) { value ->
                received += value
                threadNames += Thread.currentThread().name
                if (received.size == 2 && !completed.isCompleted) {
                    completed.complete(Unit)
                }
            }
        }

        try {
            flow.emit(1)
            flow.emit(2)

            withTimeout(1_000) {
                completed.await()
            }

            assertEquals(listOf(1, 2), received)
            assertTrue(threadNames.all { it.startsWith("flowbus-collector-test") })
        } finally {
            job.cancelAndJoin()
            dispatcher.close()
            executor.shutdownNow()
        }
    }

    private class RecordingLogger : FlowBusLogger {
        val records = Collections.synchronizedList(mutableListOf<LogRecord>())

        override fun warn(tag: String, message: String, throwable: Throwable?) {
            records += LogRecord(tag = tag, message = message, throwable = throwable)
        }
    }

    private class RecordingErrorHandler : FlowBusErrorHandler {
        val records = Collections.synchronizedList(mutableListOf<ErrorRecord>())

        override fun handle(context: FlowBusErrorContext, throwable: Throwable) {
            records += ErrorRecord(context = context, throwable = throwable)
        }
    }

    private data class LogRecord(
        val tag: String,
        val message: String,
        val throwable: Throwable?
    )

    private data class ErrorRecord(
        val context: FlowBusErrorContext,
        val throwable: Throwable
    )
}
