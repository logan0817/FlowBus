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
 * @param delayMillis The delay in milliseconds before the event is emitted. Default is 0 (immediate).
 * 事件发射前的延迟时间（毫秒）。默认是 0（立即）。
 */
inline fun <reified T : Any> postEvent(event: T, delayMillis: Long = 0L) {
    GlobalViewModelStore.get(FlowEventBus::class.java)
        .post(eventName = T::class.java.name, value = event, delayMillis = delayMillis)
}

/**
 * Scoped event post with an explicit bus owner.
 *
 * 显式指定事件总线所属 owner 的局部事件发送。
 */
inline fun <reified T : Any> postEventTo(owner: ViewModelStoreOwner, event: T, delayMillis: Long = 0L) {
    ViewModelProvider(owner = owner).get(FlowEventBus::class.java)
        .post(eventName = T::class.java.name, value = event, delayMillis = delayMillis)
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
 * @param delayMillis The delay in milliseconds before the event is emitted. Default is 0 (immediate).
 * 事件发射前的延迟时间（毫秒）。默认是 0（立即）。
 */
@Deprecated(
    message = "Use postEventTo(owner = ..., event = ...) for scoped events.",
    replaceWith = ReplaceWith("postEventTo(owner = scope, event = event, delayMillis = delayMillis)")
)
inline fun <reified T : Any> postEvent(scope: ViewModelStoreOwner, event: T, delayMillis: Long = 0L) {
    postEventTo(owner = scope, event = event, delayMillis = delayMillis)
}

/**
 * Global Scope Sticky Event Post.
 * Publishes an event payload associated with the generic type T to the sticky bus.
 *
 * 全局作用域粘性事件发布。
 * 发布与泛型类型 T 关联的粘性事件数据到事件总线。
 */
inline fun <reified T : Any> postStickyEvent(event: T, delayMillis: Long = 0L) {
    GlobalViewModelStore.get(FlowEventBus::class.java)
        .post(eventName = T::class.java.name, value = event, isSticky = true, delayMillis = delayMillis)
}

/**
 * Scoped sticky event post with an explicit bus owner.
 *
 * 显式指定事件总线所属 owner 的局部粘性事件发送。
 */
inline fun <reified T : Any> postStickyEventTo(owner: ViewModelStoreOwner, event: T, delayMillis: Long = 0L) {
    ViewModelProvider(owner = owner).get(FlowEventBus::class.java)
        .post(eventName = T::class.java.name, value = event, isSticky = true, delayMillis = delayMillis)
}

/**
 * Limited Scope Sticky Event Post.
 * Publishes an event payload associated with the generic type T to the scoped sticky bus.
 *
 * 限定作用域粘性事件发布。
 * 发布与泛型类型 T 关联的粘性事件数据到事件总线。
 */
@Deprecated(
    message = "Use postStickyEventTo(owner = ..., event = ...) for scoped sticky events.",
    replaceWith = ReplaceWith("postStickyEventTo(owner = scope, event = event, delayMillis = delayMillis)")
)
inline fun <reified T : Any> postStickyEvent(scope: ViewModelStoreOwner, event: T, delayMillis: Long = 0L) {
    postStickyEventTo(owner = scope, event = event, delayMillis = delayMillis)
}
