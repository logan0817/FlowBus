package com.logan.flowbus

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.core.defaultEventName
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * 订阅全局总线中的事件，并由当前 [LifecycleOwner] 管理订阅生命周期。
 *
 * @param dispatcher 回调执行所在的协程调度器。
 * @param minLifecycleState 开始收集的最小生命周期状态。
 * @param isSticky 是否订阅粘性事件。
 * @param eventName 事件通道名；默认使用事件类型全名。
 * @param onReceived 收到事件后的回调。
 */
inline fun <reified T : Any> LifecycleOwner.subscribeEvent(
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    eventName: String = defaultEventName<T>(),
    noinline onReceived: (T) -> Unit
): Job {
    return GlobalViewModelStore.get(FlowEventBus::class.java)
        .subscribeEvent(
            lifecycleOwner = this,
            eventName = eventName,
            valueType = T::class,
            startState = minLifecycleState,
            dispatcher = dispatcher,
            isSticky = isSticky,
            onReceived = onReceived
        )
}

/**
 * 订阅全局总线中指定 [channel] 的事件，并由当前 [LifecycleOwner] 管理订阅生命周期。
 */
fun <T : Any> LifecycleOwner.subscribeEvent(
    channel: EventChannel<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onReceived: (T) -> Unit
): Job {
    return GlobalViewModelStore.get(FlowEventBus::class.java)
        .subscribeEvent(
            lifecycleOwner = this,
            eventName = channel.name,
            valueType = channel.valueType,
            startState = minLifecycleState,
            dispatcher = dispatcher,
            isSticky = isSticky,
            onReceived = onReceived
        )
}

/**
 * 订阅指定 [owner] 对应局部总线中的事件，并由当前 [LifecycleOwner] 管理订阅生命周期。
 *
 * 这是 Android 层最容易混淆的地方：
 * - 接收者 [LifecycleOwner] 决定“订阅活多久”
 * - 参数 [owner] 决定“从哪个总线收”
 *
 * [owner] 不局限于 Activity / Fragment，只要实现了 `ViewModelStoreOwner` 即可。
 */
inline fun <reified T : Any> LifecycleOwner.subscribeEvent(
    owner: ViewModelStoreOwner,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    eventName: String = defaultEventName<T>(),
    noinline onReceived: (T) -> Unit
): Job {
    return ViewModelProvider(owner).get(FlowEventBus::class.java)
        .subscribeEvent(
            lifecycleOwner = this,
            eventName = eventName,
            valueType = T::class,
            startState = minLifecycleState,
            dispatcher = dispatcher,
            isSticky = isSticky,
            onReceived = onReceived
        )
}

/**
 * 订阅指定 [owner] 局部总线中命名 [channel] 的事件，并由当前 [LifecycleOwner] 管理订阅生命周期。
 */
fun <T : Any> LifecycleOwner.subscribeEvent(
    owner: ViewModelStoreOwner,
    channel: EventChannel<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onReceived: (T) -> Unit
): Job {
    return ViewModelProvider(owner).get(FlowEventBus::class.java)
        .subscribeEvent(
            lifecycleOwner = this,
            eventName = channel.name,
            valueType = channel.valueType,
            startState = minLifecycleState,
            dispatcher = dispatcher,
            isSticky = isSticky,
            onReceived = onReceived
        )
}

/**
 * 在当前 [CoroutineScope] 中订阅全局总线事件。
 *
 * 订阅生命周期完全由当前协程作用域管理，适用于 ViewModel、Repository、Worker
 * 以及其他非 UI 场景。
 */
inline fun <reified T : Any> CoroutineScope.subscribeEvent(
    isSticky: Boolean = false,
    eventName: String = defaultEventName<T>(),
    noinline onReceived: (T) -> Unit
): Job = launch {
    GlobalViewModelStore.get(FlowEventBus::class.java)
        .subscribeEvent(
            eventName = eventName,
            valueType = T::class,
            isSticky = isSticky,
            onReceived = onReceived
        )
}

/**
 * 在当前 [CoroutineScope] 中订阅全局总线里命名 [channel] 的事件。
 */
fun <T : Any> CoroutineScope.subscribeEvent(
    channel: EventChannel<T>,
    isSticky: Boolean = false,
    onReceived: (T) -> Unit
): Job = launch {
    GlobalViewModelStore.get(FlowEventBus::class.java)
        .subscribeEvent(
            eventName = channel.name,
            valueType = channel.valueType,
            isSticky = isSticky,
            onReceived = onReceived
        )
}

/**
 * 在当前 [CoroutineScope] 中订阅指定 [owner] 对应局部总线的事件。
 *
 * - 当前 [CoroutineScope] 决定“订阅活多久”
 * - 参数 [owner] 决定“从哪个总线收”
 */
inline fun <reified T : Any> CoroutineScope.subscribeEventFrom(
    owner: ViewModelStoreOwner,
    isSticky: Boolean = false,
    eventName: String = defaultEventName<T>(),
    noinline onReceived: (T) -> Unit
): Job = launch {
    ViewModelProvider(owner).get(FlowEventBus::class.java)
        .subscribeEvent(
            eventName = eventName,
            valueType = T::class,
            isSticky = isSticky,
            onReceived = onReceived
        )
}

/**
 * 在当前 [CoroutineScope] 中订阅指定 [owner] 局部总线里命名 [channel] 的事件。
 */
fun <T : Any> CoroutineScope.subscribeEventFrom(
    owner: ViewModelStoreOwner,
    channel: EventChannel<T>,
    isSticky: Boolean = false,
    onReceived: (T) -> Unit
): Job = launch {
    ViewModelProvider(owner).get(FlowEventBus::class.java)
        .subscribeEvent(
            eventName = channel.name,
            valueType = channel.valueType,
            isSticky = isSticky,
            onReceived = onReceived
        )
}
