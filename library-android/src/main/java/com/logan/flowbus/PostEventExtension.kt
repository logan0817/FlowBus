package com.logan.flowbus

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner


/**
 * Global Scope Event Post.
 * Publishes an event payload associated with the generic type T to the bus.
 *
 * 全局作用域事件发布。
 * 发布与泛型类型 T 关联的事件数据到事件总线。
 *
 * @param event The event payload.
 * 事件数据。
 * @param timeMillis The delay in milliseconds before the event is emitted. Default is 0 (immediate).
 * 事件发射前的延迟时间（毫秒）。默认是 0（立即）。
 */
inline fun <reified T : Any> postEvent(event: T, timeMillis: Long = 0L) {
    GlobalViewModelStore.get(FlowEventBus::class.java).post(eventName = T::class.java.name, value = event, timeMillis = timeMillis)
}

/**
 * Limited Scope Event Post.
 * Publishes an event payload associated with the generic type T to the bus.
 *
 * 限定作用域事件发布。
 * 发布与泛型类型 T 关联的事件数据到事件总线。
 *
 * @param scope Scope
 * 作用域
 * @param event The event payload.
 * 事件数据。
 * @param timeMillis The delay in milliseconds before the event is emitted. Default is 0 (immediate).
 * 事件发射前的延迟时间（毫秒）。默认是 0（立即）。
 */
inline fun <reified T : Any> postEvent(scope: ViewModelStoreOwner, event: T, timeMillis: Long = 0L) {
    ViewModelProvider(owner = scope).get(FlowEventBus::class.java).post(eventName = T::class.java.name, value = event, timeMillis = timeMillis)
}