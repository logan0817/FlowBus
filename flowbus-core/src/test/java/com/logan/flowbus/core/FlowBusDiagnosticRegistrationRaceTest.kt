package com.logan.flowbus.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class FlowBusDiagnosticRegistrationRaceTest {
    @Test
    fun `observing again preserves existing identities and distinguishes streams rebuilt while disabled`() {
        for (sticky in listOf(false, true)) {
            val bus = FlowBus()
            val retained = eventKey<Int>("diagnostics.registration.retained")
            val rebuilt = eventKey<Int>("diagnostics.registration.rebuilt")
            val firstRecords = mutableListOf<FlowBusDiagnosticEvent.Published>()
            val first = bus.observeDiagnostics {
                if (it is FlowBusDiagnosticEvent.Published) firstRecords += it
            }
            var second: FlowBusDiagnosticRegistration? = null
            try {
                assertTrue(if (sticky) bus.postSticky(retained, 1) else bus.post(retained, 1))
                assertTrue(if (sticky) bus.postSticky(rebuilt, 1) else bus.post(rebuilt, 1))
                val retainedIdentity = firstRecords.single { it.channel.eventName == retained.name }.channel
                val previousIdentity = firstRecords.single { it.channel.eventName == rebuilt.name }.channel
                first.close()
                assertTrue(if (sticky) bus.postSticky(retained, 2) else bus.post(retained, 2))
                if (sticky) bus.removeSticky(rebuilt) else bus.removeEvent(rebuilt)
                assertTrue(if (sticky) bus.postSticky(rebuilt, 2) else bus.post(rebuilt, 2))
                assertEquals(2, firstRecords.size)

                val secondRecords = mutableListOf<FlowBusDiagnosticEvent.Published>()
                second = bus.observeDiagnostics {
                    if (it is FlowBusDiagnosticEvent.Published) secondRecords += it
                }
                assertTrue(if (sticky) bus.postSticky(retained, 3) else bus.post(retained, 3))
                assertTrue(if (sticky) bus.postSticky(rebuilt, 3) else bus.post(rebuilt, 3))
                assertEquals(2, secondRecords.size)
                assertEquals(retainedIdentity, secondRecords.single { it.channel.eventName == retained.name }.channel)
                val rebuiltIdentity = secondRecords.single { it.channel.eventName == rebuilt.name }.channel
                assertEquals(previousIdentity.busId, rebuiltIdentity.busId)
                assertEquals(previousIdentity.storeId, rebuiltIdentity.storeId)
                assertEquals(previousIdentity.eventName, rebuiltIdentity.eventName)
                assertEquals(previousIdentity.isSticky, rebuiltIdentity.isSticky)
                assertNotEquals(previousIdentity.flowId, rebuiltIdentity.flowId)
                first.close()
                assertEquals(2, firstRecords.size)
            } finally {
                first.close()
                second?.close()
            }
        }
    }

    @Test
    fun `concurrent first publications assign one identity per stream and one identity per store`() {
        val bus = FlowBus()
        val firstKey = eventKey<Int>("diagnostics.first.concurrent")
        val otherKey = eventKey<Int>("diagnostics.other.concurrent")
        val records = CopyOnWriteArrayList<FlowBusDiagnosticEvent.Published>()
        val registration = bus.observeDiagnostics {
            if (it is FlowBusDiagnosticEvent.Published) records += it
        }
        val workerCount = 6
        val gate = CyclicBarrier(workerCount + 1)
        val workers = Executors.newFixedThreadPool(workerCount)
        val tasks = (0 until workerCount).map { value ->
            workers.submit<Boolean> {
                gate.await(5, TimeUnit.SECONDS)
                if (value % 2 == 0) bus.post(firstKey, value) && bus.post(otherKey, value)
                else bus.post(otherKey, value) && bus.post(firstKey, value)
            }
        }
        try {
            gate.await(5, TimeUnit.SECONDS)
            tasks.forEach { assertTrue(it.get(5, TimeUnit.SECONDS)) }
            assertEquals(workerCount * 2, records.size)
            val first = records.filter { it.channel.eventName == firstKey.name }
            val other = records.filter { it.channel.eventName == otherKey.name }
            assertEquals(workerCount, first.size)
            assertEquals(workerCount, other.size)
            assertEquals(1, first.map { it.channel }.distinct().size)
            assertEquals(1, other.map { it.channel }.distinct().size)
            assertEquals(first.singleIdentity().busId, other.singleIdentity().busId)
            assertEquals(first.singleIdentity().storeId, other.singleIdentity().storeId)
            assertNotEquals(first.singleIdentity().flowId, other.singleIdentity().flowId)
            assertTrue(records.all {
                it.channel.busId > 0 && it.channel.storeId > 0 && it.channel.flowId > 0 &&
                    it.result.accepted && it.channel.scopeName == null && !it.channel.isSticky
            })
        } finally {
            gate.reset()
            tasks.forEach { it.cancel(true) }
            registration.close()
            workers.shutdownNow()
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
        }
    }

    @Test
    fun `last close racing with a new registration cannot remove the new observer`() {
        val workers = Executors.newFixedThreadPool(2)
        val registrations = CopyOnWriteArrayList<FlowBusDiagnosticRegistration>()
        try {
            repeat(32) { iteration ->
                val bus = FlowBus()
                val key = eventKey<Int>("diagnostics.registration.race")
                val oldCalls = AtomicInteger()
                val newCalls = AtomicInteger()
                val old = bus.observeDiagnostics { oldCalls.incrementAndGet() }
                registrations += old
                val gate = CyclicBarrier(3)
                val closing = workers.submit {
                    gate.await(5, TimeUnit.SECONDS)
                    old.close()
                }
                val installing = workers.submit<FlowBusDiagnosticRegistration> {
                    gate.await(5, TimeUnit.SECONDS)
                    bus.observeDiagnostics { newCalls.incrementAndGet() }.also { registrations += it }
                }
                try {
                    gate.await(5, TimeUnit.SECONDS)
                    closing.get(5, TimeUnit.SECONDS)
                    val current = installing.get(5, TimeUnit.SECONDS)
                    try {
                        assertTrue(bus.post(key, iteration))
                        assertEquals("Closed observer was called in iteration $iteration", 0, oldCalls.get())
                        assertEquals("New observer was removed in iteration $iteration", 1, newCalls.get())
                        old.close()
                        assertTrue(bus.post(key, iteration))
                        assertEquals(0, oldCalls.get())
                        assertEquals(2, newCalls.get())
                    } finally {
                        current.close()
                    }
                } finally {
                    gate.reset()
                    closing.cancel(true)
                    installing.cancel(true)
                    old.close()
                }
            }
        } finally {
            workers.shutdownNow()
            try {
                assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS))
            } finally {
                registrations.forEach { it.close() }
            }
        }
    }

    private fun List<FlowBusDiagnosticEvent.Published>.singleIdentity(): FlowBusDiagnosticChannel =
        map { it.channel }.distinct().single()
}
