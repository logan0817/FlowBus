package com.logan.flowbus.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.collect
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

internal class FlowBusDiagnosticRuntime {
    @Volatile private var observers: CopyOnWriteArrayList<Registration>? = null
    @Volatile private var assignedBusId = 0L
    val isEnabled: Boolean get() = observers != null
    val busId: Long get() {
        if (assignedBusId != 0L) return assignedBusId
        return synchronized(this) {
            if (assignedBusId == 0L) assignedBusId = nextId()
            assignedBusId
        }
    }

    fun observe(observer: FlowBusDiagnosticObserver): FlowBusDiagnosticRegistration {
        return synchronized(this) {
            val current = observers ?: CopyOnWriteArrayList<Registration>().also { observers = it }
            Registration(observer, this).also { current.add(it) }
        }
    }

    inline fun publish(event: () -> FlowBusDiagnosticEvent) {
        if (isEnabled) dispatch(event())
    }

    fun dispatch(event: FlowBusDiagnosticEvent) {
        val current = observers ?: return
        if (dispatching.get() == true) return
        dispatching.set(true)
        try {
            for (registration in current) {
                if (!registration.closed.get()) {
                    try {
                        registration.observer?.onDiagnostic(event)
                    } catch (_: Exception) {
                        // 旁路观察器不能替代业务错误策略，也不能阻止其他观察器收到通知。
                    }
                }
            }
        } finally {
            dispatching.remove()
        }
    }

    private fun remove(registration: Registration) {
        synchronized(this) {
            val current = observers ?: return
            current.remove(registration)
            if (current.isEmpty()) observers = null
        }
    }

    private class Registration(
        @Volatile var observer: FlowBusDiagnosticObserver?,
        @Volatile private var runtime: FlowBusDiagnosticRuntime?
    ) : FlowBusDiagnosticRegistration {
        val closed = AtomicBoolean()
        override fun close() {
            if (closed.compareAndSet(false, true)) {
                observer = null
                runtime?.remove(this)
                runtime = null
            }
        }
    }

    companion object {
        private val ids = AtomicLong()
        private val dispatching = ThreadLocal<Boolean>()
        fun nextId(): Long = ids.incrementAndGet()
    }
}

internal class FlowBusStreamDiagnostics(
    val runtime: FlowBusDiagnosticRuntime,
    val channel: FlowBusDiagnosticChannel
) {
    fun published(result: FlowBusPostResult, mode: FlowBusDiagnosticPublishMode) {
        runtime.publish { FlowBusDiagnosticEvent.Published(channel, result, mode) }
    }

    fun emitFailed(error: Throwable) {
        runtime.publish {
            if (error is CancellationException) FlowBusDiagnosticEvent.EmitCancelled(channel)
            else FlowBusDiagnosticEvent.EmitFailed(channel, error.javaClass.name)
        }
    }
}

internal class FlowBusDiagnosticSession(private val stream: FlowBusStreamDiagnostics) {
    val subscriptionId = FlowBusDiagnosticRuntime.nextId()
    private var started = false

    fun start(onSubscribed: (() -> Unit)?) {
        started = true
        stream.runtime.publish { FlowBusDiagnosticEvent.SubscriptionStarted(stream.channel, subscriptionId) }
        try {
            onSubscribed?.invoke()
        } catch (error: Exception) {
            if (error !is CancellationException) failure(FlowBusDiagnosticFailurePhase.SubscriptionCallback, error)
            throw error
        }
    }

    fun failure(phase: FlowBusDiagnosticFailurePhase, error: Throwable) {
        stream.runtime.publish {
            FlowBusDiagnosticEvent.Failure(stream.channel, subscriptionId, phase, error.javaClass.name)
        }
    }

    fun end(error: Throwable?) {
        if (!started) return
        stream.runtime.publish {
            FlowBusDiagnosticEvent.SubscriptionEnded(
                stream.channel, subscriptionId,
                when (error) {
                    null -> FlowBusDiagnosticEndReason.Completed
                    is CancellationException -> FlowBusDiagnosticEndReason.Cancelled
                    else -> FlowBusDiagnosticEndReason.Failed
                },
                error?.takeUnless { it is CancellationException }?.javaClass?.name
            )
        }
    }
}

/**
 * 只在取得 Flow 时已安装观察器的路径使用。关闭态保持原 SharedFlow/粘性流路径，
 * 不让所有默认订阅永久多经过一层逐值转发。
 */
internal class FlowBusDiagnosticFlow<T>(
    private val stream: FlowBusStreamDiagnostics,
    private val onSubscribed: (() -> Unit)?,
    private val upstream: ((() -> Unit)?) -> Flow<T>
) : Flow<T> {
    @OptIn(InternalCoroutinesApi::class)
    override suspend fun collect(collector: FlowCollector<T>) {
        collectWithDiagnostics { value, _ -> collector.emit(value) }
    }

    suspend fun collectWithDiagnostics(onValue: suspend (T, FlowBusDiagnosticSession?) -> Unit) {
        if (!stream.runtime.isEnabled) {
            upstream(onSubscribed).collect { onValue(it, null) }
            return
        }
        val session = FlowBusDiagnosticSession(stream)
        var failure: Throwable? = null
        try {
            upstream { session.start(onSubscribed) }.collect { onValue(it, session) }
        } catch (error: Throwable) {
            failure = error
            throw error
        } finally {
            session.end(failure)
        }
    }
}

internal suspend fun <T> Flow<T>.collectWithFlowBusDiagnostics(
    onValue: suspend (T, FlowBusDiagnosticSession?) -> Unit
) {
    if (this is FlowBusDiagnosticFlow<T>) collectWithDiagnostics(onValue)
    else collect { onValue(it, null) }
}
