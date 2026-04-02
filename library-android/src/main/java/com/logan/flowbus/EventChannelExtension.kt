package com.logan.flowbus

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner

/**
 * 向 Android 全局总线发送普通事件。
 */
fun <T : Any> EventChannel<T>.post(value: T, delayMillis: Long = 0L) {
    GlobalViewModelStore.get(FlowEventBus::class.java)
        .post(eventName = name, value = value, valueType = valueType, delayMillis = delayMillis)
}

/**
 * 挂起直到普通事件成功发送到 Android 全局总线。
 */
suspend fun <T : Any> EventChannel<T>.emit(value: T, delayMillis: Long = 0L) {
    GlobalViewModelStore.get(FlowEventBus::class.java)
        .emit(eventName = name, value = value, valueType = valueType, delayMillis = delayMillis)
}

/**
 * 向 Android 全局总线发送粘性事件。
 */
fun <T : Any> EventChannel<T>.postSticky(value: T, delayMillis: Long = 0L) {
    GlobalViewModelStore.get(FlowEventBus::class.java)
        .post(
            eventName = name,
            value = value,
            valueType = valueType,
            isSticky = true,
            delayMillis = delayMillis
        )
}

/**
 * 挂起直到粘性事件成功发送到 Android 全局总线。
 */
suspend fun <T : Any> EventChannel<T>.emitSticky(value: T, delayMillis: Long = 0L) {
    GlobalViewModelStore.get(FlowEventBus::class.java)
        .emit(
            eventName = name,
            value = value,
            valueType = valueType,
            isSticky = true,
            delayMillis = delayMillis
        )
}

/**
 * 返回 Android 全局总线上的普通事件流。
 */
fun <T : Any> EventChannel<T>.flow(isSticky: Boolean = false) =
    GlobalViewModelStore.get(FlowEventBus::class.java)
        .eventFlow(eventName = name, valueType = valueType, isSticky = isSticky)

/**
 * 返回 Android 全局总线上的粘性事件流。
 */
fun <T : Any> EventChannel<T>.stickyFlow() = flow(isSticky = true)

/**
 * 从 Android 全局总线中彻底移除该 sticky 事件。
 */
fun <T : Any> EventChannel<T>.removeSticky() {
    GlobalViewModelStore.get(FlowEventBus::class.java)
        .removeStickyEvent(eventName = name, valueType = valueType)
}

/**
 * 清空 Android 全局总线中该 sticky 事件的 replay 缓存。
 */
fun <T : Any> EventChannel<T>.clearSticky() {
    GlobalViewModelStore.get(FlowEventBus::class.java)
        .clearStickyEvent(eventName = name, valueType = valueType)
}

/**
 * 向指定 [owner] 对应的局部总线发送普通事件。
 */
fun <T : Any> EventChannel<T>.postTo(
    owner: ViewModelStoreOwner,
    value: T,
    delayMillis: Long = 0L
) {
    ViewModelProvider(owner).get(FlowEventBus::class.java)
        .post(eventName = name, value = value, valueType = valueType, delayMillis = delayMillis)
}

/**
 * 挂起直到普通事件成功发送到指定 [owner] 对应的局部总线。
 */
suspend fun <T : Any> EventChannel<T>.emitTo(
    owner: ViewModelStoreOwner,
    value: T,
    delayMillis: Long = 0L
) {
    ViewModelProvider(owner).get(FlowEventBus::class.java)
        .emit(eventName = name, value = value, valueType = valueType, delayMillis = delayMillis)
}

/**
 * 向指定 [owner] 对应的局部总线发送粘性事件。
 */
fun <T : Any> EventChannel<T>.postStickyTo(
    owner: ViewModelStoreOwner,
    value: T,
    delayMillis: Long = 0L
) {
    ViewModelProvider(owner).get(FlowEventBus::class.java)
        .post(
            eventName = name,
            value = value,
            valueType = valueType,
            isSticky = true,
            delayMillis = delayMillis
        )
}

/**
 * 挂起直到粘性事件成功发送到指定 [owner] 对应的局部总线。
 */
suspend fun <T : Any> EventChannel<T>.emitStickyTo(
    owner: ViewModelStoreOwner,
    value: T,
    delayMillis: Long = 0L
) {
    ViewModelProvider(owner).get(FlowEventBus::class.java)
        .emit(
            eventName = name,
            value = value,
            valueType = valueType,
            isSticky = true,
            delayMillis = delayMillis
        )
}

/**
 * 返回指定 [owner] 对应局部总线上的普通事件流。
 */
fun <T : Any> EventChannel<T>.flowFrom(
    owner: ViewModelStoreOwner,
    isSticky: Boolean = false
) = ViewModelProvider(owner).get(FlowEventBus::class.java)
    .eventFlow(eventName = name, valueType = valueType, isSticky = isSticky)

/**
 * 返回指定 [owner] 对应局部总线上的粘性事件流。
 */
fun <T : Any> EventChannel<T>.stickyFlowFrom(owner: ViewModelStoreOwner) =
    flowFrom(owner = owner, isSticky = true)

/**
 * 从指定 [owner] 对应的局部总线中彻底移除该 sticky 事件。
 */
fun <T : Any> EventChannel<T>.removeStickyFrom(owner: ViewModelStoreOwner) {
    ViewModelProvider(owner).get(FlowEventBus::class.java)
        .removeStickyEvent(eventName = name, valueType = valueType)
}

/**
 * 清空指定 [owner] 对应局部总线中该 sticky 事件的 replay 缓存。
 */
fun <T : Any> EventChannel<T>.clearStickyFrom(owner: ViewModelStoreOwner) {
    ViewModelProvider(owner).get(FlowEventBus::class.java)
        .clearStickyEvent(eventName = name, valueType = valueType)
}
