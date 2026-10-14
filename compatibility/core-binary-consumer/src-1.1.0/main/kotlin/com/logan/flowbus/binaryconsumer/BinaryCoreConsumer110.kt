package com.logan.flowbus.binaryconsumer

import com.logan.flowbus.core.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

/** 仅以正式 1.1.0 编译；替换候选后不重新编译，实际执行新增挂起入口。 */
object BinaryCoreConsumer110 {
    suspend fun verify() = coroutineScope {
        Legacy110JavaCaller.verify()
        val bus = FlowBus()
        val shared = bus.scoped("binary110.shared")
        val owned = bus.openScope("binary110.owned")
        val channel = eventChannel<Int>("binary110.channel")
        val key = channel.asEventKey()
        val targets = listOf(
            Target({ bus.collectSuspending(key, onReceived = it) }, { bus.emit(key, it) }),
            Target({ shared.collectSuspending<Int>(eventName = key.name, onReceived = it) }, { shared.emit(key, it) }),
            Target({ owned.collectSuspending(key, onReceived = it) }, { owned.emit(key, it) }),
            Target({ channel.collectSuspendingOn(bus, onReceived = it) }, { channel.emitOn(bus, it) }),
            Target({ channel.collectSuspendingOn(shared, onReceived = it) }, { channel.emitOn(shared, it) }),
            Target({ channel.collectSuspendingOn(owned, onReceived = it) }, { channel.emitOn(owned, it) }),
            Target({ bus.collectStickySuspending(key, onReceived = it) }, { bus.emitSticky(key, it) })
        )
        try {
            targets.forEachIndexed { index, target ->
                val received = CompletableDeferred<Int>()
                val collecting = launch(start = CoroutineStart.UNDISPATCHED) {
                    target.collect { yield(); received.complete(it) }
                }
                try {
                    target.emit(index)
                    check(received.await() == index)
                } finally { collecting.cancelAndJoin() }
            }
        } finally {
            owned.closeSuspending()
            shared.removeScope()
        }
    }

    private class Target(val collect: suspend (suspend (Int) -> Unit) -> Unit, val emit: suspend (Int) -> Unit)
}
