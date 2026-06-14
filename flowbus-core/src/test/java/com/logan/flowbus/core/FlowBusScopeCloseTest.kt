package com.logan.flowbus.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock

class FlowBusScopeCloseTest {

    @Test
    fun `try close returns timeout when scope has in flight operation`() = runBlocking {
        val bus = FlowBus(
            FlowBusConfig(
                normalBufferCapacity = 0,
                overflowPolicy = BufferOverflow.SUSPEND
            )
        )
        val scope = bus.openScope("closing.timeout")
        val key = eventKey<Int>("closing.event")
        val flow = scope.flow(key) as MutableSharedFlow<Int>
        val firstHandled = CompletableDeferred<Unit>()
        val allowFirstToFinish = CompletableDeferred<Unit>()
        val secondEmitStarted = CompletableDeferred<Unit>()

        val collectorJob = launch {
            flow.collect { value ->
                if (value == 1 && !firstHandled.isCompleted) {
                    firstHandled.complete(Unit)
                    allowFirstToFinish.await()
                }
            }
        }

        try {
            flow.subscriptionCount.first { it > 0 }
            val firstEmitJob = launch { scope.emit(key, 1) }
            firstHandled.await()
            val secondEmitJob = launch {
                secondEmitStarted.complete(Unit)
                scope.emit(key, 2)
            }
            secondEmitStarted.await()

            val result = scope.tryClose(timeoutMillis = 10)

            assertFalse(result.closed)
            assertEquals(FlowBusCloseOutcome.Timeout, result.outcome)
            assertFalse(scope.isClosed)

            allowFirstToFinish.complete(Unit)
            secondEmitJob.cancelAndJoin()
            firstEmitJob.cancelAndJoin()
        } finally {
            allowFirstToFinish.complete(Unit)
            collectorJob.cancelAndJoin()
            scope.closeSuspending()
        }
    }

    @Test
    fun `close suspending closes scope and removes scoped store`() = runBlocking {
        val bus = FlowBus()
        val scope = bus.openScope("closing.suspending")
        val key = eventKey<String>("closing.suspending.event")

        scope.postSticky(key, "value")
        assertTrue(bus.hasScope("closing.suspending"))

        val result = scope.closeSuspending()

        assertTrue(result.closed)
        assertEquals(FlowBusCloseOutcome.Closed, result.outcome)
        assertTrue(scope.isClosed)
        assertFalse(bus.hasScope("closing.suspending"))
    }

    @Test
    fun `job lifecycle binding does not block job completion while scope has in flight operation`() = runBlocking {
        val bus = FlowBus(
            FlowBusConfig(
                normalBufferCapacity = 0,
                overflowPolicy = BufferOverflow.SUSPEND
            )
        )
        val boundJob = Job()
        val scope = bus.openScope("closing.bound.job", closeWhen = boundJob)
        val key = eventKey<Int>("closing.bound.job.event")
        val flow = scope.flow(key) as MutableSharedFlow<Int>
        val firstHandled = CompletableDeferred<Unit>()
        val allowFirstToFinish = CompletableDeferred<Unit>()
        val secondEmitStarted = CompletableDeferred<Unit>()

        val collectorJob = launch {
            flow.collect { value ->
                if (value == 1 && !firstHandled.isCompleted) {
                    firstHandled.complete(Unit)
                    allowFirstToFinish.await()
                }
            }
        }

        try {
            flow.subscriptionCount.first { it > 0 }
            val firstEmitJob = launch { scope.emit(key, 1) }
            firstHandled.await()
            val secondEmitJob = launch {
                secondEmitStarted.complete(Unit)
                scope.emit(key, 2)
            }
            secondEmitStarted.await()

            val executor = Executors.newSingleThreadExecutor()
            val cancelFuture = executor.submit<Boolean> {
                boundJob.cancel()
                true
            }
            try {
                cancelFuture.get(500, TimeUnit.MILLISECONDS)
            } catch (e: TimeoutException) {
                cancelFuture.cancel(true)
                throw AssertionError("Job completion must not block while FlowBusScope has an in-flight operation.", e)
            } finally {
                executor.shutdownNow()
            }
            assertFalse(scope.isClosed)

            allowFirstToFinish.complete(Unit)
            withTimeout(1_000) {
                while (!scope.isClosed) {
                    yield()
                }
            }

            firstEmitJob.cancelAndJoin()
            secondEmitJob.cancelAndJoin()
        } finally {
            allowFirstToFinish.complete(Unit)
            collectorJob.cancelAndJoin()
            if (!scope.isClosed) {
                scope.closeSuspending()
            }
        }
    }

