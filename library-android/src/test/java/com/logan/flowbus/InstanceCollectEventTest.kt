package com.logan.flowbus

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.LifecycleRegistry
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.FlowBusErrorContext
import com.logan.flowbus.core.FlowBusErrorHandler
import com.logan.flowbus.core.FlowBusErrorPhase
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class InstanceCollectEventTest {
    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    @Test
    fun `explicit instance keeps isolation and restarts only while active`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val owner = Owner()
        val bus = FlowBus()
        val other = FlowBus()
        val key = eventKey<Int>("instance.normal")
        val received = mutableListOf<Int>()
        val job = owner.collectEventFrom(bus, key, dispatcher = main) { received += it }
        try {
            owner.registry.currentState = Lifecycle.State.CREATED
            runCurrent()
            bus.post(key, 1)
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            other.post(key, 2)
            bus.post(key, 3)
            runCurrent()
            assertEquals(listOf(3), received)
            owner.registry.currentState = Lifecycle.State.CREATED
            runCurrent()
            bus.post(key, 4)
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            bus.post(key, 5)
            runCurrent()
            assertEquals(listOf(3, 5), received)
            owner.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            assertTrue(job.isCompleted)
            assertEquals(0, (bus.flow(key) as MutableSharedFlow<Int>).subscriptionCount.value)
        } finally {
            job.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `suspending lifecycle callback cancels in flight and applies instance errors`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val errors = mutableListOf<FlowBusErrorContext>()
        val bus = FlowBus(FlowBusConfig(errorHandler = FlowBusErrorHandler { context, _ -> errors += context }))
        val owner = Owner()
        val key = eventKey<Int>("instance.suspending")
        val cancelled = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val job = owner.collectEventFromSuspending(bus, key, dispatcher = main) { value ->
            received += value
            if (value == 1) {
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
            if (value == 3) throw IllegalStateException("callback")
        }
        try {
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            bus.post(key, 1)
            runCurrent()
            bus.post(key, 2)
            runCurrent()
            assertEquals(listOf(1), received)
            owner.registry.currentState = Lifecycle.State.CREATED
            runCurrent()
            assertTrue(cancelled.isCompleted)
            assertTrue(errors.isEmpty())
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            bus.post(key, 3)
            runCurrent()
            bus.post(key, 4)
            runCurrent()
            assertEquals(listOf(1, 3, 4), received)
            assertEquals(FlowBusErrorPhase.SubscriberCallback, errors.single().phase)
        } finally {
            owner.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            job.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `scoped sticky source refreshes after inactive store replacement`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val bus = FlowBus()
        val scoped = bus.scoped("instance.session")
        val owner = Owner()
        val key = eventKey<Int>("instance.sticky")
        val received = mutableListOf<Int>()
        scoped.postSticky(key, 1)
        val job = owner.collectEventFromSuspending(scoped, key, dispatcher = main, isSticky = true) { received += it }
        try {
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(listOf(1), received)
            owner.registry.currentState = Lifecycle.State.CREATED
            runCurrent()
            scoped.removeScope()
            scoped.postSticky(key, 2)
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(listOf(1, 2), received)
        } finally {
            owner.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            job.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `handle subscription preserves ownership and resume threshold`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val bus = FlowBus()
        val scope = bus.openScope("instance.handle")
        val key = eventKey<Int>("instance.handle.event")
        val owner = Owner()
        val received = mutableListOf<Int>()
        val job = owner.collectEventFromSuspending(scope, key, dispatcher = main,
            minLifecycleState = Lifecycle.State.RESUMED, isSticky = true) { received += it }
        try {
            scope.postSticky(key, 1)
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertTrue(received.isEmpty())
            owner.registry.currentState = Lifecycle.State.RESUMED
            runCurrent()
            assertEquals(listOf(1), received)
            owner.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            assertTrue(job.isCompleted)
            assertFalse(scope.isClosed)
            assertTrue(scope.post(key, 2))
        } finally {
            job.cancelAndJoin()
            scope.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `ordinary scoped and handle adapters preserve source errors and sticky replay`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val contexts = mutableListOf<FlowBusErrorContext>()
        val bus = FlowBus(FlowBusConfig(errorHandler = FlowBusErrorHandler { context, _ -> contexts += context }))
        val scoped = bus.scoped("ordinary.shared")
        val handle = bus.openScope("ordinary.owned")
        val owner = Owner()
        val key = eventKey<Int>("ordinary.adapters")
        val first = owner.collectEventFrom(scoped, key, main, isSticky = true) { throw IllegalStateException("scoped") }
        val second = owner.collectEventFrom(handle, key, main, isSticky = true) { throw IllegalStateException("owned") }
        try {
            scoped.postSticky(key, 1)
            handle.postSticky(key, 2)
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(setOf("ordinary.shared", "ordinary.owned"), contexts.map { it.scopeName }.toSet())
            assertTrue(contexts.all { it.isSticky && it.phase == FlowBusErrorPhase.SubscriberCallback })
            assertTrue(first.isActive && second.isActive)
            owner.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            assertFalse(handle.isClosed)
        } finally {
            first.cancelAndJoin()
            second.cancelAndJoin()
            handle.close()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `lifecycle and coroutine suspend aliases route reified and channel sticky sources`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        FlowBusAndroid.resetForTests()
        FlowBusAndroid.configure(FlowBusConfig(logger = com.logan.flowbus.core.FlowBusLogger.None))
        val receiver = Owner()
        val source = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        val name = "suspending.aliases"
        val channel = eventChannel<Int>(name)
        val calls = mutableListOf<Int>()
        val callbacks: suspend (Int) -> Unit = { delay(1); calls += it }
        try {
            receiver.registry.currentState = Lifecycle.State.STARTED
            val subscriptions = listOf<() -> kotlinx.coroutines.Job>(
                { receiver.onEventSuspending(channel, main, isSticky = true, onReceived = callbacks) },
                { receiver.onEventSuspending<Int>(main, isSticky = true, eventName = name, onReceived = callbacks) },
                { receiver.onEventSuspending(source, channel, main, isSticky = true, onReceived = callbacks) },
                { receiver.onEventSuspending<Int>(source, main, isSticky = true, eventName = name, onReceived = callbacks) },
                { onEventSuspending(channel, isSticky = true, onReceived = callbacks) },
                { onEventSuspending<Int>(isSticky = true, eventName = name, onReceived = callbacks) },
                { onEventSuspending(source, channel, isSticky = true, onReceived = callbacks) },
                { onEventSuspending<Int>(source, isSticky = true, eventName = name, onReceived = callbacks) }
            )
            subscriptions.forEachIndexed { index, subscribe ->
                calls.clear()
                postStickyEvent(100 + index, eventName = name)
                postStickyEventTo(source, 200 + index, eventName = name)
                val subscription = subscribe()
                try {
                    advanceUntilIdle()
                    val fromOwner = index % 4 >= 2
                    assertEquals(listOf((if (fromOwner) 200 else 100) + index), calls)
                } finally { subscription.cancelAndJoin() }
            }
        } finally {
            receiver.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            source.viewModelStore.clear()
            FlowBusAndroid.resetForTests()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `generic suspend flow collection waits sequentially and stops at destruction`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val owner = Owner()
        val release = CompletableDeferred<Unit>()
        val calls = mutableListOf<Int>()
        val job = owner.collectEventSuspending(flowOf(1, 2), main) {
            calls += it
            if (it == 1) release.await()
            if (it == 2) awaitCancellation()
        }
        try {
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(listOf(1), calls)
            release.complete(Unit)
            runCurrent()
            assertEquals(listOf(1, 2), calls)
            owner.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            assertTrue(job.isCompleted)
        } finally {
            job.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `closed handle fails on resumed subscription without switching to new same name handle`() {
        val failure = IllegalStateException::class.java
        val thrown = assertThrows(failure) {
            runTest {
                val main = StandardTestDispatcher(testScheduler)
                Dispatchers.setMain(main)
                val bus = FlowBus()
                val old = bus.openScope("closed.resume")
                val key = eventKey<Int>("closed.resume.event")
                val receiver = Owner()
                val calls = mutableListOf<Int>()
                val job = receiver.collectEventFromSuspending(old, key, main, isSticky = true) { calls += it }
                try {
                    receiver.registry.currentState = Lifecycle.State.STARTED
                    runCurrent()
                    receiver.registry.currentState = Lifecycle.State.CREATED
                    runCurrent()
                    old.close()
                    val replacement = bus.openScope(old.scopeName)
                    try {
                        replacement.postSticky(key, 99)
                        receiver.registry.currentState = Lifecycle.State.STARTED
                        runCurrent()
                        assertTrue(job.isCancelled)
                        assertTrue(calls.isEmpty())
                    } finally { replacement.close() }
                } finally {
                    receiver.registry.currentState = Lifecycle.State.DESTROYED
                    runCurrent()
                    job.cancelAndJoin()
                    old.close()
                    Dispatchers.resetMain()
                }
            }
        }
        assertTrue(thrown.message.orEmpty().contains("closed.resume"))
    }

    @Test
    fun `generic suspend flow callback failure propagates to caller`() {
        val expected = IllegalArgumentException("generic.callback")
        val thrown = assertThrows(IllegalArgumentException::class.java) {
            runTest {
                val main = StandardTestDispatcher(testScheduler)
                Dispatchers.setMain(main)
                val receiver = Owner()
                val job = receiver.collectEventSuspending(flowOf(1), main) { throw expected }
                try {
                    receiver.registry.currentState = Lifecycle.State.STARTED
                    runCurrent()
                    assertTrue(job.isCancelled)
                } finally {
                    receiver.registry.currentState = Lifecycle.State.DESTROYED
                    runCurrent()
                    job.cancelAndJoin()
                    Dispatchers.resetMain()
                }
            }
        }
        assertEquals(expected.message, thrown.message)
        // 协程调试的堆栈恢复可能复制异常，但原异常必须保留在因果链中。
        assertTrue(generateSequence<Throwable>(thrown) { it.cause }.any { it === expected })
    }

    @Test
    fun `owner lifecycle suspend alias cancels handling and resubscribes after stop`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        FlowBusAndroid.resetForTests()
        val errors = mutableListOf<FlowBusErrorContext>()
        FlowBusAndroid.configure(FlowBusConfig(errorHandler = FlowBusErrorHandler { context, _ -> errors += context }))
        val receiver = Owner()
        val source = object : ViewModelStoreOwner { override val viewModelStore = ViewModelStore() }
        val name = "owner.lifecycle.suspend"
        val calls = mutableListOf<Int>()
        val cancelled = CompletableDeferred<Unit>()
        val job = receiver.onEventSuspending<Int>(from = source, dispatcher = main, eventName = name) {
            calls += it
            if (it == 1) {
                try { awaitCancellation() } finally { cancelled.complete(Unit) }
            }
        }
        try {
            receiver.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            postEventTo(source, 1, eventName = name)
            runCurrent()
            receiver.registry.currentState = Lifecycle.State.CREATED
            runCurrent()
            assertTrue(cancelled.isCompleted)
            assertTrue(errors.isEmpty())
            postEventTo(source, 2, eventName = name)
            receiver.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            postEventTo(source, 3, eventName = name)
            runCurrent()
            assertEquals(listOf(1, 3), calls)
        } finally {
            receiver.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            job.cancelAndJoin()
            source.viewModelStore.clear()
            FlowBusAndroid.resetForTests()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `all instance entry points reject initialized before starting collection`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val owner = Owner()
        val bus = FlowBus()
        val key = eventKey<Int>("instance.invalid")
        val scoped = bus.scoped("invalid")
        val handle = bus.openScope("invalid.handle")
        val calls = listOf<() -> Unit>(
            { owner.collectEventFrom(bus, key, main, Lifecycle.State.INITIALIZED) {} },
            { owner.collectEventFrom(scoped, key, main, Lifecycle.State.INITIALIZED) {} },
            { owner.collectEventFrom(handle, key, main, Lifecycle.State.INITIALIZED) {} },
            { owner.collectEventFromSuspending(bus, key, main, Lifecycle.State.INITIALIZED) {} },
            { owner.collectEventFromSuspending(scoped, key, main, Lifecycle.State.INITIALIZED) {} },
            { owner.collectEventFromSuspending(handle, key, main, Lifecycle.State.INITIALIZED) {} },
            { owner.collectEventSuspending(flowOf(1), main, Lifecycle.State.INITIALIZED) {} }
        )
        try {
            calls.forEach { call ->
                try { call(); fail("Must reject INITIALIZED") }
                catch (expected: IllegalArgumentException) { assertTrue(expected.message.orEmpty().contains("INITIALIZED")) }
            }
        } finally {
            handle.close()
            Dispatchers.resetMain()
        }
    }
}
