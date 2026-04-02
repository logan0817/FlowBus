package com.logan.flowbus

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.FlowBusErrorHandler
import com.logan.flowbus.core.FlowBusLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Collections

class FlowBusAndroidTest {

    @After
    fun tearDown() {
        FlowBusAndroid.resetForTests()
    }

    @Test
    fun `configure applies custom config to new FlowEventBus`() {
        val config = FlowBusConfig(
            normalBufferCapacity = 8,
            stickyReplay = 2,
            stickyExtraBufferCapacity = 1,
            overflowPolicy = BufferOverflow.DROP_LATEST,
            logger = FlowBusLogger.None,
            errorHandler = FlowBusErrorHandler.Rethrow
        )

        FlowBusAndroid.configure(config)

        assertEquals(config, flowBusConfigOf(FlowEventBus()))
    }

    @Test
    fun `configure factory is evaluated for each new FlowEventBus`() {
        var sequence = 0
        FlowBusAndroid.configure {
            sequence += 1
            FlowBusConfig(normalBufferCapacity = sequence)
        }

        val first = flowBusConfigOf(FlowEventBus())
        val second = flowBusConfigOf(FlowEventBus())

        assertEquals(1, first.normalBufferCapacity)
        assertEquals(2, second.normalBufferCapacity)
    }

    @Test
    fun `flow event bus rejects conflicting payload types for same event name`() {
        val flowEventBus = FlowEventBus()
        flowEventBus.eventFlow(eventName = "demo", valueType = String::class)

        try {
            flowEventBus.eventFlow(eventName = "demo", valueType = Int::class)
            fail("Expected conflicting event types to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("demo"))
        }
    }

    @Test
    fun `emitEvent delivers without dropping when buffer is suspended`() = runBlocking {
        FlowBusAndroid.configure(
            FlowBusConfig(
                normalBufferCapacity = 0,
                overflowPolicy = BufferOverflow.SUSPEND,
                logger = FlowBusLogger.None,
                errorHandler = FlowBusErrorHandler.Rethrow
            )
        )

        val owner = TestOwner()
        val flow = eventFlowFrom<Int>(owner = owner) as MutableSharedFlow<Int>
        val received = Collections.synchronizedList(mutableListOf<Int>())
        val firstHandled = CompletableDeferred<Unit>()
        val allowFirstToFinish = CompletableDeferred<Unit>()
        val secondEmitFinished = CompletableDeferred<Unit>()

        val collectorJob = launch {
            flow.collect { value ->
                received += value
                if (value == 1 && !firstHandled.isCompleted) {
                    firstHandled.complete(Unit)
                    allowFirstToFinish.await()
                }
            }
        }

        flow.subscriptionCount.first { it > 0 }
        val firstEmitJob = launch { emitEventTo(owner = owner, event = 1) }
        firstHandled.await()

        val secondEmitJob = launch {
            emitEventTo(owner = owner, event = 2)
            secondEmitFinished.complete(Unit)
        }

        delay(100)
        assertFalse(secondEmitFinished.isCompleted)

        allowFirstToFinish.complete(Unit)

        withTimeout(1_000) {
            secondEmitFinished.await()
        }

        assertEquals(listOf(1, 2), received)
        firstEmitJob.cancelAndJoin()
        secondEmitJob.cancelAndJoin()
        collectorJob.cancelAndJoin()
        owner.viewModelStore.clear()
    }

    @Test
    fun `postEvent logs when an event is dropped`() = runBlocking {
        val warnings = Collections.synchronizedList(mutableListOf<String>())
        val logger = object : FlowBusLogger {
            override fun warn(tag: String, message: String, throwable: Throwable?) {
                warnings += message
            }
        }

        FlowBusAndroid.configure(
            FlowBusConfig(
                normalBufferCapacity = 0,
                overflowPolicy = BufferOverflow.SUSPEND,
                logger = logger,
                errorHandler = FlowBusErrorHandler.Rethrow
            )
        )

        val owner = TestOwner()
        val flow = eventFlowFrom<Int>(owner = owner) as MutableSharedFlow<Int>
        val firstHandled = CompletableDeferred<Unit>()
        val allowFirstToFinish = CompletableDeferred<Unit>()

        val collectorJob = launch {
            flow.collect { value ->
                if (value == 1 && !firstHandled.isCompleted) {
                    firstHandled.complete(Unit)
                    allowFirstToFinish.await()
                }
            }
        }

        flow.subscriptionCount.first { it > 0 }
        val firstEmitJob = launch { emitEventTo(owner = owner, event = 1) }
        firstHandled.await()

        postEventTo(owner = owner, event = 2)

        assertTrue(warnings.any { it.contains("Dropped event") })

        allowFirstToFinish.complete(Unit)
        firstEmitJob.cancelAndJoin()
        collectorJob.cancelAndJoin()
        owner.viewModelStore.clear()
    }

    private class TestOwner : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
    }

    private fun flowBusConfigOf(flowEventBus: FlowEventBus): FlowBusConfig {
        val field = FlowEventBus::class.java.getDeclaredField("bus")
        field.isAccessible = true
        return (field.get(flowEventBus) as FlowBus).config
    }
}