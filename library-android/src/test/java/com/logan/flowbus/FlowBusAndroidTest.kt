package com.logan.flowbus

import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelProvider
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.FlowBusErrorHandler
import com.logan.flowbus.core.FlowBusLogger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.util.Collections

class FlowBusAndroidTest {

    @After
    fun tearDown() {
        FlowBusAndroid.resetForTests()
    }

    @Test
    fun `android built-in config rethrows subscriber exceptions by default`() {
        val config = flowBusConfigOf(FlowEventBus())

        assertTrue(config.errorHandler === FlowBusErrorHandler.Rethrow)
    }

    @Test
    fun `android built-in logger does not send raw throwables to platform log`() {
        val source = File("src/main/java/com/logan/flowbus/FlowBusAndroid.kt").readText()

        assertFalse(source.contains("Log.w(tag, message, throwable)"))
        assertFalse(source.contains("android.util.Log.w(tag, message, throwable)"))
        assertTrue(source.contains("throwable::class.java.name"))
    }

    @Test
    fun `global FlowEventBus store does not use lifecycle ViewModelProvider`() {
        val source = File("src/main/java/com/logan/flowbus/GlobalViewModelStore.kt").readText()

        assertFalse(source.contains("ViewModelProvider(this, ViewModelProvider.NewInstanceFactory())[FlowEventBus::class.java]"))
    }

    @Test
    fun `global ViewModel store keeps generic ViewModel compatibility`() {
        val viewModel = GlobalViewModelStore.get(TestViewModel::class.java)

        assertEquals(TestViewModel::class.java, viewModel.javaClass)
    }

    @Test
    fun `global store owner resolves the same FlowEventBus used by global APIs`() = runBlocking {
        val eventName = "android.global.owner.shared.bus"
        val flow = eventFlowFrom<String>(
            owner = GlobalViewModelStore,
            eventName = eventName
        ) as MutableSharedFlow<String>
        val received = CompletableDeferred<String>()
        val collectorJob = launch {
            flow.collect { value ->
                received.complete(value)
            }
        }

        try {
            flow.subscriptionCount.first { it > 0 }

            postEvent(event = "global", eventName = eventName)

            assertEquals("global", withTimeout(1_000) { received.await() })
        } finally {
            collectorJob.cancelAndJoin()
        }
    }

    @Test
    fun `direct ViewModelProvider global owner resolves the same FlowEventBus used by global APIs`() = runBlocking {
        val eventName = "android.global.provider.shared.bus"
        val directBus = ViewModelProvider(GlobalViewModelStore)[FlowEventBus::class.java]
        val flow = directBus.eventFlow(eventName = eventName, valueType = String::class) as MutableSharedFlow<String>
        val received = CompletableDeferred<String>()
        val collectorJob = launch {
            flow.collect { value ->
                received.complete(value)
            }
        }

        try {
            flow.subscriptionCount.first { it > 0 }

            postEvent(event = "provider", eventName = eventName)

            assertEquals("provider", withTimeout(1_000) { received.await() })
        } finally {
            collectorJob.cancelAndJoin()
        }
    }

