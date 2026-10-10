package com.logan.flowbus.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
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
    fun `explicit close during cancelled suspending wait remains permanent`() = runBlocking {
        verifyPermanentCloseDuringSuspendingWait(removeScope = false)
    }

    @Test
    fun `scope removal during cancelled suspending wait remains permanent`() = runBlocking {
        verifyPermanentCloseDuringSuspendingWait(removeScope = true)
    }

    @Test
    fun `explicit close during timed wait survives timeout`() = runBlocking {
        withBlockedScope("closing.explicit.timeout") { bus, scope, sender ->
            val closer = async(Dispatchers.Default) { scope.tryClose(1_000) }
            try {
                scope.awaitClosingForTest()
                scope.close()
                assertEquals(FlowBusCloseOutcome.Timeout, withTimeout(2_000) { closer.await() }.outcome)
                assertPermanentCloseAndReopen(bus, scope, sender)
            } finally {
                closer.cancelAndJoin()
            }
        }
    }

    @Test
    fun `explicit close during timed wait survives thread interruption`() = runBlocking {
        withBlockedScope("closing.explicit.interrupted") { bus, scope, sender ->
            val interrupted = CompletableDeferred<Boolean>()
            val closeThread = Thread {
                try {
                    scope.tryClose(5_000)
                    interrupted.complete(false)
                } catch (_: InterruptedException) {
                    interrupted.complete(Thread.currentThread().isInterrupted)
                } catch (failure: Throwable) {
                    interrupted.completeExceptionally(failure)
                }
            }
            closeThread.start()
            try {
                scope.awaitClosingForTest()
                scope.close()
                closeThread.interrupt()
                assertTrue(withTimeout(1_000) { interrupted.await() })
                assertPermanentCloseAndReopen(bus, scope, sender)
            } finally {
                closeThread.interrupt()
                closeThread.join(1_000)
            }
        }
    }

    private suspend fun verifyPermanentCloseDuringSuspendingWait(removeScope: Boolean) {
        withBlockedScope("closing.explicit.cancel.$removeScope") { bus, scope, sender ->
            val closer = launch(start = CoroutineStart.UNDISPATCHED) { scope.closeSuspending() }
            try {
                scope.awaitClosingForTest()
                if (removeScope) bus.removeScope(scope.scopeName) else scope.close()
                withTimeout(1_000) { closer.cancelAndJoin() }
                assertPermanentCloseAndReopen(bus, scope, sender)
            } finally {
                closer.cancelAndJoin()
            }
        }
    }

    private suspend fun assertPermanentCloseAndReopen(bus: FlowBus, scope: FlowBusScope, sender: Job) {
        assertTrue(scope.isClosed)
        assertFalse(sender.isCompleted)
        val key = eventKey<String>("closing.explicit.state")
        val rejection = runCatching { scope.postSticky(key, "rejected") }.exceptionOrNull()
        assertTrue(rejection is IllegalStateException)
        val reopened = bus.openScope(scope.scopeName)
        try {
            assertTrue(reopened.postSticky(key, "new store"))
            sender.cancelAndJoin()
            assertTrue(withTimeout(1_000) { scope.closeSuspending() }.closed)
            assertEquals("new store", reopened.stickyFlow(key).first())
        } finally {
            reopened.close()
        }
        assertFalse(bus.hasScope(scope.scopeName))
    }

    private suspend fun withBlockedScope(
        scopeName: String,
        block: suspend CoroutineScope.(FlowBus, FlowBusScope, Job) -> Unit
    ) = coroutineScope {
        val bus = FlowBus(FlowBusConfig(normalBufferCapacity = 0, overflowPolicy = BufferOverflow.SUSPEND))
        val scope = bus.openScope(scopeName)
        val key = eventKey<Int>("closing.explicit.blocked")
        val firstReceived = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            scope.flow(key).collect {
                firstReceived.complete(Unit)
                releaseFirst.await()
            }
        }
        var sender: Job? = null
        try {
            scope.emit(key, 1)
            firstReceived.await()
            val blockedSender = launch(start = CoroutineStart.UNDISPATCHED) { scope.emit(key, 2) }
            sender = blockedSender
            block(bus, scope, blockedSender)
        } finally {
            sender?.cancelAndJoin()
            releaseFirst.complete(Unit)
            collector.cancelAndJoin()
            scope.close()
        }
    }

    @Test
    fun `job completion during interrupted timed close keeps scope closed`() = runBlocking {
        val bus = FlowBus(FlowBusConfig(normalBufferCapacity = 0, overflowPolicy = BufferOverflow.SUSPEND))
        val boundJob = Job()
        val scope = bus.openScope("closing.interrupted.bound", closeWhen = boundJob)
        val key = eventKey<Int>("closing.interrupted.bound.event")
        val firstReceived = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            scope.flow(key).collect {
                firstReceived.complete(Unit)
                releaseFirst.await()
            }
        }
        val interrupted = CompletableDeferred<Boolean>()
        val closeThread = Thread {
            try {
                scope.tryClose(5_000)
                interrupted.complete(false)
            } catch (_: InterruptedException) {
                interrupted.complete(Thread.currentThread().isInterrupted)
            } catch (failure: Throwable) {
                interrupted.completeExceptionally(failure)
            }
        }
        var sender: Job? = null
        try {
            scope.emit(key, 1)
            firstReceived.await()
            sender = launch(start = CoroutineStart.UNDISPATCHED) { scope.emit(key, 2) }
            closeThread.start()
            scope.awaitClosingForTest()
            boundJob.cancel()
            closeThread.interrupt()

            assertTrue(withTimeout(1_000) { interrupted.await() })
            assertTrue(scope.isClosed)
            assertFalse(sender.isCompleted)
            try {
                scope.postSticky(key, 3)
                throw AssertionError("A lifecycle-closed scope must reject new operations")
            } catch (_: IllegalStateException) {
                // 中断仅结束调用方的等待，生命周期关闭仍然生效。
            }
            sender.cancelAndJoin()
            assertTrue(withTimeout(1_000) { scope.closeSuspending() }.closed)
            assertFalse(bus.hasScope(scope.scopeName))
        } finally {
            closeThread.interrupt()
            closeThread.join(1_000)
            releaseFirst.complete(Unit)
            sender?.cancelAndJoin()
            collector.cancelAndJoin()
            scope.close()
        }
    }

    @Test
    fun `job completion during cancelled close wait is not lost`() = runBlocking {
        val bus = FlowBus(FlowBusConfig(normalBufferCapacity = 0, overflowPolicy = BufferOverflow.SUSPEND))
        val boundJob = Job()
        val scope = bus.openScope("closing.cancel.bound", closeWhen = boundJob)
        val key = eventKey<Int>("closing.cancel.bound.event")
        val firstReceived = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            scope.flow(key).collect {
                firstReceived.complete(Unit)
                releaseFirst.await()
            }
        }
        var sender: Job? = null
        var closer: Job? = null
        try {
            scope.emit(key, 1)
            firstReceived.await()
            sender = launch(start = CoroutineStart.UNDISPATCHED) { scope.emit(key, 2) }
            closer = launch(start = CoroutineStart.UNDISPATCHED) { scope.closeSuspending() }
            scope.awaitClosingForTest()
            boundJob.cancel()
            withTimeout(1_000) { closer.cancelAndJoin() }

            assertTrue(scope.isClosed)
            assertFalse(sender.isCompleted)
            sender.cancelAndJoin()
            assertTrue(withTimeout(1_000) { scope.closeSuspending() }.closed)
            assertFalse(bus.hasScope(scope.scopeName))
        } finally {
            releaseFirst.complete(Unit)
            sender?.cancelAndJoin()
            closer?.cancelAndJoin()
            collector.cancelAndJoin()
            scope.close()
        }
    }

    @Test
    fun `job completion during timed close is retained after timeout`() = runBlocking {
        val bus = FlowBus(FlowBusConfig(normalBufferCapacity = 0, overflowPolicy = BufferOverflow.SUSPEND))
        val boundJob = Job()
        val scope = bus.openScope("closing.timeout.bound", closeWhen = boundJob)
        val key = eventKey<Int>("closing.timeout.bound.event")
        val firstReceived = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            scope.flow(key).collect {
                firstReceived.complete(Unit)
                releaseFirst.await()
            }
        }
        var sender: Job? = null
        try {
            scope.emit(key, 1)
            firstReceived.await()
            sender = launch(start = CoroutineStart.UNDISPATCHED) { scope.emit(key, 2) }
            val closing = async(Dispatchers.Default) { scope.tryClose(1_000) }
            scope.awaitClosingForTest()
            boundJob.cancel()
            assertEquals(FlowBusCloseOutcome.Timeout, withTimeout(2_000) { closing.await() }.outcome)
            assertTrue(scope.isClosed)
            sender.cancelAndJoin()
            assertTrue(withTimeout(1_000) { scope.closeSuspending() }.closed)
            assertFalse(bus.hasScope(scope.scopeName))
        } finally {
            releaseFirst.complete(Unit)
            sender?.cancelAndJoin()
            collector.cancelAndJoin()
            scope.close()
        }
    }

    @Test
    fun `unconfined close completion can race with scope removal`() = runBlocking {
        val bus = FlowBus(FlowBusConfig(normalBufferCapacity = 0, overflowPolicy = BufferOverflow.SUSPEND))
        val scope = bus.openScope("closing.unconfined.remove")
        val key = eventKey<Int>("closing.unconfined.event")
        val firstReceived = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            scope.flow(key).collect {
                firstReceived.complete(Unit)
                releaseFirst.await()
            }
        }
        var sender: Job? = null
        try {
            scope.emit(key, 1)
            firstReceived.await()
            sender = launch(start = CoroutineStart.UNDISPATCHED) { scope.emit(key, 2) }
            val closeResult = async(Dispatchers.Unconfined) { scope.closeSuspending() }
            scope.awaitClosingForTest()
            val removal = launch(Dispatchers.Default) { bus.removeScope(scope.scopeName) }
            withTimeout(1_000) {
                sender.cancelAndJoin()
                removal.join()
                assertTrue(closeResult.await().closed)
            }
            assertTrue(scope.isClosed)
            assertFalse(bus.hasScope(scope.scopeName))
        } finally {
            releaseFirst.complete(Unit)
            sender?.cancelAndJoin()
            collector.cancelAndJoin()
            scope.close()
        }
    }

    @Test
    fun `cancel initiated close wait restores scope without releasing blocked sender`() = runBlocking {
        val bus = FlowBus(FlowBusConfig(normalBufferCapacity = 0, overflowPolicy = BufferOverflow.SUSPEND))
        val scope = bus.openScope("closing.cancel.initiator")
        val key = eventKey<Int>("closing.cancel.event")
        val firstReceived = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            scope.flow(key).collect {
                firstReceived.complete(Unit)
                releaseFirst.await()
            }
        }
        var sender: Job? = null
        var closer: Job? = null
        try {
            scope.emit(key, 1)
            firstReceived.await()
            sender = launch(start = CoroutineStart.UNDISPATCHED) { scope.emit(key, 2) }
            closer = launch(start = CoroutineStart.UNDISPATCHED) { scope.closeSuspending() }
            scope.awaitClosingForTest()
            assertFalse(scope.isClosed)
            assertEquals(FlowBusCloseOutcome.ClosingInProgress, scope.tryClose(0).outcome)

            withTimeout(1_000) { closer.cancelAndJoin() }

            assertFalse(sender.isCompleted)
            assertFalse(scope.isClosed)
            assertFalse(scope.isClosingForTest())
            assertTrue(scope.postSticky(eventKey<String>("closing.cancel.sticky"), "still open"))
        } finally {
            releaseFirst.complete(Unit)
            sender?.cancelAndJoin()
            closer?.cancelAndJoin()
            collector.cancelAndJoin()
            scope.close()
        }
    }

    @Test
    fun `cancel prepared close waiter does not roll back closure or affect other waiter`() = runBlocking {
        val bus = FlowBus(FlowBusConfig(normalBufferCapacity = 0, overflowPolicy = BufferOverflow.SUSPEND))
        val scope = bus.openScope("closing.cancel.waiter")
        val key = eventKey<Int>("closing.cancel.waiter.event")
        val firstReceived = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            scope.flow(key).collect {
                firstReceived.complete(Unit)
                releaseFirst.await()
            }
        }
        var sender: Job? = null
        var cancelledWaiter: Job? = null
        var remainingWaiter: Job? = null
        try {
            scope.emit(key, 1)
            firstReceived.await()
            sender = launch(start = CoroutineStart.UNDISPATCHED) { scope.emit(key, 2) }
            scope.close()
            cancelledWaiter = launch(start = CoroutineStart.UNDISPATCHED) { scope.closeSuspending() }
            val result = async(start = CoroutineStart.UNDISPATCHED) { scope.closeSuspending() }
            remainingWaiter = result

            withTimeout(1_000) { cancelledWaiter.cancelAndJoin() }

            assertTrue(scope.isClosed)
            assertFalse(result.isCompleted)
            sender.cancelAndJoin()
            assertEquals(FlowBusCloseOutcome.Closed, withTimeout(1_000) { result.await() }.outcome)
            assertFalse(bus.hasScope(scope.scopeName))
        } finally {
            releaseFirst.complete(Unit)
            sender?.cancelAndJoin()
            cancelledWaiter?.cancelAndJoin()
            remainingWaiter?.cancelAndJoin()
            collector.cancelAndJoin()
            scope.close()
        }
    }

    @Test
    fun `timed out suspending close allows a later successful close`() = runBlocking {
        val bus = FlowBus(FlowBusConfig(normalBufferCapacity = 0, overflowPolicy = BufferOverflow.SUSPEND))
        val scope = bus.openScope("closing.suspending.timeout")
        val key = eventKey<Int>("closing.suspending.timeout.event")
        val firstReceived = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            scope.flow(key).collect {
                firstReceived.complete(Unit)
                releaseFirst.await()
            }
        }
        var sender: Job? = null
        try {
            scope.emit(key, 1)
            firstReceived.await()
            sender = launch(start = CoroutineStart.UNDISPATCHED) { scope.emit(key, 2) }
            try {
                withTimeout(50) { scope.closeSuspending() }
                throw AssertionError("Close must time out while sender is blocked")
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                assertFalse(scope.isClosed)
                assertFalse(scope.isClosingForTest())
            }
            sender.cancelAndJoin()
            assertEquals(FlowBusCloseOutcome.Closed, scope.closeSuspending().outcome)
        } finally {
            releaseFirst.complete(Unit)
            sender?.cancelAndJoin()
            collector.cancelAndJoin()
            scope.close()
        }
    }

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
    fun `close marks scope closed immediately and cleans up after in flight operation`() = runBlocking {
        val key = eventKey<String>("closing.direct.nonblocking.event")
        val store = FlowBusStore(FlowBusConfig())
        val storeLookupStarted = CompletableDeferred<Unit>()
        val releaseStoreLookup = CompletableDeferred<Unit>()
        val closeActionCalled = CompletableDeferred<Unit>()
        val scope = FlowBusScope(
            busScopeName = "closing.direct.nonblocking",
            scopedBus = ScopedFlowBus(
                scopeName = "closing.direct.nonblocking",
                storeProvider = {
                    storeLookupStarted.complete(Unit)
                    runBlocking { releaseStoreLookup.await() }
                    store
                },
                removeScopeAction = {}
            ),
            closeAction = { _, _ -> closeActionCalled.complete(Unit) }
        )

        val postJob = launch(Dispatchers.Default) {
            scope.post(key, "value")
        }
        storeLookupStarted.await()

        scope.close()

        assertTrue(scope.isClosed)
        assertFalse(closeActionCalled.isCompleted)
        val timeoutResult = scope.tryClose(timeoutMillis = 10)
        assertFalse(timeoutResult.closed)
        assertEquals(FlowBusCloseOutcome.Timeout, timeoutResult.outcome)
        val closeResult = async { scope.closeSuspending() }
        yield()
        assertFalse(closeResult.isCompleted)

        releaseStoreLookup.complete(Unit)
        withTimeout(1_000) {
            postJob.join()
            closeActionCalled.await()
            assertEquals(FlowBusCloseOutcome.Closed, closeResult.await().outcome)
        }
    }

    @Test
    fun `direct close cleanup does not remove immediately reopened scope store`() = runBlocking {
        val bus = FlowBus(
            FlowBusConfig(
                normalBufferCapacity = 0,
                overflowPolicy = BufferOverflow.SUSPEND
            )
        )
        val normalKey = eventKey<Int>("closing.reopen.event")
        val stickyKey = eventKey<String>("closing.reopen.state")
        val scope = bus.openScope("closing.reopen")
        val flow = scope.flow(normalKey) as MutableSharedFlow<Int>
        val firstHandled = CompletableDeferred<Unit>()
        val allowFirstToFinish = CompletableDeferred<Unit>()
        val secondEmitStarted = CompletableDeferred<Unit>()
        val secondEmitFinished = CompletableDeferred<Unit>()

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
            val firstEmitJob = launch { scope.emit(normalKey, 1) }
            firstHandled.await()
            val secondEmitJob = launch {
                secondEmitStarted.complete(Unit)
                scope.emit(normalKey, 2)
                secondEmitFinished.complete(Unit)
            }
            secondEmitStarted.await()
            delay(50)
            assertFalse(secondEmitFinished.isCompleted)

            scope.close()
            val reopenedScope = bus.openScope("closing.reopen")
            reopenedScope.postSticky(stickyKey, "new-state")

            allowFirstToFinish.complete(Unit)
            withTimeout(1_000) {
                secondEmitFinished.await()
            }

            assertEquals("new-state", reopenedScope.stickyFlow(stickyKey).first())

            firstEmitJob.cancelAndJoin()
            secondEmitJob.cancelAndJoin()
            reopenedScope.close()
        } finally {
            allowFirstToFinish.complete(Unit)
            collectorJob.cancelAndJoin()
        }
    }

    @Test
    fun `remove scope during pending close does not clear store before in flight operation finishes`() = runBlocking {
        val bus = FlowBus(
            FlowBusConfig(
                normalBufferCapacity = 0,
                overflowPolicy = BufferOverflow.SUSPEND
            )
        )
        val scopeName = "closing.remove.pending"
        val key = eventKey<Int>("closing.remove.pending.event")
        val scope = bus.openScope(scopeName)
        val flow = scope.flow(key) as MutableSharedFlow<Int>
        val firstHandled = CompletableDeferred<Unit>()
        val allowFirstToFinish = CompletableDeferred<Unit>()
        val secondEmitStarted = CompletableDeferred<Unit>()
        val secondEmitFinished = CompletableDeferred<Unit>()

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
                secondEmitFinished.complete(Unit)
            }
            secondEmitStarted.await()
            delay(50)
            assertFalse(secondEmitFinished.isCompleted)

            scope.close()
            bus.removeScope(scopeName)

            assertTrue(bus.hasScope(scopeName))

            allowFirstToFinish.complete(Unit)
            withTimeout(1_000) {
                secondEmitFinished.await()
                while (bus.hasScope(scopeName)) {
                    yield()
                }
            }

            firstEmitJob.cancelAndJoin()
            secondEmitJob.cancelAndJoin()
        } finally {
            allowFirstToFinish.complete(Unit)
            collectorJob.cancelAndJoin()
        }
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
            assertTrue(scope.isClosed)
            assertTrue(bus.hasScope("closing.bound.job"))

            allowFirstToFinish.complete(Unit)
            withTimeout(1_000) {
                while (bus.hasScope("closing.bound.job")) {
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
    fun `thread interruption does not prevent lifecycle close completion`() = runBlocking {
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
