package com.logan.flowbus.benchmark

import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong

/** 候选内相同工作量比较关闭/开启诊断，开启组使用仅计数的观察器。 */
class DiagnosticsWorkload(private val scenario: String, private val enabled: Boolean) : AutoCloseable {
    private val bus = FlowBus(FlowBusConfig(
        overflowPolicy = if (scenario == "active1") BufferOverflow.SUSPEND else BufferOverflow.DROP_OLDEST
    ))
    private val key = eventKey<Int>("benchmark.diagnostics")
    private val observations = AtomicLong()
    private val registration = if (enabled) bus.observeDiagnostics { observations.incrementAndGet() } else null

    init {
        require(scenario in listOf("noSubscribers", "stickySend", "active1"))
        bus.flow(key)
        bus.stickyFlow(key)
    }

    fun run(): Workload.Result {
        val result = when (scenario) {
            "noSubscribers" -> {
                repeat(128) { check(bus.post(key, it)) }
                Workload.Result(128, 0, 0)
            }
            "stickySend" -> {
                repeat(128) { check(bus.postSticky(key, it)) }
                Workload.Result(128, 0, 0)
            }
            else -> runBlocking { withTimeout(10_000) { activeBatch() } }
        }
        val expected = if (enabled) if (scenario == "active1") 130L else 128L else 0L
        check(observations.getAndSet(0) == expected)
        return result
    }

    private suspend fun activeBatch(): Workload.Result = coroutineScope {
        var count = 0L
        var sum = 0L
        val complete = CompletableDeferred<Unit>()
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            bus.collectSuspending(key) {
                check(it == count.toInt())
                count++
                sum += it
                if (count == 128L) complete.complete(Unit)
            }
        }
        try {
            repeat(128) { bus.emit(key, it) }
            complete.await()
        } finally {
            collector.cancelAndJoin()
        }
        check(count == 128L && sum == 8128L)
        Workload.Result(128, count, sum)
    }

    override fun close() {
        registration?.close()
    }

    companion object {
        @JvmStatic fun main(args: Array<String>) {
            for (enabled in listOf(false, true)) {
                for (scenario in listOf("noSubscribers", "stickySend", "active1")) {
                    DiagnosticsWorkload(scenario, enabled).use {
                        val result = it.run()
                        println("PASS diagnostics=$enabled scenario=$scenario sent=${result.sent} received=${result.received}")
                    }
                }
            }
        }
    }
}
