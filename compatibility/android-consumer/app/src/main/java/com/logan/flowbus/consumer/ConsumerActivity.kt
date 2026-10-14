package com.logan.flowbus.consumer

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.collectEvent
import com.logan.flowbus.collectEventFromSuspending
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusChannelConfig
import com.logan.flowbus.configureEventChannel
import com.logan.flowbus.eventChannel
import com.logan.flowbus.flowFrom
import com.logan.flowbus.tryPostTo
import com.logan.flowbus.emitTo
import com.logan.flowbus.core.eventKey
import com.logan.flowbus.onEvent
import com.logan.flowbus.onEventSuspending
import com.logan.flowbus.postScopedEvent
import com.logan.flowbus.postStickyEvent
import com.logan.flowbus.postStickyEventTo
import com.logan.flowbus.scopedEventFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import java.util.UUID

/** 真实 Maven/R8 产物的设备自检入口；仅用于消费者验证，不进入库发布产物。 */
class ConsumerActivity : Activity() {
    private val verificationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runId = intent.getStringExtra("flowbus.consumer.runId") ?: UUID.randomUUID().toString()
        val status = TextView(this).apply { text = "FlowBus consumer: RUNNING" }
        setContentView(status)
        verificationScope.launch {
            val passed = mutableListOf<String>()
            try {
                withTimeout(10_000) {
                    verifyCore()
                    passed += "normal-sticky-scope"
                    verifyLegacyAndroid()
                    passed += "legacy-android"
                    verifyOwnerSuspending()
                    passed += "owner-suspending"
                    verifyExplicitLifecycle()
                    passed += "explicit-lifecycle"
                    verifyChannelConfiguration()
                    passed += "channel-config"
                    LegacyAndroidCalls.verify()
                    passed += "binary-1.1.0-android"
                }
                val result = "PASS API=${Build.VERSION.SDK_INT} checks=${passed.size} runId=$runId"
                Log.i(TAG, result)
                status.text = buildString {
                    appendLine(result)
                    passed.forEach { appendLine(it) }
                }
            } catch (failure: Throwable) {
                // Activity 销毁导致的正常取消不生成误导性失败；自检超时仍必须输出 FAIL。
                if (failure is CancellationException && !isActive) throw failure
                Log.e(TAG, "FAIL runId=$runId completed=${passed.joinToString()}", failure)
                status.text = "FAIL runId=$runId ${failure.javaClass.simpleName}: ${failure.message}"
            }
        }
    }

    override fun onDestroy() {
        verificationScope.cancel()
        super.onDestroy()
    }

    private suspend fun verifyCore() = coroutineScope {
        val bus = FlowBus()
        val key = eventKey<Int>("consumer.runtime.core")
        // 直接收集的 UNDISPATCHED 协程先建立订阅，不依靠任意延时判断发送时机。
        val normal = async(start = CoroutineStart.UNDISPATCHED) { bus.flow(key).first() }
        try {
            check(bus.post(key, 11))
            check(normal.await() == 11)
        } finally {
            normal.cancelAndJoin()
        }
        check(bus.postSticky(key, 12))
        check(bus.stickyFlow(key).first() == 12)
        check(bus.consumeStickyLatest(key) == 12)
        check(bus.consumeStickyLatest(key) == null)

        val source = bus.openScope("consumer.runtime.scope")
        try {
            check(source.postSticky(key, 13))
            check(source.stickyFlow(key).first() == 13)
            check(source.closeSuspending().closed)
            val reopened = bus.openScope(source.scopeName)
            try {
                check(reopened.consumeStickyLatest(key) == null)
                check(reopened.postSticky(key, 14))
                check(reopened.stickyFlow(key).first() == 14)
            } finally {
                reopened.close()
            }
        } finally {
            source.close()
        }
    }

    private suspend fun verifyLegacyAndroid() = coroutineScope {
        val owner = VerificationOwner()
        owner.registry.currentState = Lifecycle.State.STARTED
        val genericReceived = CompletableDeferred<String>()
        val generic = owner.collectEvent(flowOf("ready")) { genericReceived.complete(it) }
        val oldReceived = CompletableDeferred<Int>()
        val old = owner.onEvent<Int>(
            from = owner, isSticky = true, eventName = "consumer.runtime.legacy"
        ) { oldReceived.complete(it) }
        val globalReceived = CompletableDeferred<Int>()
        val global = owner.onEvent<Int>(
            isSticky = true, eventName = "consumer.runtime.legacy"
        ) { globalReceived.complete(it) }
        val scoped = async(start = CoroutineStart.UNDISPATCHED) {
            owner.scopedEventFlow<Int>(eventName = "consumer.runtime.scoped").first()
        }
        try {
            check(genericReceived.await() == "ready")
            postStickyEvent(99, eventName = "consumer.runtime.legacy")
            postStickyEventTo(owner, 21, eventName = "consumer.runtime.legacy")
            check(oldReceived.await() == 21)
            check(globalReceived.await() == 99)
            owner.postScopedEvent(22, eventName = "consumer.runtime.scoped")
            check(scoped.await() == 22)
        } finally {
            owner.destroy()
            generic.cancel()
            old.cancel()
            global.cancel()
            scoped.cancel()
            generic.join()
            old.join()
            global.join()
            scoped.join()
        }
    }

    private suspend fun verifyOwnerSuspending() {
        val owner = VerificationOwner()
        owner.registry.currentState = Lifecycle.State.STARTED
        val received = CompletableDeferred<Int>()
        val subscription = owner.onEventSuspending<Int>(
            from = owner, isSticky = true, eventName = "consumer.runtime.owner"
        ) {
            yield()
            received.complete(it)
        }
        try {
            postStickyEventTo(owner, 31, eventName = "consumer.runtime.owner")
            check(received.await() == 31)
        } finally {
            owner.destroy()
            subscription.cancelAndJoin()
        }
    }

    private suspend fun verifyExplicitLifecycle() {
        val owner = VerificationOwner()
        val bus = FlowBus()
        val source = bus.openScope("consumer.runtime.explicit")
        val other = FlowBus()
        val key = eventKey<Int>("consumer.runtime.lifecycle")
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val received = Channel<Int>(Channel.UNLIMITED)
        owner.registry.currentState = Lifecycle.State.CREATED
        check(source.postSticky(key, 41))
        check(other.postSticky(key, 99))
        val subscription = owner.collectEventFromSuspending(source, key, isSticky = true) {
            if (it == 41) {
                started.complete(Unit)
                try {
                    awaitCancellation()
                } finally {
                    cancelled.complete(Unit)
                }
            } else {
                received.send(it)
            }
        }
        try {
            owner.registry.currentState = Lifecycle.State.STARTED
            started.await()
            check(source.postSticky(key, 42))
            check(received.tryReceive().isFailure)
            owner.registry.currentState = Lifecycle.State.CREATED
            cancelled.await()
            check(source.postSticky(key, 43))
            check(other.postSticky(key, 100))
            owner.registry.currentState = Lifecycle.State.STARTED
            check(received.receive() == 43)
            owner.destroy()
            subscription.join()
            check(!source.isClosed)
            check(source.postSticky(key, 44))
        } finally {
            owner.destroy()
            // 先完成非挂起清理，避免已取消协程在 join 时跳过 scope/channel 的关闭。
            subscription.cancel()
            source.close()
            received.close()
            subscription.join()
        }
    }

    private suspend fun verifyChannelConfiguration() = coroutineScope {
        val owner = VerificationOwner()
        val channel = eventChannel<Int>("consumer.runtime.config")
        val config = FlowBusChannelConfig(
            normalBufferCapacity = 0,
            normalOverflowPolicy = BufferOverflow.SUSPEND
        )
        try {
            configureEventChannel(channel, config, owner)
            configureEventChannel(channel, config, owner)
            val received = async(start = CoroutineStart.UNDISPATCHED) { channel.flowFrom(owner).first() }
            try {
                check(!channel.tryPostTo(owner, 51))
                channel.emitTo(owner, 52)
                check(received.await() == 52)
            } finally {
                received.cancelAndJoin()
            }
            val independent = FlowBus()
            independent.configureChannel(channel.asEventKey(), config)
            check(independent.tryPostResult(channel.asEventKey(), 53).overflowPolicy == BufferOverflow.SUSPEND)
            check(independent.inspect().config.overflowPolicy == BufferOverflow.DROP_OLDEST)
        } finally {
            owner.destroy()
        }
    }

    private class VerificationOwner : LifecycleOwner, ViewModelStoreOwner {
        val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        override val viewModelStore = ViewModelStore()

        fun destroy() {
            registry.currentState = Lifecycle.State.DESTROYED
            viewModelStore.clear()
        }
    }

    private companion object {
        const val TAG = "FlowBusConsumer"
    }
}
