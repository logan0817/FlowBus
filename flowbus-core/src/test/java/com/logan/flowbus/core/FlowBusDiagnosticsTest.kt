package com.logan.flowbus.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Modifier
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

@OptIn(ExperimentalCoroutinesApi::class)
class FlowBusDiagnosticsTest {
    @After
    fun tearDown() {
        DefaultFlowBus.resetForTests()
    }

    @Test
    fun `publication metadata covers ordinary and sticky sends on each source`() = runTest {
        val bus = FlowBus()
        val shared = bus.scoped("shared")
        val owned = bus.openScope("owned")
        val defaultBus = FlowBus()
        DefaultFlowBus.install(defaultBus)
        val records = mutableListOf<FlowBusDiagnosticEvent>()
        val rootRegistration = bus.observeDiagnostics { records += it }
        val defaultRegistration = DefaultFlowBus.observeDiagnostics { records += it }
        val rootKey = eventKey<Int>("diagnostics.publish.root")
        val sharedKey = eventKey<Int>("diagnostics.publish.shared")
        val ownedKey = eventKey<Int>("diagnostics.publish.owned")
        val defaultKey = eventKey<Int>("diagnostics.publish.default")
        try {
            assertTrue(bus.post(rootKey, 1))
            assertTrue(bus.tryPostResult(rootKey, 2).accepted)
            bus.emit(rootKey, 3)
            assertTrue(bus.postSticky(rootKey, 1))
            assertTrue(bus.tryPostStickyResult(rootKey, 2).accepted)
            bus.emitSticky(rootKey, 3)

            assertTrue(shared.post(sharedKey, 1))
            assertTrue(shared.tryPostResult(sharedKey, 2).accepted)
            shared.emit(sharedKey, 3)
            assertTrue(shared.postSticky(sharedKey, 1))
            assertTrue(shared.tryPostStickyResult(sharedKey, 2).accepted)
            shared.emitSticky(sharedKey, 3)

            assertTrue(owned.post(ownedKey, 1))
            assertTrue(owned.tryPostResult(ownedKey, 2).accepted)
            owned.emit(ownedKey, 3)
            assertTrue(owned.postSticky(ownedKey, 1))
            assertTrue(owned.tryPostStickyResult(ownedKey, 2).accepted)
            owned.emitSticky(ownedKey, 3)

            assertTrue(DefaultFlowBus.post(defaultKey, 1))
            assertTrue(DefaultFlowBus.tryPostResult(defaultKey, 2).accepted)
            DefaultFlowBus.emit(defaultKey, 3)
            assertTrue(DefaultFlowBus.postSticky(defaultKey, 1))
            assertTrue(DefaultFlowBus.tryPostStickyResult(defaultKey, 2).accepted)
            DefaultFlowBus.emitSticky(defaultKey, 3)

            val publications = records.filterIsInstance<FlowBusDiagnosticEvent.Published>()
            assertEquals(24, publications.size)
            val expectedScopes = mapOf(
                rootKey.name to null,
                sharedKey.name to "shared",
                ownedKey.name to "owned",
                defaultKey.name to null
            )
            for ((name, scopeName) in expectedScopes) {
                val source = publications.filter { it.channel.eventName == name }
                assertEquals(6, source.size)
                assertTrue(source.all { it.channel.scopeName == scopeName && it.result.scopeName == scopeName })
                assertTrue(source.all { it.result.accepted && it.result.subscriptionCount == 0 })
                assertTrue(source.all { it.result.outcome == FlowBusPostOutcome.AcceptedWithoutActiveSubscriber })
                assertTrue(source.all { it.channel.eventName == it.result.eventName && it.channel.isSticky == it.result.isSticky })
                assertEquals(1, source.map { it.channel.storeId }.distinct().size)
                assertEquals(2, source.map { it.channel.flowId }.distinct().size)
                for (sticky in listOf(false, true)) {
                    val stream = source.filter { it.channel.isSticky == sticky }
                    assertEquals(
                        listOf(
                            FlowBusDiagnosticPublishMode.TryPost,
                            FlowBusDiagnosticPublishMode.TryPost,
                            FlowBusDiagnosticPublishMode.Emit
                        ),
                        stream.map { it.mode }
                    )
                    assertEquals(1, stream.map { it.channel }.distinct().size)
                }
            }
            val mainBusIds = publications.filter { it.channel.eventName != defaultKey.name }.map { it.channel.busId }.distinct()
            assertEquals(1, mainBusIds.size)
            assertNotEquals(mainBusIds.single(), publications.first { it.channel.eventName == defaultKey.name }.channel.busId)
            assertEquals(3, publications.filter { it.channel.eventName != defaultKey.name }.map { it.channel.storeId }.distinct().size)
        } finally {
            rootRegistration.close()
            defaultRegistration.close()
            owned.closeSuspending()
        }
    }

