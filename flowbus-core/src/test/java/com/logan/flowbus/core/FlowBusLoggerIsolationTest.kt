package com.logan.flowbus.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.*
import org.junit.Test

class FlowBusLoggerIsolationTest {
    @Test
    fun `logger failure cannot bypass handler or prevent later values`() = runTest {
        for (suspending in listOf(false, true)) {
            for (loggerFailure in listOf(IllegalStateException("logger failed"), CancellationException("logger cancelled"))) {
                val expected = IllegalArgumentException("business failure")
                val failures = mutableListOf<Throwable>()
                val phases = mutableListOf<FlowBusErrorPhase>()
                val received = mutableListOf<Int>()
                var logCalls = 0
                collect(
                    suspending = suspending,
                    flow = flowOf(1, 2),
                    key = eventKey<Int>("logger.handled"),
                    logger = object : FlowBusLogger {
                        override fun warn(tag: String, message: String, throwable: Throwable?) {
                            logCalls++
                            assertSame(expected, throwable)
                            throw loggerFailure
                        }
                    },
                    handler = FlowBusErrorHandler { context, error ->
                        phases += context.phase
                        failures += error
                    }
                ) {
                    if (it == 1) throw expected
                    received += it
                }
                assertEquals(listOf(2), received)
                assertEquals(listOf(expected), failures)
                assertEquals(listOf(FlowBusErrorPhase.SubscriberCallback), phases)
                assertEquals(1, logCalls)
            }
        }
    }

    @Test
    fun `rethrow preserves original callback error when logger throws`() = runTest {
        for (suspending in listOf(false, true)) {
            val expected = IllegalArgumentException("original callback failure")
            val actual = runCatching {
                collect(
                    suspending,
                    flowOf(1),
                    eventKey<Int>("logger.rethrow"),
                    throwingLogger(),
                    FlowBusErrorHandler.Rethrow
                ) { throw expected }
            }.exceptionOrNull()
            assertSame(expected, actual)
        }
    }

    @Test
    fun `raw value mismatch still reaches handler when logger throws`() = runTest {
        for (suspending in listOf(false, true)) {
            val errors = mutableListOf<Throwable>()
            val contexts = mutableListOf<FlowBusErrorContext>()
            val received = mutableListOf<String>()
            collect(
                suspending,
                flowOf(123, "ok"),
                eventKey<String>("logger.raw.type"),
                throwingLogger(),
                FlowBusErrorHandler { context, error ->
                    contexts += context
                    errors += error
                }
            ) { received += it }
            assertEquals(listOf("ok"), received)
            assertTrue(errors.single() is ClassCastException)
            assertEquals(FlowBusErrorPhase.ValueCast, contexts.single().phase)
            assertEquals(String::class, contexts.single().expectedValueType)
            assertEquals(Int::class, contexts.single().actualValueType)
        }
    }

    @Test
    fun `raw mismatch keeps its original type error under rethrow and throwing logger`() = runTest {
        for (suspending in listOf(false, true)) {
            val actual = runCatching {
                collect(
                    suspending,
                    flowOf(123),
                    eventKey<String>("logger.raw.rethrow"),
                    throwingLogger(),
                    FlowBusErrorHandler.Rethrow
                ) { fail("An invalid value must not reach the subscriber") }
            }.exceptionOrNull()
            assertTrue(actual is ClassCastException)
            assertTrue(actual?.message.orEmpty().contains("logger.raw.rethrow"))
        }
    }

    @Test
    fun `callback cancellation reaches neither logger nor error handler`() = runTest {
        for (suspending in listOf(false, true)) {
            val expected = CancellationException("callback cancelled")
            var logCalls = 0
            var handlerCalls = 0
            val actual = runCatching {
                collect(
                    suspending,
                    flowOf(1),
                    eventKey<Int>("logger.cancel"),
                    object : FlowBusLogger {
                        override fun warn(tag: String, message: String, throwable: Throwable?) {
                            logCalls++
                            throw IllegalStateException("logger must not run")
                        }
                    },
                    FlowBusErrorHandler { _, _ -> handlerCalls++ }
                ) { throw expected }
            }.exceptionOrNull()
            assertSame(expected, actual)
            assertEquals(0, logCalls)
            assertEquals(0, handlerCalls)
        }
    }

    private fun throwingLogger() = object : FlowBusLogger {
        override fun warn(tag: String, message: String, throwable: Throwable?) {
            throw IllegalStateException("logger failed")
        }
    }

    private suspend fun <T : Any> collect(
        suspending: Boolean,
        flow: Flow<Any>,
        key: EventKey<T>,
        logger: FlowBusLogger,
        handler: FlowBusErrorHandler,
        receive: (T) -> Unit
    ) {
        if (suspending) {
            collectFlowBusSuspendingSequentially<T>(
                flow, eventKey = key, logger = logger, errorHandler = handler
            ) {
                yield()
                receive(it)
            }
        } else {
            collectFlowBusSequentially<T>(
                flow, eventKey = key, logger = logger, errorHandler = handler, onReceived = receive
            )
        }
    }
}
