package com.logan.flowbus

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Permanently removes the Sticky Event Flow and its replay cache for event type T from the global bus.
 *
 * 从全局总线中永久移除事件类型 T 的粘性事件 Flow 及其重放缓存。
 */
inline fun <reified T> removeStickyEvent() {
    GlobalViewModelStore.get(FlowEventBus::class.java).removeStickEvent(T::class.java.name)
}

/**
 * Permanently removes the Sticky Event Flow and its replay cache for event type T from the local bus.
 *
 * 从本地总线中永久移除事件类型 T 的粘性事件 Flow 及其重放缓存。
 *
 * @param scope The ViewModelStoreOwner providing the local FlowEventBus instance.
 */
inline fun <reified T> removeStickyEvent(scope: ViewModelStoreOwner) {
    ViewModelProvider(scope).get(FlowEventBus::class.java).removeStickEvent(T::class.java.name)
}

/**
 * Clears the replay cache for the global Sticky Event Flow of type T, but keeps the Flow instance.
 *
 * 清除全局粘性事件类型 T 的重放缓存，但保留 Flow 实例。
 */
inline fun <reified T> clearStickyEvent() {
    GlobalViewModelStore.get(FlowEventBus::class.java).clearStickEvent(T::class.java.name)
}

/**
 * Clears the replay cache for the local Sticky Event Flow of type T, but keeps the Flow instance.
 *
 * 清除本地粘性事件类型 T 的重放缓存，但保留 Flow 实例。
 *
 * @param scope The ViewModelStoreOwner providing the local FlowEventBus instance.
 */
inline fun <reified T> clearStickyEvent(scope: ViewModelStoreOwner) {
    ViewModelProvider(scope).get(FlowEventBus::class.java).clearStickEvent(T::class.java.name)
}
