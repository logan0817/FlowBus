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

/**
 * [subscribeEvent] 的短别名，更适合“监听事件”语义。
 */
@MainThread
inline fun <reified T : Any> LifecycleOwner.onEvent(
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    eventName: String = defaultEventName<T>(),
    noinline onReceived: (T) -> Unit
): Job {
    return subscribeEvent(
        dispatcher = dispatcher,
        minLifecycleState = minLifecycleState,
        isSticky = isSticky,
        eventName = eventName,
        onReceived = onReceived
    )
}

/**
 * 监听全局总线中命名 [channel] 的事件。
 */
@MainThread
fun <T : Any> LifecycleOwner.onEvent(
    channel: EventChannel<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onReceived: (T) -> Unit
): Job {
    return subscribeEvent(
        channel = channel,
        dispatcher = dispatcher,
        minLifecycleState = minLifecycleState,
        isSticky = isSticky,
        onReceived = onReceived
    )
}

/**
 * 监听来自指定 [from] 作用域的事件。
 */
@MainThread
inline fun <reified T : Any> LifecycleOwner.onEvent(
    from: ViewModelStoreOwner,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    eventName: String = defaultEventName<T>(),
    noinline onReceived: (T) -> Unit
): Job {
    return subscribeEvent(
        owner = from,
        dispatcher = dispatcher,
        minLifecycleState = minLifecycleState,
        isSticky = isSticky,
        eventName = eventName,
        onReceived = onReceived
    )
}

/**
 * 监听来自指定 [from] 作用域中命名 [channel] 的事件。
 */
@MainThread
fun <T : Any> LifecycleOwner.onEvent(
    from: ViewModelStoreOwner,
    channel: EventChannel<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onReceived: (T) -> Unit
): Job {
    return subscribeEvent(
        owner = from,
        channel = channel,
        dispatcher = dispatcher,
        minLifecycleState = minLifecycleState,
        isSticky = isSticky,
        onReceived = onReceived
    )
}

/**
 * 在当前 [CoroutineScope] 中监听全局事件。
 */
inline fun <reified T : Any> CoroutineScope.onEvent(
    isSticky: Boolean = false,
    eventName: String = defaultEventName<T>(),
    noinline onReceived: (T) -> Unit
): Job {
    return subscribeEvent(
        isSticky = isSticky,
        eventName = eventName,
        onReceived = onReceived
    )
}

/**
 * 在当前 [CoroutineScope] 中监听全局总线里命名 [channel] 的事件。
 */
fun <T : Any> CoroutineScope.onEvent(
    channel: EventChannel<T>,
    isSticky: Boolean = false,
    onReceived: (T) -> Unit
): Job {
    return subscribeEvent(
        channel = channel,
        isSticky = isSticky,
        onReceived = onReceived
    )
}

/**
 * 在当前 [CoroutineScope] 中监听来自指定 [from] 作用域的事件。
 */
@MainThread
inline fun <reified T : Any> CoroutineScope.onEvent(
    from: ViewModelStoreOwner,
    isSticky: Boolean = false,
    eventName: String = defaultEventName<T>(),
    noinline onReceived: (T) -> Unit
): Job {
    return subscribeEventFrom(
        owner = from,
        isSticky = isSticky,
        eventName = eventName,
        onReceived = onReceived
    )
}

/**
 * 在当前 [CoroutineScope] 中监听来自指定 [from] 作用域里命名 [channel] 的事件。
 */
@MainThread
fun <T : Any> CoroutineScope.onEvent(
    from: ViewModelStoreOwner,
    channel: EventChannel<T>,
    isSticky: Boolean = false,
    onReceived: (T) -> Unit
): Job {
    return subscribeEventFrom(
        owner = from,
        channel = channel,
        isSticky = isSticky,
        onReceived = onReceived
    )
}