    @Test
    fun `interrupted auto close wait retries lifecycle close and eventually closes scope`() = runBlocking {
        val config = FlowBusConfig(
            normalBufferCapacity = 0,
            overflowPolicy = BufferOverflow.SUSPEND
        )
        val key = eventKey<String>("closing.auto.interrupted.event")
        val store = FlowBusStore(config)
        val storeLookupStarted = CompletableDeferred<Unit>()
        val releaseStoreLookup = CompletableDeferred<Unit>()
        val autoCloseThread = CompletableDeferred<Thread>()
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "flowbus-auto-close-interrupt-test").also { autoCloseThread.complete(it) }
        }
        val autoCloseDispatcher = executor.asCoroutineDispatcher()
        val closeActionCalled = CompletableDeferred<Unit>()
        val scope = FlowBusScope(
            busScopeName = "closing.auto.interrupted",
            scopedBus = ScopedFlowBus(
                scopeName = "closing.auto.interrupted",
                storeProvider = {
                    storeLookupStarted.complete(Unit)
                    runBlocking { releaseStoreLookup.await() }
                    store
                },
                removeScopeAction = {}
            ),
            closeAction = { _, _ -> closeActionCalled.complete(Unit) },
            autoCloseDispatcher = autoCloseDispatcher
        )
        val boundJob = Job()
        scope.bindTo(boundJob)
        var postJob: Job? = null

        try {
            postJob = launch(Dispatchers.Default) { scope.post(key, "value") }
            storeLookupStarted.await()

            boundJob.cancel()
            scope.awaitClosingForTest()
            autoCloseThread.await().interrupt()

            releaseStoreLookup.complete(Unit)
            postJob.cancelAndJoin()
            scope.awaitClosedForTest()
            assertTrue(scope.isClosed)
            closeActionCalled.await()
        } finally {
            releaseStoreLookup.complete(Unit)
            postJob?.cancelAndJoin()
            if (!scope.isClosed && !scope.isClosingForTest()) {
                scope.closeSuspending()
            }
            autoCloseDispatcher.close()
            executor.shutdownNow()
        }
    }

    @Test
    fun `closing already closed scope reports already closed`() = runBlocking {
        val bus = FlowBus()
        val scope = bus.openScope("closing.already")

        scope.close()

        val result = scope.tryClose(timeoutMillis = 0)

        assertTrue(result.closed)
        assertEquals(FlowBusCloseOutcome.AlreadyClosed, result.outcome)
    }

    @Test
    fun `remove scope uses close path for open scope handle`() = runBlocking {
        val bus = FlowBus()
        val scope = bus.openScope("closing.remove.scope")
        val key = eventKey<String>("closing.remove.scope.event")

        scope.postSticky(key, "value")
        bus.removeScope("closing.remove.scope")

        assertTrue(scope.isClosed)
        assertFalse(bus.hasScope("closing.remove.scope"))
    }

    @Test
    fun `concurrent close reports closing in progress instead of timeout`() = runBlocking {
        val key = eventKey<String>("closing.concurrent.event")
        val store = FlowBusStore(FlowBusConfig())
        val storeLookupStarted = CompletableDeferred<Unit>()
        val releaseStoreLookup = CompletableDeferred<Unit>()
        val scope = FlowBusScope(
            busScopeName = "closing.concurrent",
            scopedBus = ScopedFlowBus(
                scopeName = "closing.concurrent",
                storeProvider = {
                    storeLookupStarted.complete(Unit)
                    runBlocking { releaseStoreLookup.await() }
                    store
                },
                removeScopeAction = {}
            ),
            closeAction = { _, _ -> }
        )

        try {
            val postJob = launch(Dispatchers.Default) { scope.post(key, "value") }
            storeLookupStarted.await()
            val firstClose = async(Dispatchers.Default) { scope.closeSuspending() }
            scope.awaitClosingForTest()

            val secondClose = scope.tryClose(timeoutMillis = 0)

            assertFalse(secondClose.closed)
            assertEquals(FlowBusCloseOutcome.ClosingInProgress, secondClose.outcome)

            releaseStoreLookup.complete(Unit)
            assertEquals(FlowBusCloseOutcome.Closed, firstClose.await().outcome)
            postJob.cancelAndJoin()
        } finally {
            releaseStoreLookup.complete(Unit)
        }
    }

    @Test
    fun `open scope replaces a stale closed handle`() {
        val bus = FlowBus()
        val scope = bus.openScope("closing.stale.handle")

        scope.close()
        bus.putOpenScopeForTest(scope.scopeName, scope)

        val reopened = bus.openScope("closing.stale.handle")

        try {
            assertFalse(reopened.isClosed)
            assertNotSame(scope, reopened)
        } finally {
            reopened.close()
        }
    }

    private suspend fun FlowBusScope.awaitClosingForTest() {
        withTimeout(1_000) {
            while (!isClosingForTest()) {
                yield()
            }
        }
    }

    private suspend fun FlowBusScope.awaitClosedForTest() {
        withTimeout(1_000) {
            while (!isClosed) {
                yield()
            }
        }
    }

    private fun FlowBusScope.isClosingForTest(): Boolean {
        val lockField = FlowBusScope::class.java.getDeclaredField("operationLock").apply { isAccessible = true }
        val closingField = FlowBusScope::class.java.getDeclaredField("isClosing").apply { isAccessible = true }
        val lock = lockField.get(this) as ReentrantLock
        lock.lock()
        return try {
            closingField.getBoolean(this)
        } finally {
            lock.unlock()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun FlowBus.putOpenScopeForTest(scopeName: String, scope: FlowBusScope) {
        val field = FlowBus::class.java.getDeclaredField("openScopes").apply { isAccessible = true }
        val openScopes = field.get(this) as ConcurrentHashMap<String, FlowBusScope>
        openScopes[scopeName] = scope
    }
}
