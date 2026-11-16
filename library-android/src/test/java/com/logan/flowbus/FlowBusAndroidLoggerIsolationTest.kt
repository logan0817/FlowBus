package com.logan.flowbus

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.FlowBusLogger
import com.logan.flowbus.core.FlowBusPostOutcome
import com.logan.flowbus.core.FlowBusPostResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(Parameterized::class)
class FlowBusAndroidLoggerIsolationTest(private val loggerCancels: Boolean) {
    private val channel = eventChannel<Int>("android.logger.isolation")

    @Test
    fun `FlowEventBus retains rejection Boolean and result when warning logger fails`() = withThrowingLogger {
            _, bus, warnings, _ ->
        assertFalse(bus.post(channel.name, 1, Int::class))
        assertRejected(bus.postResult(channel.name, 2, Int::class))
        assertEquals(2, warnings.size)
    }

    @Test
    fun `Kotlin owner channel and Java wrappers retain rejected send results`() = withThrowingLogger {
            owner, _, warnings, _ ->
        assertFalse(tryPostEventTo(owner, 1, eventName = channel.name))
        assertRejected(tryPostEventResultTo(owner, 2, eventName = channel.name))
        assertFalse(channel.tryPostTo(owner, 3))
        assertFalse(FlowBusJava.postTo(owner, channel, 4))
        assertRejected(FlowBusJava.postResultTo(owner, channel, 5))
        assertEquals(5, warnings.size)
    }

    @Test
    fun `delayed post schedules first and warning failure neither escapes nor generates a second warning`() =
        withThrowingLogger { _, bus, warnings, received ->
            assertTrue(bus.post(channel.name, 1, Int::class, delayMillis = 100))
            assertTrue(warnings.isEmpty())
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
            assertEquals(1, warnings.size)
            assertTrue(warnings.single().startsWith("Dropped event"))
            assertTrue(received.isEmpty())
        }

    @Test
    fun `owner cancellation still cancels a delayed send before its warning path`() =
        withThrowingLogger { owner, bus, warnings, received ->
            assertTrue(bus.post(channel.name, 1, Int::class, delayMillis = 100))
            runCurrent()
            owner.viewModelStore.clear()
            advanceTimeBy(100)
            runCurrent()
            assertTrue(warnings.isEmpty())
            assertTrue(received.isEmpty())
            // 收集生命周期仍活跃，零缓冲仍会拒绝；无 warning 来自发送任务已被 owner 取消。
            assertEquals(1, bus.inspectChannel(channel.asEventKey())?.normalSubscriptionCount)
        }

    @Test
    fun `warning isolation does not swallow synchronous send validation errors`() =
        withThrowingLogger { _, bus, warnings, _ ->
            assertThrows(IllegalArgumentException::class.java) {
                bus.post(channel.name, "wrong type", String::class)
            }
            assertThrows(IllegalArgumentException::class.java) {
                bus.post(channel.name, "wrong type", String::class, delayMillis = 100)
            }
            assertThrows(IllegalArgumentException::class.java) {
                bus.postResult(channel.name, "wrong type", String::class)
            }
            assertThrows(IllegalArgumentException::class.java) {
                bus.post(channel.name, 1, Int::class, delayMillis = -1)
            }
            runCurrent()
            advanceTimeBy(100)
            runCurrent()
            assertTrue(warnings.isEmpty())
        }

    private fun withThrowingLogger(
        block: suspend TestScope.(Owner, FlowEventBus, MutableList<String>, MutableList<Int>) -> Unit
    ) = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        FlowBusAndroid.resetForTests()
        val warnings = mutableListOf<String>()
        FlowBusAndroid.configure(FlowBusConfig(
            normalBufferCapacity = 0,
            overflowPolicy = BufferOverflow.SUSPEND,
            logger = object : FlowBusLogger {
                override fun warn(tag: String, message: String, throwable: Throwable?) {
                    warnings += message
                    if (loggerCancels) throw CancellationException("logger failed")
                    throw IllegalStateException("logger failed")
                }
            }
        ))
        val owner = Owner()
        owner.registry.currentState = Lifecycle.State.STARTED
        val bus = ViewModelProvider(owner)[FlowEventBus::class.java]
        val ready = CompletableDeferred<Unit>()
        val received = mutableListOf<Int>()
        val subscription = bus.subscribeEventSuspending(
            lifecycleOwner = owner,
            key = channel.asEventKey(),
            onSubscribed = { ready.complete(Unit) },
            dispatcher = main,
            onReceived = { received += it }
        )
        try {
            runCurrent()
            ready.await()
            assertEquals(1, bus.inspectChannel(channel.asEventKey())?.normalSubscriptionCount)
            block(owner, bus, warnings, received)
        } finally {
            owner.registry.currentState = Lifecycle.State.DESTROYED
            subscription.cancelAndJoin()
            owner.viewModelStore.clear()
            FlowBusAndroid.resetForTests()
            Dispatchers.resetMain()
        }
    }

    private fun assertRejected(result: FlowBusPostResult) {
        assertFalse(result.accepted)
        assertEquals(FlowBusPostOutcome.RejectedByBuffer, result.outcome)
        assertEquals(1, result.subscriptionCount)
        assertEquals(BufferOverflow.SUSPEND, result.overflowPolicy)
        assertEquals(channel.name, result.eventName)
    }

    private class Owner : LifecycleOwner, ViewModelStoreOwner {
        val registry = LifecycleRegistry.createUnsafe(this)
        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore = ViewModelStore()
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "loggerCancellation={0}")
        fun parameters(): List<Array<Boolean>> = listOf(arrayOf(false), arrayOf(true))
    }
}
