package com.logan.flowbus.consumer

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.logan.flowbus.collectEventFromSuspending
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin

/** 原版 1.3.0 编译的 Android 订阅通知及默认参数调用。 */
object Legacy130AndroidCalls {
    suspend fun verify() {
        val owner = Owner()
        owner.registry.currentState = Lifecycle.State.STARTED
        val bus = FlowBus()
        val key = eventKey<Int>("consumer.binary.130.kotlin")
        val ready = CompletableDeferred<Unit>()
        val received = CompletableDeferred<Int>()
        val job = owner.collectEventFromSuspending(
            from = bus, key = key, onSubscribed = { ready.complete(Unit) },
            onReceived = { received.complete(it) }
        )
        try {
            ready.await()
            check(bus.inspectChannel(key)?.normalSubscriptionCount == 1)
            check(bus.post(key, 130))
            check(received.await() == 130)
        } finally {
            job.cancelAndJoin()
            owner.registry.currentState = Lifecycle.State.DESTROYED
        }
        check(bus.inspectChannel(key)?.normalSubscriptionCount == 0)
    }

    private class Owner : LifecycleOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
    }
}