    @Test
    fun `global store owner resolves the same FlowEventBus used by global event channels`() = runBlocking {
        val channel = eventChannel<String>("android.global.owner.channel.shared.bus")
        val flow = channel.flowFrom(owner = GlobalViewModelStore) as MutableSharedFlow<String>
        val received = CompletableDeferred<String>()
        val collectorJob = launch {
            flow.collect { value ->
                received.complete(value)
            }
        }

        try {
            flow.subscriptionCount.first { it > 0 }

            assertTrue(channel.tryPost("channel"))

            assertEquals("channel", withTimeout(1_000) { received.await() })
        } finally {
            collectorJob.cancelAndJoin()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `reset cancels pending delayed posts from global bus`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Default)
        val eventName = "android.global.delayed.cancel.on.reset"
        val flow = eventFlow<String>(eventName = eventName) as MutableSharedFlow<String>
        val received = CompletableDeferred<String>()
        val collectorJob = launch {
            flow.collect { value ->
                received.complete(value)
            }
        }

        try {
            flow.subscriptionCount.first { it > 0 }
            assertTrue(tryPostEvent(event = "delayed", delayMillis = 100, eventName = eventName))

            FlowBusAndroid.resetForTests()
            delay(200)

            assertFalse(received.isCompleted)
        } finally {
            collectorJob.cancelAndJoin()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `androidx owner and lifecycle bound APIs declare main thread contract`() {
        val sourceRoot = File("src/main/java/com/logan/flowbus")
        val violations = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val lines = file.readLines()
                lines.asSequence().mapIndexedNotNull { index, line ->
                    val trimmedLine = line.trim()
                    val isCommentLine = trimmedLine.startsWith("*") || trimmedLine.startsWith("//")
                    val needsMainThread = !isCommentLine && (
                        line.contains("ViewModelProvider(owner") ||
                        line.contains("lifecycleScope.launch")
                        )
                    if (!needsMainThread) return@mapIndexedNotNull null

                    val functionLine = findEnclosingFunctionLine(lines, index)
                        ?: return@mapIndexedNotNull "${file.name}:${index + 1} has no enclosing function"
                    if (hasMainThreadAnnotation(lines, functionLine)) {
                        null
                    } else {
                        "${file.name}:${functionLine + 1} ${lines[functionLine].trim()}"
                    }
                }
            }
            .toList()

        assertEquals(emptyList<String>(), violations)
    }

    @Test
    fun `coroutine owner subscriptions resolve owner bus before launching`() {
        val source = File("src/main/java/com/logan/flowbus/SubscribeEventExtension.kt").readText()

        assertFalse(source.contains("= launch {\n    ViewModelProvider(owner)"))
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
    fun `configure is rejected after the global FlowEventBus has been created`() {
        eventFlow<String>(eventName = "android.configure.after.global.access")

        try {
            FlowBusAndroid.configure(FlowBusConfig(normalBufferCapacity = 3))
            fail("Expected configure to fail after the global FlowEventBus has been created.")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("before first global bus use"))
        }
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

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `lifecycle collection APIs reject initialized lifecycle state before launching`() {
        Dispatchers.setMain(Dispatchers.Default)
        val owner = TestLifecycleOwner()

        try {
            assertRejectsInitializedLifecycleState {
                owner.collectEvent(
                    flow = flowOf("value"),
                    minLifecycleState = Lifecycle.State.INITIALIZED
                ) {
                    fail("Expected collectEvent to reject INITIALIZED before collecting.")
                }
            }

            assertRejectsInitializedLifecycleState {
                FlowEventBus().subscribeEvent(
                    lifecycleOwner = owner,
                    eventName = "android.lifecycle.initialized.flow.event.bus",
                    valueType = String::class,
                    startState = Lifecycle.State.INITIALIZED,
                    dispatcher = Dispatchers.Main.immediate,
                    isSticky = false
                ) {
                    fail("Expected FlowEventBus.subscribeEvent to reject INITIALIZED before collecting.")
                }
            }
        } finally {
            Dispatchers.resetMain()
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
    fun `tryPostEvent returns false when an event is dropped`() = runBlocking {
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

        assertFalse(tryPostEventTo(owner = owner, event = 2))

        allowFirstToFinish.complete(Unit)
        firstEmitJob.cancelAndJoin()
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

    @Test
    fun `negative delayMillis is rejected for post and emit apis`() = runBlocking {
        val owner = TestOwner()

        try {
            tryPostEventTo(
                owner = owner,
                event = "value",
                delayMillis = -1,
                eventName = "android.negative.delay.post"
            )
            fail("Expected negative delayMillis to be rejected for tryPostEventTo.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("delayMillis"))
        }

        try {
            emitEventTo(
                owner = owner,
                event = "value",
                delayMillis = -1,
                eventName = "android.negative.delay.emit"
            )
            fail("Expected negative delayMillis to be rejected for emitEventTo.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("delayMillis"))
        } finally {
            owner.viewModelStore.clear()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `delayed tryPostEventTo only reports scheduling and is cancelled with owner`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Default)
        val owner = TestOwner()
        val flow = eventFlowFrom<String>(
            owner = owner,
            eventName = "android.delayed.post.owner.clear"
        ) as MutableSharedFlow<String>
        val received = CompletableDeferred<String>()
        val collectorJob = launch {
            flow.collect { value ->
                received.complete(value)
            }
        }

        try {
            flow.subscriptionCount.first { it > 0 }

            assertTrue(
                tryPostEventTo(
                    owner = owner,
                    event = "delayed",
                    delayMillis = 200,
                    eventName = "android.delayed.post.owner.clear"
                )
            )

            owner.viewModelStore.clear()
            delay(300)

            assertFalse(received.isCompleted)
        } finally {
            collectorJob.cancelAndJoin()
            owner.viewModelStore.clear()
            Dispatchers.resetMain()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `delayed tryPostEventTo rejects conflicting payload type before returning`() = runBlocking {
        Dispatchers.setMain(Dispatchers.Default)
        val owner = TestOwner()
        val eventName = "android.delayed.type.conflict"

        try {
            withContext(Dispatchers.Main.immediate) {
                eventFlowFrom<String>(owner = owner, eventName = eventName)

                try {
                    tryPostEventTo(
                        owner = owner,
                        event = 1,
                        delayMillis = Long.MAX_VALUE,
                        eventName = eventName
                    )
                    fail("Expected delayed tryPostEventTo to reject conflicting event type before returning.")
                } catch (expected: IllegalArgumentException) {
                    assertTrue(expected.message.orEmpty().contains(eventName))
                }
            }
        } finally {
            owner.viewModelStore.clear()
            Dispatchers.resetMain()
        }
    }

    private class TestOwner : ViewModelStoreOwner {
        override val viewModelStore: ViewModelStore = ViewModelStore()
    }

    private class TestLifecycleOwner : LifecycleOwner {
        private val registry = LifecycleRegistry(this)

        override val lifecycle: Lifecycle
            get() = registry
    }

    class TestViewModel : androidx.lifecycle.ViewModel()

    private fun flowBusConfigOf(flowEventBus: FlowEventBus): FlowBusConfig {
        val field = FlowEventBus::class.java.getDeclaredField("bus")
        field.isAccessible = true
        return (field.get(flowEventBus) as FlowBus).config
    }

    private fun assertRejectsInitializedLifecycleState(block: () -> Unit) {
        try {
            block()
            fail("Expected INITIALIZED lifecycle state to be rejected.")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message.orEmpty().contains("INITIALIZED"))
        }
    }

    private fun findEnclosingFunctionLine(lines: List<String>, startIndex: Int): Int? {
        for (index in startIndex downTo 0) {
            if (lines[index].contains("fun ")) {
                return index
            }
        }
        return null
    }

    private fun hasMainThreadAnnotation(lines: List<String>, functionLine: Int): Boolean {
        var index = functionLine - 1
        while (index >= 0) {
            val text = lines[index].trim()
            if (text.isEmpty()) {
                index -= 1
                continue
            }
            if (!text.startsWith("@")) {
                return false
            }
            if (text == "@MainThread") {
                return true
            }
            index -= 1
        }
        return false
    }
}
