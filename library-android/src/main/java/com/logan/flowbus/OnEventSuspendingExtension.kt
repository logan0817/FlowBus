package com.logan.flowbus

import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.core.defaultEventName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** 监听 Android 全局事件，按生命周期顺序执行挂起回调，沿用总线的错误策略。 */
@MainThread
inline fun <reified T : Any> LifecycleOwner.onEventSuspending(
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    eventName: String = defaultEventName<T>(),
    noinline onReceived: suspend (T) -> Unit
): Job = GlobalViewModelStore.get(FlowEventBus::class.java).subscribeEventSuspending(
    this, eventName, T::class, minLifecycleState, dispatcher, isSticky, onReceived
)

/** 监听 Android 全局命名通道，生命周期停止时请求取消当前挂起处理，回调须遵循协作式取消。 */
@MainThread
fun <T : Any> LifecycleOwner.onEventSuspending(
    channel: EventChannel<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onReceived: suspend (T) -> Unit
): Job = GlobalViewModelStore.get(FlowEventBus::class.java).subscribeEventSuspending(
    this, channel.name, channel.valueType, minLifecycleState, dispatcher, isSticky, onReceived
)

/** 接收者决定订阅生命周期，[from] 决定事件来源；不改变 owner 的生命周期。 */
@MainThread
inline fun <reified T : Any> LifecycleOwner.onEventSuspending(
    from: ViewModelStoreOwner,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    eventName: String = defaultEventName<T>(),
    noinline onReceived: suspend (T) -> Unit
): Job = onEventSuspending(
    from, eventChannel(eventName, T::class), dispatcher, minLifecycleState, isSticky, onReceived
)

/** 从显式 owner 的命名通道顺序收集挂起事件。 */
@MainThread
fun <T : Any> LifecycleOwner.onEventSuspending(
    from: ViewModelStoreOwner,
    channel: EventChannel<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onReceived: suspend (T) -> Unit
): Job = flowEventBusFor(from).subscribeEventSuspending(
    this, channel.name, channel.valueType, minLifecycleState, dispatcher, isSticky, onReceived
)

/** 在调用方 scope 中顺序处理全局事件；取消会传播，不吞掉挂起回调的取消。 */
inline fun <reified T : Any> CoroutineScope.onEventSuspending(
    isSticky: Boolean = false,
    eventName: String = defaultEventName<T>(),
    noinline onReceived: suspend (T) -> Unit
): Job = launch {
    GlobalViewModelStore.get(FlowEventBus::class.java).subscribeEventSuspending(
        eventName, T::class, isSticky, onReceived = onReceived
    )
}

/** 在调用方 scope 中顺序处理全局命名通道。 */
fun <T : Any> CoroutineScope.onEventSuspending(
    channel: EventChannel<T>,
    isSticky: Boolean = false,
    onReceived: suspend (T) -> Unit
): Job = launch {
    GlobalViewModelStore.get(FlowEventBus::class.java).subscribeEventSuspending(
        channel.name, channel.valueType, isSticky, onReceived = onReceived
    )
}

/** 在主线程获取 owner 总线后，将挂起订阅交给调用方 scope 管理。 */
@MainThread
inline fun <reified T : Any> CoroutineScope.onEventSuspending(
    from: ViewModelStoreOwner,
    isSticky: Boolean = false,
    eventName: String = defaultEventName<T>(),
    noinline onReceived: suspend (T) -> Unit
): Job {
    return onEventSuspending(from, eventChannel(eventName, T::class), isSticky, onReceived)
}

/** 在主线程获取 owner 通道后，将顺序挂起订阅交给调用方 scope 管理。 */
@MainThread
fun <T : Any> CoroutineScope.onEventSuspending(
    from: ViewModelStoreOwner,
    channel: EventChannel<T>,
    isSticky: Boolean = false,
    onReceived: suspend (T) -> Unit
): Job {
    val bus = flowEventBusFor(from)
    return launch {
        bus.subscribeEventSuspending(channel.name, channel.valueType, isSticky, onReceived = onReceived)
    }
}