    @Test
    fun `flows obtained before observing stay uninstrumented while fresh flows announce collection`() = runTest {
        val bus = FlowBus()
        val key = eventKey<Int>("diagnostics.late.enable")
        val oldFlow = bus.flow(key)
        val records = mutableListOf<FlowBusDiagnosticEvent>()
        val registration = bus.observeDiagnostics { records += it }
        val oldValues = mutableListOf<Int>()
        val freshValues = mutableListOf<Int>()
        val oldJob = launch(start = CoroutineStart.UNDISPATCHED) { oldFlow.collect { oldValues += it } }
        val freshJob = launch(start = CoroutineStart.UNDISPATCHED) { bus.flow(key).collect { freshValues += it } }
        try {
            assertEquals(1, records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionStarted>().size)
            bus.post(key, 1)
            runCurrent()
            assertEquals(listOf(1), oldValues)
            assertEquals(listOf(1), freshValues)
            oldJob.cancelAndJoin()
            assertTrue(records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionEnded>().isEmpty())
            freshJob.cancelAndJoin()
            val start = records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionStarted>().single()
            val end = records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionEnded>().single()
            assertEquals(start.channel, end.channel)
            assertEquals(start.subscriptionId, end.subscriptionId)
            assertEquals(FlowBusDiagnosticEndReason.Cancelled, end.reason)
            assertNull(end.failureType)
        } finally {
            oldJob.cancelAndJoin()
            freshJob.cancelAndJoin()
            registration.close()
        }
    }

    @Test
    fun `subscription starts before user notification and replay with distinct collection ids`() = runTest {
        val bus = FlowBus()
        val key = eventKey<Int>("diagnostics.replay.order")
        bus.postSticky(key, 7)
        val records = mutableListOf<FlowBusDiagnosticEvent>()
        val registration = bus.observeDiagnostics { records += it }
        var notifications = 0
        val source = bus.stickyFlow(key) {
            notifications++
            assertEquals(notifications, records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionStarted>().size)
        }
        try {
            repeat(2) {
                assertEquals(7, source.first())
                assertEquals(it + 1, notifications)
            }
            val starts = records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionStarted>()
            val ends = records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionEnded>()
            assertEquals(2, starts.size)
            assertEquals(2, ends.size)
            assertNotEquals(starts[0].subscriptionId, starts[1].subscriptionId)
            assertEquals(starts[0].channel, starts[1].channel)
            assertEquals(starts.map { it.subscriptionId }, ends.map { it.subscriptionId })
            assertTrue(records.filterIsInstance<FlowBusDiagnosticEvent.Failure>().isEmpty())
            assertEquals(0, bus.inspectChannel(key)?.stickySubscriptionCount)
        } finally {
            registration.close()
        }
    }

