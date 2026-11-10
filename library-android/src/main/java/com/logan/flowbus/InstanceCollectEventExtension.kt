package com.logan.flowbus

import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.logan.flowbus.core.EventKey
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusScope
import com.logan.flowbus.core.ScopedFlowBus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 按生命周期收集显式 [from] 实例的事件，使用该实例的日志和错误策略。
 *
 * 注册不表示订阅立即就绪；普通事件在非活跃阶段不留存。该订阅不会关闭传入实例。
 * 需要挂起处理时使用 [collectEventFromSuspending]，需要 Flow 操作符时使用 [collectEvent]。
 */
@MainThread
fun <T : Any> LifecycleOwner.collectEventFrom(
    from: FlowBus,
    key: EventKey<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onReceived: (T) -> Unit
): Job = launchFlowBusCollection(minLifecycleState) {
    if (isSticky) from.collectSticky(key, dispatcher, onReceived)
    else from.collect(key, dispatcher, onReceived)
}

/** 按生命周期收集显式命名 scope 的事件；同名 store 重建后在下一次活跃阶段取新流。 */
@MainThread
fun <T : Any> LifecycleOwner.collectEventFrom(
    from: ScopedFlowBus,
    key: EventKey<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onReceived: (T) -> Unit
): Job = launchFlowBusCollection(minLifecycleState) {
    if (isSticky) from.collectSticky(key, dispatcher, onReceived)
    else from.collect(key, dispatcher, onReceived)
}

/**
 * 按生命周期收集显式 scope 句柄；停止订阅不关闭句柄。
 * 已关闭句柄不会自动切换到同名新句柄，再次收集会按 scope 契约抛出异常。
 */
@MainThread
fun <T : Any> LifecycleOwner.collectEventFrom(
    from: FlowBusScope,
    key: EventKey<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onReceived: (T) -> Unit
): Job = launchFlowBusCollection(minLifecycleState) {
    if (isSticky) from.collectSticky(key, dispatcher, onReceived)
    else from.collect(key, dispatcher, onReceived)
}

/**
 * 按生命周期顺序执行显式实例的挂起回调，不并发处理同一订阅中的事件。
 * 离开活跃阶段会请求取消当前处理；阻塞或 NonCancellable 工作仍需自行结束。CancellationException 不交给总线错误策略。
 */
@MainThread
fun <T : Any> LifecycleOwner.collectEventFromSuspending(
    from: FlowBus,
    key: EventKey<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onReceived: suspend (T) -> Unit
): Job = launchFlowBusCollection(minLifecycleState) {
    if (isSticky) from.collectStickySuspending(key, dispatcher, onReceived)
    else from.collectSuspending(key, dispatcher, onReceived)
}

/** 按生命周期顺序执行命名 scope 的挂起回调，沿用该实例的错误策略。 */
@MainThread
fun <T : Any> LifecycleOwner.collectEventFromSuspending(
    from: ScopedFlowBus,
    key: EventKey<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onReceived: suspend (T) -> Unit
): Job = launchFlowBusCollection(minLifecycleState) {
    if (isSticky) from.collectStickySuspending(key, dispatcher, onReceived)
    else from.collectSuspending(key, dispatcher, onReceived)
}

/** 按生命周期顺序执行 scope 句柄的挂起回调；订阅结束不会关闭传入句柄。 */
@MainThread
fun <T : Any> LifecycleOwner.collectEventFromSuspending(
    from: FlowBusScope,
    key: EventKey<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onReceived: suspend (T) -> Unit
): Job = launchFlowBusCollection(minLifecycleState) {
    if (isSticky) from.collectStickySuspending(key, dispatcher, onReceived)
    else from.collectSuspending(key, dispatcher, onReceived)
}

/**
 * 当前活跃阶段的底层订阅建立后调用 [onSubscribed]，随后按顺序执行挂起回调。
 * 每次重新进入活跃阶段都会重新通知；通知不代表业务处理完成，也不能保证后续不会取消。
 * [onSubscribed] 的异常直接终止本次订阅，不交给事件回调错误策略。
 */
@MainThread
fun <T : Any> LifecycleOwner.collectEventFromSuspending(
    from: FlowBus,
    key: EventKey<T>,
    onSubscribed: () -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onReceived: suspend (T) -> Unit
): Job = launchFlowBusCollection(minLifecycleState) {
    if (isSticky) from.collectStickySuspending(key, onSubscribed, dispatcher, onReceived)
    else from.collectSuspending(key, onSubscribed, dispatcher, onReceived)
}

/** 按当前命名 scope 的实际订阅通知；非活跃期间 store 重建后，下次活跃会从新 store 订阅。 */
@MainThread
fun <T : Any> LifecycleOwner.collectEventFromSuspending(
    from: ScopedFlowBus,
    key: EventKey<T>,
    onSubscribed: () -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onReceived: suspend (T) -> Unit
): Job = launchFlowBusCollection(minLifecycleState) {
    if (isSticky) from.collectStickySuspending(key, onSubscribed, dispatcher, onReceived)
    else from.collectSuspending(key, onSubscribed, dispatcher, onReceived)
}

/** 按固定 scope 句柄的实际订阅通知；取消订阅不会关闭来源，已关闭句柄不会切换到同名新来源。 */
@MainThread
fun <T : Any> LifecycleOwner.collectEventFromSuspending(
    from: FlowBusScope,
    key: EventKey<T>,
    onSubscribed: () -> Unit,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onReceived: suspend (T) -> Unit
): Job = launchFlowBusCollection(minLifecycleState) {
    if (isSticky) from.collectStickySuspending(key, onSubscribed, dispatcher, onReceived)
    else from.collectSuspending(key, onSubscribed, dispatcher, onReceived)
}

@MainThread
private fun LifecycleOwner.launchFlowBusCollection(
    minLifecycleState: Lifecycle.State,
    collect: suspend () -> Unit
): Job {
    require(minLifecycleState != Lifecycle.State.INITIALIZED) {
        "Lifecycle.State.INITIALIZED is not supported by repeatOnLifecycle. Use CREATED, STARTED, or RESUMED."
    }
    return lifecycleScope.launch {
        repeatOnLifecycle(minLifecycleState) { collect() }
    }
}
