package com.logan.flowbus.binaryconsumer

import com.logan.flowbus.core.DefaultFlowBus
import com.logan.flowbus.core.FlowBusChannelSnapshot
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 正式 1.3.0 编译的订阅通知、默认参数和诊断调用；原版与候选运行同一份 class。 */
object BinaryCoreConsumer130 {
    suspend fun verify() = coroutineScope {
        Legacy130JavaCaller.verify()
        // 通用旧 API fixture 已安装默认总线，复用它验证 1.3.0 调用。
        val bus = DefaultFlowBus.raw()
        val shared = bus.scoped("binary.1.3.0.shared")
        val owned = bus.openScope("binary.1.3.0.owned")
        val key = eventKey<Int>("binary.1.3.0.subscription")
        val collectors: List<suspend (() -> Unit, suspend (Int) -> Unit) -> Unit> = listOf(
            { ready, receive -> bus.collectSuspending(key, ready, onReceived = receive) },
            { ready, receive -> bus.collectStickySuspending(key, ready, onReceived = receive) },
            { ready, receive -> shared.collectSuspending(key, ready, onReceived = receive) },
            { ready, receive -> shared.collectStickySuspending(key, ready, onReceived = receive) },
            { ready, receive -> owned.collectSuspending(key, ready, onReceived = receive) },
            { ready, receive -> owned.collectStickySuspending(key, ready, onReceived = receive) },
            { ready, receive -> DefaultFlowBus.raw().collectSuspending(key, ready, onReceived = receive) }
        )
        val senders: List<(Int) -> Boolean> = listOf(
            { bus.post(key, it) }, { bus.postSticky(key, it) },
            { shared.post(key, it) }, { shared.postSticky(key, it) },
            { owned.post(key, it) }, { owned.postSticky(key, it) },
            { DefaultFlowBus.post(key, it) }
        )
        val inspectors: List<() -> FlowBusChannelSnapshot?> = listOf(
            { bus.inspectChannel(key) }, { bus.inspectChannel(key) },
            { shared.inspectChannel(key) }, { shared.inspectChannel(key) },
            { owned.inspectChannel(key) }, { owned.inspectChannel(key) },
            { DefaultFlowBus.raw().inspectChannel(key) }
        )
        try {
            collectors.forEachIndexed { index, collect ->
                val ready = CompletableDeferred<Unit>()
                val received = CompletableDeferred<Int>()
                val collector = launch(start = CoroutineStart.UNDISPATCHED) {
                    collect({ ready.complete(Unit) }) { received.complete(it) }
                }
                try {
                    ready.await()
                    val snapshot = checkNotNull(inspectors[index]())
                    check(snapshot.normalSubscriptionCount + snapshot.stickySubscriptionCount == 1)
                    check(senders[index](index))
                    check(received.await() == index)
                    check(snapshot.copy() == snapshot)
                } finally {
                    collector.cancelAndJoin()
                }
                val stopped = checkNotNull(inspectors[index]())
                check(stopped.normalSubscriptionCount + stopped.stickySubscriptionCount == 0)
            }
        } finally {
            owned.close()
            shared.removeScope()
        }
    }
}
