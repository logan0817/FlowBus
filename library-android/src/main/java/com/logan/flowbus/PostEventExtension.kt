package com.logan.flowbus

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.core.defaultEventName

/**
 * 向全局总线发送事件。
 *
 * 这是 best-effort 发送；当底层缓冲已满时，事件可能不会被接收。
 * 如果你需要明确知道这次发送是否被当前总线接收，请使用 [tryPostEvent]；
 * 如果你需要遵循背压策略并保证发送成功，请使用 [emitEvent]。
 *
 * @param event 事件数据。
 * @param delayMillis 延迟发送时间，单位毫秒。
 * @param eventName 事件通道名；默认使用事件类型全名。
 */
inline fun <reified T : Any> postEvent(
    event: T,
    delayMillis: Long = 0L,
    eventName: String = defaultEventName<T>()
) {
    tryPostEvent(event = event, delayMillis = delayMillis, eventName = eventName)
}

/**
 * 尝试向全局总线发送事件，并返回当前调用是否已被总线接收。
 *
 * - 返回 `true`：事件已被接收；若使用延迟发送，则表示发送任务已成功安排
 * - 返回 `false`：当前缓冲已满，事件被丢弃
 */
inline fun <reified T : Any> tryPostEvent(
    event: T,
    delayMillis: Long = 0L,
    eventName: String = defaultEventName<T>()
): Boolean {
    return GlobalViewModelStore.get(FlowEventBus::class.java)
        .post(eventName = eventName, value = event, valueType = T::class, delayMillis = delayMillis)
}

/**
 * 向指定 [owner] 对应的局部总线发送事件。
 *
 * 这是 best-effort 发送；当底层缓冲已满时，事件可能不会被接收。
 * 如果你需要明确知道这次发送是否被当前总线接收，请使用 [tryPostEventTo]；
 * 如果你需要遵循背压策略并保证发送成功，请使用 [emitEventTo]。
 *
 * [owner] 用来决定“往哪个总线发”，它不限制具体类型，只要实现了
 * `ViewModelStoreOwner` 即可。
 *
 * @param owner 事件总线所属作用域。
 * @param event 事件数据。
 * @param delayMillis 延迟发送时间，单位毫秒。
 * @param eventName 事件通道名；默认使用事件类型全名。
 */
inline fun <reified T : Any> postEventTo(
    owner: ViewModelStoreOwner,
    event: T,
    delayMillis: Long = 0L,
    eventName: String = defaultEventName<T>()
) {
    tryPostEventTo(owner = owner, event = event, delayMillis = delayMillis, eventName = eventName)
}

/**
 * 尝试向指定 [owner] 对应的局部总线发送事件，并返回当前调用是否已被总线接收。
 */
inline fun <reified T : Any> tryPostEventTo(
    owner: ViewModelStoreOwner,
    event: T,
    delayMillis: Long = 0L,
    eventName: String = defaultEventName<T>()
): Boolean {
    return ViewModelProvider(owner = owner).get(FlowEventBus::class.java)
        .post(eventName = eventName, value = event, valueType = T::class, delayMillis = delayMillis)
}

/**
 * 挂起直到全局事件成功发送。
 */
suspend inline fun <reified T : Any> emitEvent(
    event: T,
    delayMillis: Long = 0L,
    eventName: String = defaultEventName<T>()
) {
    GlobalViewModelStore.get(FlowEventBus::class.java)
        .emit(eventName = eventName, value = event, valueType = T::class, delayMillis = delayMillis)
}

/**
 * 挂起直到指定 [owner] 对应的局部事件成功发送。
 */
suspend inline fun <reified T : Any> emitEventTo(
    owner: ViewModelStoreOwner,
    event: T,
    delayMillis: Long = 0L,
    eventName: String = defaultEventName<T>()
) {
    ViewModelProvider(owner = owner).get(FlowEventBus::class.java)
        .emit(eventName = eventName, value = event, valueType = T::class, delayMillis = delayMillis)
}

/**
 * 向全局总线发送粘性事件。
 *
 * 这是 best-effort 发送；当底层缓冲已满时，事件可能不会被接收。
 * 如果你需要明确知道这次发送是否被当前总线接收，请使用 [tryPostStickyEvent]；
 * 如果你需要遵循背压策略并保证发送成功，请使用 [emitStickyEvent]。
 */
inline fun <reified T : Any> postStickyEvent(
    event: T,
    delayMillis: Long = 0L,
    eventName: String = defaultEventName<T>()
) {
    tryPostStickyEvent(event = event, delayMillis = delayMillis, eventName = eventName)
}

/**
 * 尝试向全局总线发送粘性事件，并返回当前调用是否已被总线接收。
 */
inline fun <reified T : Any> tryPostStickyEvent(
    event: T,
    delayMillis: Long = 0L,
    eventName: String = defaultEventName<T>()
): Boolean {
    return GlobalViewModelStore.get(FlowEventBus::class.java)
        .post(
            eventName = eventName,
            value = event,
            valueType = T::class,
            isSticky = true,
            delayMillis = delayMillis
        )
}

/**
 * 向指定 [owner] 对应的局部总线发送粘性事件。
 */
inline fun <reified T : Any> postStickyEventTo(
    owner: ViewModelStoreOwner,
    event: T,
    delayMillis: Long = 0L,
    eventName: String = defaultEventName<T>()
) {
    tryPostStickyEventTo(owner = owner, event = event, delayMillis = delayMillis, eventName = eventName)
}

/**
 * 尝试向指定 [owner] 对应的局部总线发送粘性事件，并返回当前调用是否已被总线接收。
 */
inline fun <reified T : Any> tryPostStickyEventTo(
    owner: ViewModelStoreOwner,
    event: T,
    delayMillis: Long = 0L,
    eventName: String = defaultEventName<T>()
): Boolean {
    return ViewModelProvider(owner = owner).get(FlowEventBus::class.java)
        .post(
            eventName = eventName,
            value = event,
            valueType = T::class,
            isSticky = true,
            delayMillis = delayMillis
        )
}

/**
 * 挂起直到全局粘性事件成功发送。
 */
suspend inline fun <reified T : Any> emitStickyEvent(
    event: T,
    delayMillis: Long = 0L,
    eventName: String = defaultEventName<T>()
) {
    GlobalViewModelStore.get(FlowEventBus::class.java)
        .emit(
            eventName = eventName,
            value = event,
            valueType = T::class,
            isSticky = true,
            delayMillis = delayMillis
        )
}

/**
 * 挂起直到指定 [owner] 对应的局部粘性事件成功发送。
 */
suspend inline fun <reified T : Any> emitStickyEventTo(
    owner: ViewModelStoreOwner,
    event: T,
    delayMillis: Long = 0L,
    eventName: String = defaultEventName<T>()
) {
    ViewModelProvider(owner = owner).get(FlowEventBus::class.java)
        .emit(
            eventName = eventName,
            value = event,
            valueType = T::class,
            isSticky = true,
            delayMillis = delayMillis
        )
}
