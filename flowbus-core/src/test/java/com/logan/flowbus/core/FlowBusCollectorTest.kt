package com.logan.flowbus.core

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Collections

class FlowBusCollectorTest {

    @Test
    fun `collectFlowBusSequentially preserves handler order`() = runBlocking {
        val flow = MutableSharedFlow<Any>(replay = 2)
        val received = Collections.synchronizedList(mutableListOf<Int>())
        val completed = CompletableDeferred<Unit>()

        val job = launch {
            collectFlowBusSequentially<Int>(
                flow = flow,
                dispatcher = Dispatchers.Default
            ) { value ->
                if (value == 1) {
                    Thread.sleep(100)
                }
                received += value
                if (received.size == 2 && !completed.isCompleted) {
                    completed.complete(Unit)
                }
            }
        }

        flow.emit(1)
        flow.emit(2)

        withTimeout(1_000) {
            completed.await()
        }

        assertEquals(listOf(1, 2), received)
        job.cancelAndJoin()
    }
}
