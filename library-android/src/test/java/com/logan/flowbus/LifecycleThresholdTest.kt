package com.logan.flowbus

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LifecycleThresholdTest {
    @Test
    fun `generic and direct bus entry points reject invalid thresholds before collecting`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val receiver = Owner()
        val source = FlowEventBus()
        val key = eventKey<Int>("threshold.direct")
        val returnedJobs = mutableListOf<Job>()
        var collections = 0
        var received = 0
        var subscriptions = 0
        val events = flow { collections++; emit(1) }
        try {
            receiver.registry.currentState = Lifecycle.State.RESUMED
            for (state in invalidStates) {
                val calls = listOf<() -> Job>(
                    { receiver.collectEvent(events, main, state) { received++ } },
                    { receiver.collectEventSuspending(events, main, state) { received++ } },
                    { source.subscribeEvent(receiver, key.name, Int::class, state, main, false) { received++ } },
                    { source.subscribeEventSuspending(receiver, key.name, Int::class, state, main, false) { received++ } },
                    { source.subscribeEventSuspending(receiver, key, { subscriptions++ }, state, main) { received++ } }
                )
                calls.forEach { call ->
                    val error = assertThrows(IllegalArgumentException::class.java) { returnedJobs += call() }
                    assertTrue(error.message.orEmpty().contains(state.name))
                }
            }
            runCurrent()
            assertEquals(0, collections)
            assertEquals(0, received)
            assertEquals(0, subscriptions)
            assertNull(source.inspectChannel(key))
        } finally {
            returnedJobs.forEach { it.cancelAndJoin() }
            receiver.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            FlowBusAndroid.resetForTests()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `invalid global subscriptions leave default configuration mutable`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val receiver = Owner()
        val channel = eventChannel<Int>("threshold.global")
        val returnedJobs = mutableListOf<Job>()
        var factoryCalls = 0
        FlowBusAndroid.resetForTests()
        FlowBusAndroid.configure { factoryCalls++; FlowBusConfig() }
        try {
            receiver.registry.currentState = Lifecycle.State.RESUMED
            for (state in invalidStates) {
                val calls = listOf<() -> Job>(
                    { receiver.subscribeEvent<Int>(minLifecycleState = state) {} },
                    { receiver.subscribeEvent(channel, minLifecycleState = state) {} },
                    { receiver.onEvent<Int>(minLifecycleState = state) {} },
                    { receiver.onEvent(channel, minLifecycleState = state) {} },
                    { receiver.onEventSuspending<Int>(minLifecycleState = state) {} },
                    { receiver.onEventSuspending(channel, minLifecycleState = state) {} }
                )
                calls.forEach { call ->
                    assertThrows(IllegalArgumentException::class.java) { returnedJobs += call() }
                    assertEquals(0, factoryCalls)
                    FlowBusAndroid.configure { factoryCalls++; FlowBusConfig() }
                }
            }
        } finally {
            returnedJobs.forEach { it.cancelAndJoin() }
            receiver.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            FlowBusAndroid.resetForTests()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `invalid owner subscriptions do not access the source store`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val receiver = Owner()
        val source = StoreOwner()
        val channel = eventChannel<Int>("threshold.owner")
        val returnedJobs = mutableListOf<Job>()
        try {
            receiver.registry.currentState = Lifecycle.State.RESUMED
            for (state in invalidStates) {
                val calls = listOf<() -> Job>(
                    { receiver.subscribeEvent<Int>(source, minLifecycleState = state) {} },
                    { receiver.subscribeEvent(source, channel, minLifecycleState = state) {} },
                    { receiver.onEvent<Int>(source, minLifecycleState = state) {} },
                    { receiver.onEvent(source, channel, minLifecycleState = state) {} },
                    { receiver.onEventSuspending<Int>(source, minLifecycleState = state) {} },
                    { receiver.onEventSuspending(source, channel, minLifecycleState = state) {} }
                )
                calls.forEach { call ->
                    assertThrows(IllegalArgumentException::class.java) { returnedJobs += call() }
                    assertEquals(0, source.storeReads)
                }
            }
        } finally {
            returnedJobs.forEach { it.cancelAndJoin() }
            receiver.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            source.store.clear()
            FlowBusAndroid.resetForTests()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `valid thresholds start and restart only at their lifecycle boundary`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        try {
            for (state in validStates) {
                val receiver = Owner()
                val source = FlowBus()
                val key = eventKey<Int>("threshold.valid.${state.name}")
                var subscriptions = 0
                val received = mutableListOf<Int>()
                val job = receiver.collectEventFromSuspending(
                    from = source,
                    key = key,
                    onSubscribed = { subscriptions++ },
                    dispatcher = main,
                    minLifecycleState = state,
                    onReceived = { received += it }
                )
                try {
                    val below = when (state) {
                        Lifecycle.State.CREATED -> Lifecycle.State.INITIALIZED
                        Lifecycle.State.STARTED -> Lifecycle.State.CREATED
                        else -> Lifecycle.State.STARTED
                    }
                    receiver.registry.currentState = below
                    runCurrent()
                    assertEquals(0, subscriptions)
                    receiver.registry.currentState = state
                    runCurrent()
                    assertEquals(1, subscriptions)
                    source.post(key, 1)
                    runCurrent()
                    assertEquals(listOf(1), received)
                    if (state == Lifecycle.State.CREATED) {
                        receiver.registry.currentState = Lifecycle.State.STARTED
                        runCurrent()
                        receiver.registry.currentState = Lifecycle.State.RESUMED
                        runCurrent()
                        assertEquals(1, subscriptions)
                    } else {
                        receiver.registry.currentState = below
                        runCurrent()
                        assertEquals(0, source.inspectChannel(key)?.normalSubscriptionCount)
                        source.post(key, 2)
                        receiver.registry.currentState = state
                        runCurrent()
                        assertEquals(2, subscriptions)
                        source.post(key, 3)
                        runCurrent()
                        assertEquals(listOf(1, 3), received)
                    }
                    receiver.registry.currentState = Lifecycle.State.DESTROYED
                    runCurrent()
                    assertTrue(job.isCompleted)
                    assertEquals(0, source.inspectChannel(key)?.normalSubscriptionCount)
                } finally {
                    job.cancelAndJoin()
                    receiver.registry.currentState = Lifecycle.State.DESTROYED
                    runCurrent()
                }
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `destroyed receiver with valid thresholds completes without subscribing`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val receiver = Owner()
        receiver.registry.currentState = Lifecycle.State.CREATED
        receiver.registry.currentState = Lifecycle.State.DESTROYED
        val source = FlowBus()
        val key = eventKey<Int>("threshold.destroyed.receiver")
        val jobs = mutableListOf<Job>()
        var callbacks = 0
        try {
            for (state in validStates) {
                jobs += receiver.collectEventFromSuspending(
                    from = source, key = key, onSubscribed = { callbacks++ },
                    dispatcher = main, minLifecycleState = state, onReceived = { callbacks++ }
                )
                jobs += receiver.collectEvent(flow<Int> { callbacks++; emit(1) }, main, state) { callbacks++ }
            }
            runCurrent()
            assertTrue(jobs.all { it.isCompleted })
            assertFalse(jobs.any { it.isActive })
            assertEquals(0, callbacks)
            assertNull(source.inspectChannel(key))
        } finally {
            jobs.forEach { it.cancelAndJoin() }
            Dispatchers.resetMain()
        }
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private class StoreOwner : ViewModelStoreOwner {
        val store = ViewModelStore()
        var storeReads = 0
        override val viewModelStore: ViewModelStore
            get() {
                storeReads++
                return store
            }
    }

    private val invalidStates = listOf(Lifecycle.State.INITIALIZED, Lifecycle.State.DESTROYED)
    private val validStates = listOf(Lifecycle.State.CREATED, Lifecycle.State.STARTED, Lifecycle.State.RESUMED)
}