    @Test
    fun `subscription callback failure is classified and ends before sticky replay`() = runTest {
        for (sticky in listOf(false, true)) {
            val key = eventKey<Int>("diagnostics.subscription.failure")
            var errorHandlerCalls = 0
            val records = mutableListOf<FlowBusDiagnosticEvent>()
            val configured = FlowBus(FlowBusConfig(errorHandler = FlowBusErrorHandler { _, _ -> errorHandlerCalls++ }))
            configured.postSticky(key, 1)
            val registration = configured.observeDiagnostics { records += it }
            val expected = IllegalArgumentException("subscription-private-message")
            val received = mutableListOf<Int>()
            try {
                val flow = if (sticky) configured.stickyFlow(key) { throw expected }
                else configured.flow(key) { throw expected }
                val actual = runCatching { flow.collect { received += it } }.exceptionOrNull()
                assertSame(expected, actual)
                assertTrue(received.isEmpty())
                assertEquals(0, errorHandlerCalls)
                val started = records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionStarted>().single()
                val failure = records.filterIsInstance<FlowBusDiagnosticEvent.Failure>().single()
                val ended = records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionEnded>().single()
                assertEquals(FlowBusDiagnosticFailurePhase.SubscriptionCallback, failure.phase)
                assertEquals(IllegalArgumentException::class.java.name, failure.exceptionType)
                assertEquals(started.subscriptionId, failure.subscriptionId)
                assertEquals(started.subscriptionId, ended.subscriptionId)
                assertEquals(FlowBusDiagnosticEndReason.Failed, ended.reason)
                assertEquals(IllegalArgumentException::class.java.name, ended.failureType)
                assertEquals(listOf(started, failure, ended), records)
            } finally {
                registration.close()
            }
        }
    }

    @Test
    fun `subscription callback cancellation ends collection without business failure or replay`() = runTest {
        val bus = FlowBus()
        val key = eventKey<Int>("diagnostics.subscription.cancel")
        bus.postSticky(key, 1)
        val records = mutableListOf<FlowBusDiagnosticEvent>()
        val registration = bus.observeDiagnostics { records += it }
        val expected = CancellationException("notification cancelled")
        val received = mutableListOf<Int>()
        try {
            val actual = runCatching {
                bus.stickyFlow(key) { throw expected }.collect { received += it }
            }.exceptionOrNull()
            assertSame(expected, actual)
            assertTrue(received.isEmpty())
            assertTrue(records.filterIsInstance<FlowBusDiagnosticEvent.Failure>().isEmpty())
            assertEquals(1, records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionStarted>().size)
            val ended = records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionEnded>().single()
            assertEquals(FlowBusDiagnosticEndReason.Cancelled, ended.reason)
            assertNull(ended.failureType)
        } finally {
            registration.close()
        }
    }

    @Test
    fun `downstream operator failure ends subscription without inventing callback failure`() = runTest {
        val bus = FlowBus()
        val key = eventKey<Int>("diagnostics.operator.failure")
        bus.postSticky(key, 1)
        val records = mutableListOf<FlowBusDiagnosticEvent>()
        val registration = bus.observeDiagnostics { records += it }
        val expected = IllegalArgumentException("downstream operator failed")
        try {
            val actual = runCatching {
                bus.stickyFlow(key).map<Int, Int> { throw expected }.first()
            }.exceptionOrNull()
            assertSame(expected, actual)
            val started = records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionStarted>().single()
            val ended = records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionEnded>().single()
            assertEquals(started.channel, ended.channel)
            assertEquals(started.subscriptionId, ended.subscriptionId)
            assertEquals(FlowBusDiagnosticEndReason.Failed, ended.reason)
            assertEquals(IllegalArgumentException::class.java.name, ended.failureType)
            assertTrue(records.filterIsInstance<FlowBusDiagnosticEvent.Failure>().isEmpty())
            assertEquals(listOf(started, ended), records)
        } finally {
            registration.close()
        }
    }

