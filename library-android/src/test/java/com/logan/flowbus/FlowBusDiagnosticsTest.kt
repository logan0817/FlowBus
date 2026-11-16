package com.logan.flowbus

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.FlowBusDiagnosticEndReason
import com.logan.flowbus.core.FlowBusDiagnosticEvent
import com.logan.flowbus.core.FlowBusDiagnosticFailurePhase
import com.logan.flowbus.core.FlowBusErrorHandler
import com.logan.flowbus.core.FlowBusLogger
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
class FlowBusDiagnosticsTest {
    @Test
    fun `Java caller observes isolated existing owner and global sources and closes idempotently`() {
        FlowBusAndroid.resetForTests()
        FlowBusAndroid.configure(FlowBusConfig(logger = FlowBusLogger.None))
        val source = Source()
        try {
            // 先创建来源，再安装观察器，不要求重新配置 Android 工厂。
            FlowBusJava.inspectChannelFrom(source, eventChannel<String>("unbound"))
            FlowBusJavaDiagnosticsCaller.verify(source)
        } finally {
            source.viewModelStore.clear()
            FlowBusAndroid.resetForTests()
        }
    }

    @Test
    fun `legacy lifecycle callback diagnostics reflect real registration and closing preserves business subscription`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        FlowBusAndroid.resetForTests()
        FlowBusAndroid.configure(FlowBusConfig(
            logger = FlowBusLogger.None,
            errorHandler = FlowBusErrorHandler.Ignore
        ))
        val receiver = Owner()
        val source = Source()
        val channel = eventChannel<Int>("android.diagnostics.lifecycle")
        val events = mutableListOf<FlowBusDiagnosticEvent>()
        val registration = observeEventDiagnostics({ events += it }, source)
        val received = mutableListOf<Int>()
        val job = receiver.onEvent(source, channel, dispatcher = main) {
            received += it
            if (it == 1) throw IllegalArgumentException("not diagnostic data")
        }
        try {
            receiver.registry.currentState = Lifecycle.State.CREATED
            runCurrent()
            assertTrue(events.isEmpty())
            receiver.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            val first = events.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionStarted>().single()
            channel.postTo(source, 1)
            runCurrent()
            val failure = events.filterIsInstance<FlowBusDiagnosticEvent.Failure>().single()
            assertEquals(FlowBusDiagnosticFailurePhase.SubscriberCallback, failure.phase)
            assertEquals(first.subscriptionId, failure.subscriptionId)
            assertEquals(first.channel, failure.channel)
            assertEquals(IllegalArgumentException::class.java.name, failure.exceptionType)
            receiver.registry.currentState = Lifecycle.State.CREATED
            runCurrent()
            val ended = events.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionEnded>().single()
            assertEquals(first.subscriptionId, ended.subscriptionId)
            assertEquals(FlowBusDiagnosticEndReason.Cancelled, ended.reason)
            assertFalse(job.isCompleted)
            receiver.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(2, events.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionStarted>().size)
            registration.close()
            val countAfterClose = events.size
            channel.postTo(source, 2)
            runCurrent()
            assertEquals(listOf(1, 2), received)
            assertEquals(countAfterClose, events.size)
        } finally {
            receiver.registry.currentState = Lifecycle.State.DESTROYED
            job.cancelAndJoin()
            registration.close()
            source.viewModelStore.clear()
            FlowBusAndroid.resetForTests()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `coroutine and lifecycle suspending legacy entry points report callback failures`() = runTest {
        val main = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(main)
        FlowBusAndroid.resetForTests()
        FlowBusAndroid.configure(FlowBusConfig(
            logger = FlowBusLogger.None,
            errorHandler = FlowBusErrorHandler.Ignore
        ))
        val receiver = Owner()
        val source = Source()
        val events = mutableListOf<FlowBusDiagnosticEvent>()
        val registration = observeEventDiagnostics({ events += it }, source)
        val normal = eventChannel<Int>("android.diagnostics.coroutine")
        val suspending = eventChannel<Int>("android.diagnostics.coroutine.suspending")
        val lifecycle = eventChannel<Int>("android.diagnostics.lifecycle.suspending")
        val jobs = listOf(
            onEvent(source, normal) { throw IllegalStateException("callback") },
            onEventSuspending(source, suspending) { throw IllegalStateException("callback") },
            receiver.onEventSuspending(source, lifecycle, dispatcher = main) { throw IllegalStateException("callback") }
        )
        try {
            receiver.registry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(3, events.filterIsInstance<FlowBusDiagnosticEvent.SubscriptionStarted>().size)
            listOf(normal, suspending, lifecycle).forEach { it.postTo(source, 1) }
            runCurrent()
            val failures = events.filterIsInstance<FlowBusDiagnosticEvent.Failure>()
            assertEquals(setOf(normal.name, suspending.name, lifecycle.name), failures.map { it.channel.eventName }.toSet())
            assertEquals(3, failures.size)
            assertTrue(failures.all { it.phase == FlowBusDiagnosticFailurePhase.SubscriberCallback })
        } finally {
            receiver.registry.currentState = Lifecycle.State.DESTROYED
            jobs.forEach { it.cancelAndJoin() }
            registration.close()
            source.viewModelStore.clear()
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
