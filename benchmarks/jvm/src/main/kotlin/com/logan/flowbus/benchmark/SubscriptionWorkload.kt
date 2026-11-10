package com.logan.flowbus.benchmark

import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.collect

/** 相同的订阅建立、独立生产者 rendezvous 发送一条、收到 ID 和取消工作量。 */
class SubscriptionWorkload(private val scenario: String) {
    private val key = eventKey<Int>("benchmark.subscription")
    private val bus = FlowBus(FlowBusConfig(normalBufferCapacity = 0, overflowPolicy = BufferOverflow.SUSPEND))
    private val useHook = when (scenario) {
        "plain" -> false
        "hook" -> true
        else -> error("Unknown subscription scenario: $scenario")
    }

    init {
        bus.flow(key) // 只测已有通道上的每次订阅，不混入首次类型/Flow 注册。
    }

    fun run(): Workload.Result = runBlocking {
        withTimeout(10_000) { operation() }
    }

    private suspend fun operation(): Workload.Result = coroutineScope {
        val subscribed = CompletableDeferred<Unit>()
        val received = CompletableDeferred<Int>()
        var notifications = 0
        var receivedCount = 0L
        val collector = launch(start = CoroutineStart.UNDISPATCHED) {
            val stream = if (useHook) bus.flow(key) {
                notifications++
                check(subscribed.complete(Unit))
            } else bus.flow(key)
            stream.collect { value ->
                check(value == 17)
                receivedCount++
                check(received.complete(value))
            }
        }
        // plain 是未组合的普通 Flow，UNDISPATCHED 返回前已实际登记；两组都完成同一信号。
        if (!useHook) check(subscribed.complete(Unit))
        val producer = launch {
            subscribed.await()
            bus.emit(key, 17)
        }
        try {
            producer.join()
            val value = received.await()
            check(receivedCount == 1L)
            check(notifications == if (useHook) 1 else 0)
            check(producer.isCompleted && !producer.isCancelled)
            Workload.Result(1, receivedCount, value.toLong())
        } finally {
            producer.cancel()
            collector.cancelAndJoin()
            check(collector.isCompleted)
        }
    }

    companion object {
        /** 独立候选自检入口，不改变 Workload 原有 selfCheck 场景。 */
        @JvmStatic fun main(args: Array<String>) {
            val scenarios = args.toList().ifEmpty { listOf("plain", "hook") }
            scenarios.forEach { scenario ->
                val result = SubscriptionWorkload(scenario).run()
                println("PASS target=candidate scenario=$scenario sent=${result.sent} received=${result.received} digest=${result.digest}")
            }
        }
    }
}