    @Test
    fun `handled callback failures are observed once and do not terminate collection`() = runTest {
        for (suspending in listOf(false, true)) {
            val errors = mutableListOf<Throwable>()
            val bus = FlowBus(FlowBusConfig(errorHandler = FlowBusErrorHandler { _, error -> errors += error }))
            val key = eventKey<Int>("diagnostics.handled.callback")
            val expected = IllegalStateException("callback-private-message")
            val records = mutableListOf<FlowBusDiagnosticEvent>()
            val registration = bus.observeDiagnostics { records += it }
            val received = mutableListOf<Int>()
            val receive: (Int) -> Unit = {
                if (it == 1) throw expected
                received += it
            }
            val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
                if (suspending) bus.collectSuspending(key) { receive(it) }
                else bus.collect(key, onReceived = receive)
            }
            try {
                bus.post(key, 1)
                bus.post(key, 2)
                runCurrent()
                assertEquals(listOf(2), received)
                assertEquals(listOf(expected), errors)
                val failure = records.filterIsInstance<FlowBusDiagnosticEvent.Failure>().single()
                assertEquals(FlowBusDiagnosticFailurePhase.SubscriberCallback, failure.phase)
                assertEquals(IllegalStateException::class.java.name, failure.exceptionType)
                assertEquals(records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionStarted>().single().subscriptionId, failure.subscriptionId)
                assertTrue(records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionEnded>().isEmpty())
            } finally {
                collecting.cancelAndJoin()
                registration.close()
            }
        }
    }

    @Test
    @Suppress("UNCHECKED_CAST")
    fun `raw value mismatch is observed through public sequential collectors`() = runTest {
        for (suspending in listOf(false, true)) {
            val bus = FlowBus()
            val key = eventKey<String>("diagnostics.raw.type")
            val raw = bus.flow(key) as MutableSharedFlow<Any>
            val records = mutableListOf<FlowBusDiagnosticEvent>()
            val registration = bus.observeDiagnostics { records += it }
            val errors = mutableListOf<FlowBusErrorContext>()
            val received = mutableListOf<String>()
            val handler = FlowBusErrorHandler { context, _ -> errors += context }
            val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
                val source = bus.flow(key)
                if (suspending) {
                    collectFlowBusSuspendingSequentially<String>(source, eventKey = key, errorHandler = handler) {
                        received += it
                    }
                } else {
                    collectFlowBusSequentially<String>(source, eventKey = key, errorHandler = handler) {
                        received += it
                    }
                }
            }
            try {
                raw.emit(123)
                raw.emit("ok")
                runCurrent()
                assertEquals(listOf("ok"), received)
                assertEquals(FlowBusErrorPhase.ValueCast, errors.single().phase)
                val failure = records.filterIsInstance<FlowBusDiagnosticEvent.Failure>().single()
                assertEquals(FlowBusDiagnosticFailurePhase.ValueCast, failure.phase)
                assertEquals(ClassCastException::class.java.name, failure.exceptionType)
                assertEquals(records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionStarted>().single().subscriptionId, failure.subscriptionId)
                assertTrue(records.filterIsInstance<FlowBusDiagnosticEvent.Published>().isEmpty())
            } finally {
                collecting.cancelAndJoin()
                registration.close()
            }
        }
    }

    @Test
    fun `rethrow preserves callback exception and cancellation is not reported as failure`() = runTest {
        for (cancellation in listOf(false, true)) {
            for (suspending in listOf(false, true)) {
                var handlerCalls = 0
                val expected = if (cancellation) CancellationException("stop") else IllegalStateException("business failure")
                val bus = FlowBus(FlowBusConfig(errorHandler = FlowBusErrorHandler { _, error ->
                    handlerCalls++
                    throw error
                }))
                val key = eventKey<Int>("diagnostics.callback.exit")
                bus.postSticky(key, 1)
                val records = mutableListOf<FlowBusDiagnosticEvent>()
                val registration = bus.observeDiagnostics { records += it }
                try {
                    val actual = runCatching {
                        if (suspending) bus.collectStickySuspending(key) { throw expected }
                        else bus.collectSticky(key) { throw expected }
                    }.exceptionOrNull()
                    assertSame(expected, actual)
                    assertEquals(if (cancellation) 0 else 1, handlerCalls)
                    assertEquals(if (cancellation) 0 else 1, records.filterIsInstance<FlowBusDiagnosticEvent.Failure>().size)
                    val ended = records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionEnded>().single()
                    assertEquals(
                        if (cancellation) FlowBusDiagnosticEndReason.Cancelled else FlowBusDiagnosticEndReason.Failed,
                        ended.reason
                    )
                    assertEquals(if (cancellation) null else IllegalStateException::class.java.name, ended.failureType)
                    assertEquals(0, bus.inspectChannel(key)?.stickySubscriptionCount)
                } finally {
                    registration.close()
                }
            }
        }
    }

    @Test
    fun `cancelling suspended callback announces exit only when callback has exited`() = runTest {
        val bus = FlowBus()
        val key = eventKey<Int>("diagnostics.callback.cancel")
        val records = mutableListOf<FlowBusDiagnosticEvent>()
        val registration = bus.observeDiagnostics { records += it }
        val callbackStarted = CompletableDeferred<Unit>()
        val releaseCallback = CompletableDeferred<Unit>()
        var callbackExited = false
        val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.collectSuspending(key) {
                try {
                    withContext(NonCancellable) {
                        callbackStarted.complete(Unit)
                        releaseCallback.await()
                    }
                } finally {
                    callbackExited = true
                }
            }
        }
        try {
            bus.emit(key, 1)
            callbackStarted.await()
            assertTrue(records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionEnded>().isEmpty())
            collecting.cancel()
            runCurrent()
            assertFalse(callbackExited)
            assertTrue(records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionEnded>().isEmpty())
            releaseCallback.complete(Unit)
            collecting.cancelAndJoin()
            assertTrue(callbackExited)
            assertEquals(FlowBusDiagnosticEndReason.Cancelled, records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionEnded>().single().reason)
            assertTrue(records.filterIsInstance<FlowBusDiagnosticEvent.Failure>().isEmpty())
        } finally {
            releaseCallback.complete(Unit)
            collecting.cancelAndJoin()
            registration.close()
        }
    }

    @Test
    fun `already cancelled collection does not announce a registered subscription`() = runTest {
        for (sticky in listOf(false, true)) {
            val bus = FlowBus()
            val key = eventKey<Int>("diagnostics.pre.cancelled")
            val records = mutableListOf<FlowBusDiagnosticEvent>()
            val registration = bus.observeDiagnostics { records += it }
            val source = if (sticky) bus.stickyFlow(key) else bus.flow(key)
            try {
                val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
                    currentCoroutineContext().cancel()
                    source.collect {}
                }
                collecting.join()
                assertTrue(collecting.isCancelled)
                assertTrue(records.isEmpty())
                assertEquals(0, bus.inspectChannel(key)?.normalSubscriptionCount)
                assertEquals(0, bus.inspectChannel(key)?.stickySubscriptionCount)
            } finally {
                registration.close()
            }
        }
    }

    @Test
    fun `cancelled pending emit is separate from buffer rejection metrics`() = runTest {
        for (sticky in listOf(false, true)) {
            val bus = FlowBus(rendezvousConfig())
            val key = eventKey<Int>("diagnostics.emit.cancel")
            val records = mutableListOf<FlowBusDiagnosticEvent>()
            val registration = bus.observeDiagnostics { records += it }
            val release = CompletableDeferred<Unit>()
            val received = mutableListOf<Int>()
            val source = if (sticky) bus.stickyFlow(key) else bus.flow(key)
            val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
                source.collect {
                    received += it
                    release.await()
                }
            }
            try {
                if (sticky) bus.emitSticky(key, 1) else bus.emit(key, 1)
                runCurrent()
                assertEquals(listOf(1), received)
                val pending = launch(start = CoroutineStart.UNDISPATCHED) {
                    if (sticky) bus.emitSticky(key, 2) else bus.emit(key, 2)
                }
                try {
                    assertFalse(pending.isCompleted)
                    pending.cancelAndJoin()
                    assertEquals(1, records.filterIsInstance<FlowBusDiagnosticEvent.EmitCancelled>().size)
                    assertEquals(1, records.filterIsInstance<FlowBusDiagnosticEvent.Published>().size)
                    assertTrue(records.filterIsInstance<FlowBusDiagnosticEvent.EmitFailed>().isEmpty())
                    val metrics = bus.inspect().root.events.single().metrics
                    assertEquals(1L, metrics.acceptedPostCount)
                    assertEquals(0L, metrics.rejectedPostCount)
                } finally {
                    pending.cancelAndJoin()
                }
            } finally {
                release.complete(Unit)
                collecting.cancelAndJoin()
                registration.close()
            }
        }
    }

    @Test
    fun `zero capacity rejection keeps original result and ordinary sticky identities`() = runTest {
        val bus = FlowBus(rendezvousConfig())
        val key = eventKey<Int>("diagnostics.buffer.reject")
        val records = mutableListOf<FlowBusDiagnosticEvent>()
        val registration = bus.observeDiagnostics { records += it }
        val normal = launch(start = CoroutineStart.UNDISPATCHED) { bus.flow(key).collect {} }
        val sticky = launch(start = CoroutineStart.UNDISPATCHED) { bus.stickyFlow(key).collect {} }
        try {
            val ordinaryResult = bus.tryPostResult(key, 1)
            val stickyResult = bus.tryPostStickyResult(key, 2)
            val published = records.filterIsInstance<FlowBusDiagnosticEvent.Published>()
            assertEquals(listOf(ordinaryResult, stickyResult), published.map { it.result })
            assertTrue(published.all { !it.result.accepted && it.result.outcome == FlowBusPostOutcome.RejectedByBuffer })
            assertTrue(published.all { it.result.subscriptionCount == 1 })
            assertEquals(listOf(false, true), published.map { it.channel.isSticky })
            assertNotEquals(published[0].channel.flowId, published[1].channel.flowId)
            assertEquals(published[0].channel.storeId, published[1].channel.storeId)
        } finally {
            normal.cancelAndJoin()
            sticky.cancelAndJoin()
            registration.close()
        }
    }

    @Test
    fun `old flows keep their identity when current flow or owned scope is rebuilt`() = runTest {
        val bus = FlowBus()
        val key = eventKey<Int>("diagnostics.rebuild")
        val records = mutableListOf<FlowBusDiagnosticEvent>()
        val registration = bus.observeDiagnostics { records += it }
        val oldValues = mutableListOf<Int>()
        val oldFlow = bus.flow(key)
        val oldJob = launch(start = CoroutineStart.UNDISPATCHED) { oldFlow.collect { oldValues += it } }
        var owned = bus.openScope("session")
        try {
            val oldChannel = records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionStarted>().single().channel
            bus.removeEvent(key)
            bus.post(key, 1)
            val rebuilt = records.filterIsInstance<FlowBusDiagnosticEvent.Published>().last().channel
            runCurrent()
            assertTrue(oldValues.isEmpty())
            assertEquals(oldChannel.busId, rebuilt.busId)
            assertEquals(oldChannel.storeId, rebuilt.storeId)
            assertNotEquals(oldChannel.flowId, rebuilt.flowId)
            oldJob.cancelAndJoin()
            assertEquals(oldChannel, records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionEnded>().single().channel)

            val oldOwnedFlow = owned.flow(key)
            val oldOwnedJob = launch(start = CoroutineStart.UNDISPATCHED) { oldOwnedFlow.collect {} }
            val ownedChannel = records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionStarted>().last().channel
            owned.closeSuspending()
            owned = bus.openScope("session")
            try {
                owned.post(key, 2)
                val reopened = records.filterIsInstance<FlowBusDiagnosticEvent.Published>().last().channel
                assertEquals(ownedChannel.busId, reopened.busId)
                assertEquals("session", reopened.scopeName)
                assertNotEquals(ownedChannel.storeId, reopened.storeId)
                assertNotEquals(ownedChannel.flowId, reopened.flowId)
                assertFalse(oldOwnedJob.isCompleted)
            } finally {
                oldOwnedJob.cancelAndJoin()
            }
        } finally {
            oldJob.cancelAndJoin()
            owned.closeSuspending()
            registration.close()
        }
    }

    @Test
    fun `clearing replay keeps sticky identity while removal creates a new flow identity`() {
        val bus = FlowBus()
        val key = eventKey<Int>("diagnostics.sticky.rebuild")
        val records = mutableListOf<FlowBusDiagnosticEvent.Published>()
        val registration = bus.observeDiagnostics {
            if (it is FlowBusDiagnosticEvent.Published) records += it
        }
        try {
            bus.postSticky(key, 1)
            bus.clearSticky(key)
            bus.postSticky(key, 2)
            bus.removeSticky(key)
            bus.postSticky(key, 3)
            assertEquals(3, records.size)
            assertEquals(records[0].channel, records[1].channel)
            assertEquals(records[1].channel.storeId, records[2].channel.storeId)
            assertNotEquals(records[1].channel.flowId, records[2].channel.flowId)
            assertEquals(3, bus.consumeStickyLatest(key))
        } finally {
            registration.close()
        }
    }

    @Test
    fun `throwing observers cannot cancel business sends or block other listeners`() = runTest {
        val bus = FlowBus()
        val key = eventKey<Int>("diagnostics.observer.isolation")
        val records = mutableListOf<FlowBusDiagnosticEvent>()
        val broken = bus.observeDiagnostics { throw IllegalStateException("observer failed") }
        val cancelled = bus.observeDiagnostics { throw CancellationException("observer cancelled") }
        val healthy = bus.observeDiagnostics { records += it }
        try {
            assertTrue(bus.postSticky(key, 9))
            assertEquals(9, bus.stickyFlow(key).first())
            assertEquals(1, records.filterIsInstance<FlowBusDiagnosticEvent.Published>().size)
            assertEquals(1, records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionStarted>().size)
            assertEquals(1, records.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionEnded>().size)
            assertTrue(records.filterIsInstance<FlowBusDiagnosticEvent.Failure>().isEmpty())
            broken.close()
            cancelled.close()
            assertTrue(bus.postSticky(key, 10))
            assertEquals(2, records.filterIsInstance<FlowBusDiagnosticEvent.Published>().size)
        } finally {
            broken.close()
            cancelled.close()
            healthy.close()
        }
    }

    @Test
    fun `observer can inspect post and close itself without recursive diagnostic delivery`() {
        val bus = FlowBus()
        val key = eventKey<Int>("diagnostics.observer.reentry")
        val records = mutableListOf<FlowBusDiagnosticEvent>()
        var selfCalls = 0
        lateinit var self: FlowBusDiagnosticRegistration
        self = bus.observeDiagnostics {
            selfCalls++
            self.close()
            assertTrue(bus.postSticky(key, 2))
            assertEquals(2L, bus.inspect().root.events.single().metrics.acceptedPostCount)
        }
        val other = bus.observeDiagnostics { records += it }
        try {
            assertTrue(bus.postSticky(key, 1))
            assertEquals(1, selfCalls)
            assertEquals(1, records.size)
            assertTrue(records.single() is FlowBusDiagnosticEvent.Published)
            assertEquals(2, bus.consumeStickyLatest(key))
            assertTrue(bus.post(key, 3))
            assertEquals(1, selfCalls)
            assertEquals(2, records.size)
            self.close()
            other.close()
            assertTrue(bus.post(key, 4))
            assertEquals(2, records.size)
        } finally {
            self.close()
            other.close()
        }
    }

    @Test
    fun `registrations of the same observer can be removed independently`() {
        val bus = FlowBus()
        val key = eventKey<Int>("diagnostics.independent.registration")
        var calls = 0
        val observer = FlowBusDiagnosticObserver { calls++ }
        val first = bus.observeDiagnostics(observer)
        val second = bus.observeDiagnostics(observer)
        try {
            bus.post(key, 1)
            assertEquals(2, calls)
            first.close()
            first.close()
            bus.post(key, 2)
            assertEquals(3, calls)
            second.close()
            bus.post(key, 3)
            assertEquals(3, calls)
        } finally {
            first.close()
            second.close()
        }
    }

    @Test
    fun `closing registration does not wait for in flight observer and prevents later calls`() {
        val bus = FlowBus()
        val key = eventKey<Int>("diagnostics.concurrent.close")
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val calls = AtomicInteger()
        val worker = Executors.newSingleThreadExecutor()
        val registration = bus.observeDiagnostics {
            calls.incrementAndGet()
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS))
        }
        try {
            val posting = worker.submit<Boolean> { bus.post(key, 1) }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            registration.close()
            registration.close()
            assertFalse(posting.isDone)
            release.countDown()
            assertTrue(posting.get(5, TimeUnit.SECONDS))
            assertTrue(bus.post(key, 2))
            assertEquals(1, calls.get())
        } finally {
            release.countDown()
            registration.close()
            worker.shutdownNow()
        }
    }

    @Test
    fun `diagnostic callbacks run outside store publication and configuration locks`() {
        val bus = FlowBus()
        val key = eventKey<Int>("diagnostics.lock.boundary")
        val worker = Executors.newSingleThreadExecutor()
        val once = AtomicBoolean()
        val completed = AtomicBoolean()
        val registration = bus.observeDiagnostics {
            if (it is FlowBusDiagnosticEvent.Published && once.compareAndSet(false, true)) {
                worker.submit {
                    bus.configureChannel(eventKey<Int>("diagnostics.other"), FlowBusChannelConfig())
                    bus.postSticky(key, 2)
                    bus.inspect()
                }.get(5, TimeUnit.SECONDS)
                completed.set(true)
            }
        }
        try {
            assertTrue(bus.postSticky(key, 1))
            assertTrue("Another thread must finish while the observer is running", completed.get())
            assertEquals(2, bus.consumeStickyLatest(key))
        } finally {
            registration.close()
            worker.shutdownNow()
        }
    }

    @Test
    fun `subscription started permits another thread to publish and configure before replay`() = runTest {
        for (sticky in listOf(false, true)) {
            val bus = FlowBus()
            val key = eventKey<Int>("diagnostics.started.lock.boundary")
            if (sticky) bus.postSticky(key, 1)
            val worker = Executors.newSingleThreadExecutor()
            val completed = AtomicBoolean()
            val registration = bus.observeDiagnostics {
                if (it is FlowBusDiagnosticEvent.SubscriptionStarted) {
                    worker.submit {
                        bus.configureChannel(eventKey<Int>("diagnostics.started.other"), FlowBusChannelConfig())
                        if (sticky) bus.postSticky(key, 2) else bus.post(key, 2)
                        bus.inspect()
                    }.get(5, TimeUnit.SECONDS)
                    completed.set(true)
                }
            }
            val received = mutableListOf<Int>()
            val source = if (sticky) bus.stickyFlow(key) else bus.flow(key)
            val collecting = launch(start = CoroutineStart.UNDISPATCHED) { source.collect { received += it } }
            try {
                assertTrue("Another thread must finish while Started is being observed", completed.get())
                runCurrent()
                assertEquals(if (sticky) listOf(1, 2) else listOf(2), received)
            } finally {
                collecting.cancelAndJoin()
                registration.close()
                worker.shutdownNow()
            }
        }
    }

    @Test
    fun `diagnostic records retain metadata without payload or throwable messages`() = runTest {
        val bus = FlowBus()
        val key = eventKey<PrivatePayload>("diagnostics.private")
        val payload = PrivatePayload()
        val secret = "private-business-content"
        val records = mutableListOf<FlowBusDiagnosticEvent>()
        val registration = bus.observeDiagnostics { records += it }
        try {
            bus.postSticky(key, payload)
            val expected = IllegalStateException(secret)
            val actual = runCatching { bus.collectSticky(key) { throw expected } }.exceptionOrNull()
            assertSame(expected, actual)
            assertEquals(1, records.filterIsInstance<FlowBusDiagnosticEvent.Failure>().size)
            records.forEach { assertMetadataOnly(it, payload, secret) }
        } finally {
            registration.close()
        }
    }

    private fun rendezvousConfig() = FlowBusConfig(
        normalBufferCapacity = 0,
        stickyReplay = 0,
        stickyExtraBufferCapacity = 0,
        overflowPolicy = BufferOverflow.SUSPEND
    )

    private fun assertMetadataOnly(value: Any?, payload: Any, secret: String) {
        when (value) {
            null, is Number, is Boolean, is Enum<*> -> return
            is String -> assertFalse(value.contains(secret))
            else -> {
                assertNotSame(payload, value)
                assertFalse(value is Throwable)
                assertTrue(value.javaClass.name.startsWith("com.logan.flowbus.core.FlowBus"))
                value.javaClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }.forEach {
                    it.isAccessible = true
                    assertMetadataOnly(it.get(value), payload, secret)
                }
            }
        }
    }

    private class PrivatePayload {
        override fun toString(): String = error("Diagnostics must not format an event payload")
    }
}
