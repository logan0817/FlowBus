package com.logan.flowbus.composeconsumer

import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusDiagnosticEvent

/** 验收期间收集有限操作产生的元数据，允许 callback 在工作线程结束。 */
internal class ConsumerDiagnostics(source: FlowBus) : AutoCloseable {
    private val events = mutableListOf<FlowBusDiagnosticEvent>()
    private val registration = source.observeDiagnostics { event ->
        synchronized(events) { events.add(event) }
    }

    private inline fun <reified T : FlowBusDiagnosticEvent> select(): List<T> =
        synchronized(events) { events.filterIsInstance<T>() }

    fun started(): List<FlowBusDiagnosticEvent.SubscriptionStarted> = select()
    fun ended(): List<FlowBusDiagnosticEvent.SubscriptionEnded> = select()
    fun failures(): List<FlowBusDiagnosticEvent.Failure> = select()

    override fun close() = registration.close()
}
