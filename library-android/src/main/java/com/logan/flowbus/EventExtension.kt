package com.logan.flowbus

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.core.defaultEventName

/**
 * 从全局总线中彻底移除事件类型 [T] 的粘性 Flow 及其重放缓存。
 */
inline fun <reified T : Any> removeStickyEvent(
    eventName: String = defaultEventName<T>()
) {
    GlobalViewModelStore.get(FlowEventBus::class.java)
        .removeStickyEvent(eventName = eventName, valueType = T::class)
}

/**
 * 从指定 [owner] 对应的局部总线中彻底移除事件类型 [T] 的粘性 Flow 及其重放缓存。
 */
@MainThread
inline fun <reified T : Any> removeStickyEvent(
    owner: ViewModelStoreOwner,
    eventName: String = defaultEventName<T>()
) {
    (if (owner === GlobalViewModelStore) {
        GlobalViewModelStore.get(FlowEventBus::class.java)
    } else {
        ViewModelProvider(owner).get(FlowEventBus::class.java)
    })
        .removeStickyEvent(eventName = eventName, valueType = T::class)
}

/**
 * 清空全局总线中事件类型 [T] 的粘性重放缓存，但保留 Flow 实例。
 */
inline fun <reified T : Any> clearStickyEvent(
    eventName: String = defaultEventName<T>()
) {
    GlobalViewModelStore.get(FlowEventBus::class.java)
        .clearStickyEvent(eventName = eventName, valueType = T::class)
}

/**
 * 清空指定 [owner] 对应局部总线中事件类型 [T] 的粘性重放缓存，但保留 Flow 实例。
 */
@MainThread
inline fun <reified T : Any> clearStickyEvent(
    owner: ViewModelStoreOwner,
    eventName: String = defaultEventName<T>()
) {
    (if (owner === GlobalViewModelStore) {
        GlobalViewModelStore.get(FlowEventBus::class.java)
    } else {
        ViewModelProvider(owner).get(FlowEventBus::class.java)
    })
        .clearStickyEvent(eventName = eventName, valueType = T::class)
}

/**
 * 读取全局总线中事件类型 [T] 的最新 sticky replay 值，并清空该 sticky replay 缓存。
 *
 * 适合导航、Toast、Dialog 这类只想消费一次的 sticky 事件。
 */
inline fun <reified T : Any> consumeStickyLatestEvent(
    eventName: String = defaultEventName<T>()
): T? {
    return GlobalViewModelStore.get(FlowEventBus::class.java)
        .consumeStickyLatestEvent(eventName = eventName, valueType = T::class)
}

/**
 * 读取指定 [owner] 对应局部总线中事件类型 [T] 的最新 sticky replay 值，并清空 replay 缓存。
 */
@MainThread
inline fun <reified T : Any> consumeStickyLatestEvent(
    owner: ViewModelStoreOwner,
    eventName: String = defaultEventName<T>()
): T? {
    return (if (owner === GlobalViewModelStore) {
        GlobalViewModelStore.get(FlowEventBus::class.java)
    } else {
        ViewModelProvider(owner).get(FlowEventBus::class.java)
    })
        .consumeStickyLatestEvent(eventName = eventName, valueType = T::class)
}
