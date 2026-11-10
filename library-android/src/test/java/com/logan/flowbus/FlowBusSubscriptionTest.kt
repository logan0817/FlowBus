package com.logan.flowbus

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowBusSubscriptionTest {
    @Test
    fun `normal subscription notifies on actual subscription in each active lifecycle`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val owner = Owner()
        val bus = FlowBus()
        val key = eventKey<Int>("subscribed.lifecycle")
        val received = mutableListOf<Int>()
        var subscriptions = 0
        val job = owner.collectEventFromSuspending(
            from = bus,
            key = key,
            onSubscribed = {
                subscriptions++
                assertEquals(1, bus.inspectChannel(key)?.normalSubscriptionCount)
                bus.post(key, subscriptions)
            },
            dispatcher = main,
            onReceived = { received += it }
        )
        try {
            owner.registry.currentState = Lifecycle.State.CREATED
            runCurrent()
            assertEquals(0, subscriptions)
            bus.post(key, 99)
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(listOf(1), received)
            owner.registry.currentState = Lifecycle.State.CREATED
            runCurrent()
            assertEquals(0, bus.inspectChannel(key)?.normalSubscriptionCount)
            bus.post(key, 100)
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(2, subscriptions)
            assertEquals(listOf(1, 2), received)
            owner.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            assertTrue(job.isCompleted)
            assertEquals(0, bus.inspectChannel(key)?.normalSubscriptionCount)
        } finally {
            job.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `shared scope notifies before sticky replay and uses replacement store on reentry`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val owner = Owner()
        val bus = FlowBus()
        val source = bus.scoped("subscribed.shared")
        val key = eventKey<Int>("subscribed.replay")
        val calls = mutableListOf<String>()
        source.postSticky(key, 1)
        val job = owner.collectEventFromSuspending(
            from = source,
            key = key,
            onSubscribed = { calls += "subscribed" },
            dispatcher = main,
            isSticky = true,
            onReceived = { calls += "event:$it" }
        )
        try {
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(listOf("subscribed", "event:1"), calls)
            owner.registry.currentState = Lifecycle.State.CREATED
            runCurrent()
            source.removeScope()
            source.postSticky(key, 2)
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(listOf("subscribed", "event:1", "subscribed", "event:2"), calls)
        } finally {
            owner.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            job.cancelAndJoin()
            source.removeScope()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `owned scope respects resumed threshold and cancellation preserves source`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        val owner = Owner()
        val source = FlowBus().openScope("subscribed.owned")
        val key = eventKey<Int>("subscribed.owned.event")
        var subscriptions = 0
        val received = mutableListOf<Int>()
        val job = owner.collectEventFromSuspending(
            from = source,
            key = key,
            onSubscribed = { subscriptions++ },
            dispatcher = main,
            minLifecycleState = Lifecycle.State.RESUMED,
            onReceived = { received += it }
        )
        try {
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(0, subscriptions)
            owner.registry.currentState = Lifecycle.State.RESUMED
            runCurrent()
            assertEquals(1, subscriptions)
            source.post(key, 1)
            runCurrent()
            assertEquals(listOf(1), received)
            job.cancelAndJoin()
            assertFalse(source.isClosed)
            assertEquals(0, source.inspectChannel(key)?.normalSubscriptionCount)
            source.post(key, 2)
            runCurrent()
            assertEquals(listOf(1), received)
        } finally {
            owner.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            job.cancelAndJoin()
            source.close()
            Dispatchers.resetMain()
        }
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
