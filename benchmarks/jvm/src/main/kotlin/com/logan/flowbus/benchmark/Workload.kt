package com.logan.flowbus.benchmark

import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.FlowBusChannelConfig
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

/** 所有对照共用的负载；ID 断言属于测量工作量，各组同样执行。 */
class Workload(private val adapter: String, private val scenario: String) {
    data class Result(val sent: Long, val received: Long, val digest: Long)
    private val key = eventKey<Int>("benchmark.event")
    private val overflow = if (scenario in listOf("active1", "active8", "slowSuspend", "producers"))
        BufferOverflow.SUSPEND else BufferOverflow.DROP_OLDEST
    private val bus = FlowBus(FlowBusConfig(overflowPolicy = overflow))
    private val normal = MutableSharedFlow<Int>(extraBufferCapacity = 64, onBufferOverflow = overflow)
    private val sticky = MutableSharedFlow<Int>(replay = 1, extraBufferCapacity = 0, onBufferOverflow = overflow)
    private val stream: Flow<Int> = if (adapter == "shared") normal else bus.flow(key)
    init { if (adapter != "shared") bus.stickyFlow(key) }
    private fun post(value: Int) = if (adapter == "shared") normal.tryEmit(value) else bus.post(key, value)
    private suspend fun emit(value: Int) = if (adapter == "shared") normal.emit(value) else bus.emit(key, value)
    private fun postSticky(value: Int) = if (adapter == "shared") sticky.tryEmit(value) else bus.postSticky(key, value)
    private fun stickyStream(): Flow<Int> = if (adapter == "shared") sticky else bus.stickyFlow(key)

    fun run(): Result = when (scenario) {
        "noSubscribers" -> { repeat(128) { check(post(it)) }; Result(128, 0, 0) }
        "stickySend" -> { repeat(128) { check(postSticky(it)) }; Result(128, 0, 0) }
        "stickyReplay" -> runBlocking {
            check(postSticky(127)); val value = stickyStream().first(); check(value == 127)
            Result(1, 1, value.toLong())
        }
        "registration" -> {
            FlowBus().flow(key) // 单独记录构造、类型注册和首个流建立，未混入稳定发送。
            Result(0, 0, 1)
        }
        "configuredRegistration" -> {
            val fresh = FlowBus()
            fresh.configureChannel(key, FlowBusChannelConfig(normalBufferCapacity = 1))
            fresh.flow(key)
            Result(0, 0, 1)
        }
        "scope" -> runBlocking {
            val scope = bus.openScope("benchmark.scope")
            check(scope.postSticky(key, 7))
            check(scope.consumeStickyLatest(key) == 7)
            check(scope.closeSuspending().closed)
            check(bus.inspect().scopes.isEmpty())
            Result(1, 1, 7)
        }
        else -> runBlocking { withTimeout(10_000) { batch() } }
    }

    private suspend fun batch(): Result = coroutineScope {
        val count = if (scenario == "active8") 8 else 1
        val slow = scenario == "slowSuspend" || scenario == "slowDrop"
        val first = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val expected = if (scenario == "slowDrop") listOf(-1) + (64 until 128) else (0 until 128).toList()
        val received = List(count) { mutableListOf<Int>() }
        // 直接 SharedFlow 与 bus.flow 都在首次挂起前建立底层订阅。
        val collectors = received.map { values ->
            launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
                stream.take(expected.size).collect { value ->
                    values += value
                    if (slow && values.size == 1) {
                        first.complete(Unit)
                        release.await()
                    }
                }
            }
        }
        if (scenario == "slowDrop") {
            check(post(-1)); first.await()
            repeat(128) { check(post(it)) }
            release.complete(Unit)
        } else if (scenario == "slowSuspend") {
            emit(0); first.await()
            // 先塞满 64 个额外位置，第 66 次发送必须等待订阅者。
            for (id in 1..64) emit(id)
            val blocked = async(start = CoroutineStart.UNDISPATCHED) { emit(65) }
            check(!blocked.isCompleted)
            release.complete(Unit)
            blocked.await()
            for (id in 66 until 128) emit(id)
        } else if (scenario == "producers") {
            (0 until 4).map { producer -> launch(Dispatchers.Default) {
                repeat(32) { emit(producer * 32 + it) }
            } }.joinAll()
        } else {
            repeat(128) { emit(it) }
        }
        collectors.joinAll()
        received.forEach { values ->
            if (scenario == "producers") check(values.sorted() == expected) else check(values == expected)
        }
        Result(if (scenario == "slowDrop") 129 else 128, received.sumOf { it.size }.toLong(), received.sumOf { list -> list.sumOf { it.toLong() } })
    }

    companion object {
        @JvmStatic fun main(args: Array<String>) {
            val target = args.single()
            val adapter = if (target == "shared") "shared" else "bus"
            val scenarios = listOf("noSubscribers", "active1", "active8", "slowSuspend", "slowDrop", "stickySend", "stickyReplay", "producers") +
                (if (adapter == "bus") listOf("registration", "scope") else emptyList()) +
                (if (target == "candidate") listOf("configuredRegistration") else emptyList())
            scenarios.forEach { scenario ->
                val result = Workload(adapter, scenario).run()
                println("PASS target=$target scenario=$scenario sent=${result.sent} received=${result.received} digest=${result.digest}")
            }
        }
    }
}
