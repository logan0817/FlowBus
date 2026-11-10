package com.logan.flowbus

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.FlowBusErrorContext
import com.logan.flowbus.core.FlowBusErrorHandler
import com.logan.flowbus.core.FlowBusErrorPhase
import com.logan.flowbus.core.FlowBusLogger
import com.logan.flowbus.core.FlowBusPostOutcome
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlowBusJavaTest {
    @Test
    fun `class factory does not create bus and post results describe actual stream`() {
        FlowBusAndroid.resetForTests()
        val channel = FlowBusJava.channel("java.results", String::class.java)
        try {
            FlowBusAndroid.configure(FlowBusConfig(logger = FlowBusLogger.None))
            assertNull(FlowBusJava.inspectChannel(channel))
            val normal = FlowBusJava.postResult(channel, "normal")
            val sticky = FlowBusJava.postStickyResult(channel, "sticky")
            assertTrue(normal.accepted)
            assertFalse(normal.isSticky)
            assertEquals(FlowBusPostOutcome.AcceptedWithoutActiveSubscriber, normal.outcome)
            assertTrue(sticky.accepted)
            assertTrue(sticky.isSticky)
            assertEquals(1, sticky.stickyReplayCount)
            assertEquals("sticky", channel.consumeStickyLatest())
        } finally {
            FlowBusAndroid.resetForTests()
        }
    }

    @Test
    fun `facade and Kotlin channels share sources without mixing owners or streams`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        FlowBusAndroid.resetForTests()
        FlowBusAndroid.configure(FlowBusConfig(logger = FlowBusLogger.None))
        val receiver = Owner()
        val source = Source()
        val other = Source()
        val channel = FlowBusJava.channel("java.interop", String::class.java)
        val global = mutableListOf<String>()
        val owned = mutableListOf<String>()
        val sticky = mutableListOf<String>()
        val kotlin = mutableListOf<String>()
        val subscriptions = listOf(
            FlowBusJava.subscribe(receiver, channel, FlowBusJava.EventListener { global += it }),
            FlowBusJava.subscribeFrom(receiver, source, channel, FlowBusJava.EventListener { owned += it }),
            FlowBusJava.subscribeStickyFrom(receiver, source, channel, FlowBusJava.EventListener { sticky += it })
        )
        val kotlinJob = receiver.onEvent(source, channel, dispatcher = main) { kotlin += it }
        try {
            receiver.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            channel.post("global")
            assertTrue(FlowBusJava.postTo(source, channel, "owner"))
            assertTrue(FlowBusJava.postStickyTo(source, channel, "sticky"))
            FlowBusJava.postTo(other, channel, "other")
            runCurrent()
            assertEquals(listOf("global"), global)
            assertEquals(listOf("owner"), owned)
            assertEquals(listOf("owner"), kotlin)
            assertEquals(listOf("sticky"), sticky)
            val snapshot = requireNotNull(FlowBusJava.inspectChannelFrom(source, channel))
            assertEquals(2, snapshot.normalSubscriptionCount)
            assertEquals(1, snapshot.stickySubscriptionCount)
            subscriptions.forEach {
                it.cancel()
                it.cancel()
            }
            runCurrent()
            assertTrue(subscriptions.all { it.isCancelled && it.isCompleted })
            FlowBusJava.postTo(source, channel, "after-cancel")
            runCurrent()
            assertEquals(listOf("owner"), owned)
            assertEquals(listOf("owner", "after-cancel"), kotlin)
        } finally {
            receiver.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            subscriptions.forEach { it.cancel() }
            kotlinJob.cancelAndJoin()
            source.viewModelStore.clear()
            other.viewModelStore.clear()
            FlowBusAndroid.resetForTests()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `ready hook repeats only in active lifecycles and can publish to its subscription`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        FlowBusAndroid.resetForTests()
        FlowBusAndroid.configure(FlowBusConfig(logger = FlowBusLogger.None))
        val receiver = Owner()
        val channel = FlowBusJava.channel("java.ready", String::class.java)
        var ready = 0
        val received = mutableListOf<String>()
        val subscription = FlowBusJava.subscribe(
            receiver,
            channel,
            Runnable {
                ready++
                FlowBusJava.post(channel, "ready:$ready")
            },
            FlowBusJava.EventListener { received += it }
        )
        try {
            receiver.registry.currentState = Lifecycle.State.CREATED
            runCurrent()
            assertEquals(0, ready)
            receiver.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(listOf("ready:1"), received)
            receiver.registry.currentState = Lifecycle.State.CREATED
            runCurrent()
            assertFalse(subscription.isCancelled)
            assertFalse(subscription.isCompleted)
            FlowBusJava.post(channel, "offline")
            receiver.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(listOf("ready:1", "ready:2"), received)
            receiver.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            assertTrue(subscription.isCompleted)
            FlowBusJava.post(channel, "destroyed")
            runCurrent()
            assertEquals(2, ready)
            assertEquals(listOf("ready:1", "ready:2"), received)
        } finally {
            subscription.cancel()
            runCurrent()
            FlowBusAndroid.resetForTests()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `listener errors use source policy and sticky hook precedes replay`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        FlowBusAndroid.resetForTests()
        val errors = mutableListOf<FlowBusErrorContext>()
        FlowBusAndroid.configure(FlowBusConfig(
            logger = FlowBusLogger.None,
            errorHandler = FlowBusErrorHandler { context, _ -> errors += context }
        ))
        val receiver = Owner()
        val channel = FlowBusJava.channel("java.error", String::class.java)
        val calls = mutableListOf<String>()
        FlowBusJava.postSticky(channel, "replay")
        val subscription = FlowBusJava.subscribeSticky(
            receiver,
            channel,
            Runnable { calls += "subscribed" },
            FlowBusJava.EventListener {
                calls += it
                if (it == "replay") throw IllegalArgumentException("callback")
            }
        )
        try {
            receiver.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            FlowBusJava.postSticky(channel, "next")
            runCurrent()
            assertEquals(listOf("subscribed", "replay", "next"), calls)
            assertEquals(FlowBusErrorPhase.SubscriberCallback, errors.single().phase)
            assertTrue(errors.single().isSticky)
            assertEquals(channel.name, errors.single().eventName)
        } finally {
            receiver.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            subscription.cancel()
            FlowBusAndroid.resetForTests()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `subscription hook failure propagates without being treated as event callback error`() {
        val errors = mutableListOf<FlowBusErrorContext>()
        val expected = IllegalStateException("subscription hook")
        val thrown = assertThrows(IllegalStateException::class.java) {
            runTest {
                val main = StandardTestDispatcher(testScheduler)
                Dispatchers.setMain(main)
                FlowBusAndroid.resetForTests()
                FlowBusAndroid.configure(FlowBusConfig(
                    logger = FlowBusLogger.None,
                    errorHandler = FlowBusErrorHandler { context, _ -> errors += context }
                ))
                val receiver = Owner()
                val channel = FlowBusJava.channel("java.hook.failure", String::class.java)
                val subscription = FlowBusJava.subscribe(
                    receiver,
                    channel,
                    Runnable { throw expected },
                    FlowBusJava.EventListener { _: String -> }
                )
                try {
                    receiver.registry.currentState = Lifecycle.State.STARTED
                    runCurrent()
                    assertTrue(subscription.isCancelled)
                    assertEquals(0, FlowBusJava.inspectChannel(channel)?.normalSubscriptionCount)
                    assertTrue(errors.isEmpty())
                } finally {
                    receiver.registry.currentState = Lifecycle.State.DESTROYED
                    runCurrent()
                    subscription.cancel()
                    FlowBusAndroid.resetForTests()
                    Dispatchers.resetMain()
                }
            }
        }
        assertEquals(expected.message, thrown.message)
        assertTrue(errors.isEmpty())
    }

    @Test
    fun `cancel before lifecycle start never notifies and completion differs from cancellation request`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        FlowBusAndroid.resetForTests()
        val receiver = Owner()
        val channel = FlowBusJava.channel("java.cancel.before.start", String::class.java)
        var notified = false
        val subscription = FlowBusJava.subscribe(receiver, channel, Runnable { notified = true }, FlowBusJava.EventListener {})
        val cleanup = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val job = launch {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    cleanup.complete(Unit)
                    release.await()
                }
            }
        }
        val inFlight = FlowBusJava.Subscription(job)
        try {
            subscription.cancel()
            receiver.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertFalse(notified)
            assertTrue(subscription.isCompleted)
            inFlight.cancel()
            inFlight.cancel()
            runCurrent()
            assertTrue(cleanup.isCompleted)
            assertTrue(inFlight.isCancelled)
            assertFalse(inFlight.isCompleted)
            release.complete(Unit)
            job.join()
            assertTrue(inFlight.isCompleted)
        } finally {
            release.complete(Unit)
            job.cancelAndJoin()
            receiver.registry.currentState = Lifecycle.State.DESTROYED
            runCurrent()
            subscription.cancel()
            FlowBusAndroid.resetForTests()
            Dispatchers.resetMain()
        }
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
    }

    private class Source : ViewModelStoreOwner {
        override val viewModelStore = ViewModelStore()
    }
}
