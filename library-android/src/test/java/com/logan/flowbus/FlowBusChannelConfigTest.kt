package com.logan.flowbus

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusChannelConfig
import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.FlowBusLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowBusChannelConfigTest {
    private class Owner(override val viewModelStore: ViewModelStore = ViewModelStore()) : LifecycleOwner, ViewModelStoreOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private val channel = eventChannel<Int>("android.configured")
    private val suspendConfig = FlowBusChannelConfig(
        normalBufferCapacity = 0,
        normalOverflowPolicy = BufferOverflow.SUSPEND
    )

    private fun checkLifecycle(block: suspend TestScope.() -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        FlowBusAndroid.resetForTests()
        FlowBusAndroid.configure(FlowBusConfig(logger = FlowBusLogger.None))
        try {
            block()
        } finally {
            FlowBusAndroid.resetForTests()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `global channel configuration uses the existing global bus and leaves owners independent`() = checkLifecycle {
        val owner = Owner()
        try {
            configureEventChannel(channel, suspendConfig)
            val global = tryPostEventResult(1, eventName = channel.name)
            val local = tryPostEventResultTo(owner, 1, eventName = channel.name)
            assertEquals(BufferOverflow.SUSPEND, global.overflowPolicy)
            assertEquals(BufferOverflow.DROP_OLDEST, local.overflowPolicy)
            configureEventChannel(channel, suspendConfig)
        } finally {
            owner.viewModelStore.clear()
        }
    }

    @Test
    fun `owner configuration isolates policy and lifecycle collection`() = checkLifecycle {
        val first = Owner()
        val second = Owner()
        val firstReceived = mutableListOf<Int>()
        val secondReceived = mutableListOf<Int>()
        configureEventChannel(channel, suspendConfig, first)
        configureEventChannel(channel, FlowBusChannelConfig(normalBufferCapacity = 1), second)
        first.registry.currentState = Lifecycle.State.STARTED
        second.registry.currentState = Lifecycle.State.STARTED
        val firstJob = first.onEvent(channel = channel, from = first) { firstReceived += it }
        val secondJob = second.onEvent(channel = channel, from = second) { secondReceived += it }
        try {
            runCurrent()
            assertFalse(channel.tryPostTo(first, 1))
            assertTrue(channel.tryPostTo(second, 2))
            runCurrent()
            assertTrue(firstReceived.isEmpty())
            assertEquals(listOf(2), secondReceived)
            channel.emitTo(first, 3)
            runCurrent()
            assertEquals(listOf(3), firstReceived)
        } finally {
            firstJob.cancelAndJoin()
            secondJob.cancelAndJoin()
            first.viewModelStore.clear()
            second.viewModelStore.clear()
        }
    }

    @Test
    fun `retained owner configuration is idempotent across receiver recreation`() = checkLifecycle {
        val retainedStore = ViewModelStore()
        val oldOwner = Owner(retainedStore)
        configureEventChannel(channel, suspendConfig, oldOwner)
        val originalFlow = channel.flowFrom(oldOwner)
        val oldReceived = mutableListOf<Int>()
        oldOwner.registry.currentState = Lifecycle.State.STARTED
        val oldJob = oldOwner.onEvent(channel = channel, from = oldOwner) { oldReceived += it }
        runCurrent()
        channel.emitTo(oldOwner, 1)
        runCurrent()
        oldOwner.registry.currentState = Lifecycle.State.DESTROYED
        runCurrent()
        assertTrue(oldJob.isCompleted)
        channel.emitTo(oldOwner, 2)

        val newOwner = Owner(retainedStore)
        configureEventChannel(channel, suspendConfig, newOwner)
        assertSame(originalFlow, channel.flowFrom(newOwner))
        val newReceived = mutableListOf<Int>()
        val newJob = newOwner.onEvent(channel = channel, from = newOwner) { newReceived += it }
        try {
            newOwner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            channel.emitTo(newOwner, 3)
            runCurrent()
            assertEquals(listOf(1), oldReceived)
            assertEquals(listOf(3), newReceived)
            newOwner.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            assertTrue(newJob.isCompleted)
        } finally {
            oldJob.cancelAndJoin()
            newJob.cancelAndJoin()
            retainedStore.clear()
        }
        val freshOwner = Owner()
        try {
            configureEventChannel(channel, FlowBusChannelConfig(), freshOwner)
            assertEquals(BufferOverflow.DROP_OLDEST, tryPostEventResultTo(freshOwner, 4, channel.name).overflowPolicy)
        } finally {
            freshOwner.viewModelStore.clear()
        }
    }

    @Test
    fun `configured suspending owner subscription cancels in flight and resumes without replay`() = checkLifecycle {
        val owner = Owner()
        configureEventChannel(channel, FlowBusChannelConfig(normalBufferCapacity = 1, normalOverflowPolicy = BufferOverflow.SUSPEND), owner)
        owner.registry.currentState = Lifecycle.State.STARTED
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val never = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val job = owner.onEventSuspending(channel = channel, from = owner) {
            received += it
            if (it == 1) {
                started.complete(Unit)
                try { never.await() } finally { cancelled.complete(Unit) }
            }
        }
        try {
            runCurrent()
            channel.emitTo(owner, 1)
            runCurrent()
            assertTrue(started.isCompleted)
            owner.registry.currentState = Lifecycle.State.CREATED
            runCurrent()
            assertTrue(cancelled.isCompleted)
            channel.emitTo(owner, 2)
            owner.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            channel.emitTo(owner, 3)
            runCurrent()
            assertEquals(listOf(1, 3), received)
        } finally {
            job.cancelAndJoin()
            owner.viewModelStore.clear()
        }
    }

    @Test
    fun `configured explicit root shared and owned sources reuse instance lifecycle adapters`() = checkLifecycle {
        val owner = Owner()
        val bus = FlowBus()
        val key = channel.asEventKey()
        val shared = bus.scoped("shared")
        val owned = bus.openScope("owned")
        bus.configureChannel(key, suspendConfig)
        shared.configureChannel(key, suspendConfig)
        owned.configureChannel(key, suspendConfig)
        val received = mutableListOf<String>()
        owner.registry.currentState = Lifecycle.State.STARTED
        val jobs = listOf(
            owner.collectEventFrom(bus, key) { received += "root:$it" },
            owner.collectEventFrom(shared, key) { received += "shared:$it" },
            owner.collectEventFromSuspending(owned, key) { received += "owned:$it" }
        )
        try {
            runCurrent()
            bus.emit(key, 1)
            shared.emit(key, 2)
            owned.emit(key, 3)
            runCurrent()
            assertEquals(listOf("root:1", "shared:2", "owned:3"), received)
            owner.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            assertTrue(jobs.all { it.isCompleted })
            assertFalse(owned.isClosed)
            assertEquals(BufferOverflow.SUSPEND, owned.tryPostResult(key, 4).overflowPolicy)
        } finally {
            jobs.forEach { it.cancelAndJoin() }
            owned.close()
            owner.viewModelStore.clear()
        }
    }

    @Test
    fun `android instance accepts valid configuration after failure and rejects later strategy changes`() = checkLifecycle {
        val bus = FlowEventBus()
        val key = channel.asEventKey()
        assertThrows(IllegalArgumentException::class.java) {
            bus.configureChannel(key, FlowBusChannelConfig(normalBufferCapacity = -1))
        }
        bus.configureChannel(key, suspendConfig)
        assertEquals(BufferOverflow.SUSPEND, bus.postResult(channel.name, 1, Int::class).overflowPolicy)
        assertThrows(IllegalStateException::class.java) { bus.configureChannel(key, FlowBusChannelConfig()) }
        assertEquals(BufferOverflow.SUSPEND, bus.postResult(channel.name, 2, Int::class).overflowPolicy)
    }
}
