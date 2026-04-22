package com.logan.flowbus

import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.core.defaultEventName

/**
 * 以当前 [ViewModelStoreOwner] 作为事件作用域读取事件流。
 *
 * 这个扩展把 `eventFlowFrom(owner = ...)` 变成了更明确的
 * `owner.scopedEventFlow<T>()` 写法。
 */
inline fun <reified T : Any> ViewModelStoreOwner.scopedEventFlow(
    isSticky: Boolean = false,
    eventName: String = defaultEventName<T>()
) = eventFlowFrom<T>(owner = this, isSticky = isSticky, eventName = eventName)

/**
 * 使用命名 [channel] 读取当前 [ViewModelStoreOwner] 作用域下的事件流。
 */
fun <T : Any> ViewModelStoreOwner.scopedEventFlow(
    channel: EventChannel<T>,
    isSticky: Boolean = false
) = channel.flowFrom(owner = this, isSticky = isSticky)

/**
 * 读取当前 [ViewModelStoreOwner] 作用域下的粘性事件流。
 */
inline fun <reified T : Any> ViewModelStoreOwner.scopedStickyEventFlow(
    eventName: String = defaultEventName<T>()
) = stickyEventFlowFrom<T>(owner = this, eventName = eventName)

/**
 * 使用命名 [channel] 读取当前 [ViewModelStoreOwner] 作用域下的粘性事件流。
 */
fun <T : Any> ViewModelStoreOwner.scopedStickyEventFlow(
    channel: EventChannel<T>
) = channel.stickyFlowFrom(owner = this)

/**
 * 以当前 [ViewModelStoreOwner] 作为事件作用域发送普通事件。
 */
inline fun <reified T : Any> ViewModelStoreOwner.postScopedEvent(
    event: T,
    delayMillis: Long = 0L,
    eventName: String = defaultEventName<T>()
) {
    postEventTo(owner = this, event = event, delayMillis = delayMillis, eventName = eventName)
}

/**
 * 使用命名 [channel] 向当前 [ViewModelStoreOwner] 作用域发送普通事件。
 */
fun <T : Any> ViewModelStoreOwner.postScopedEvent(
    channel: EventChannel<T>,
    event: T,
    delayMillis: Long = 0L
) {
    channel.postTo(owner = this, value = event, delayMillis = delayMillis)
}

/**
 * 以当前 [ViewModelStoreOwner] 作为事件作用域挂起发送普通事件。
 */
suspend inline fun <reified T : Any> ViewModelStoreOwner.emitScopedEvent(
    event: T,
    delayMillis: Long = 0L,
    eventName: String = defaultEventName<T>()
) {
    emitEventTo(owner = this, event = event, delayMillis = delayMillis, eventName = eventName)
}

/**
 * 使用命名 [channel] 向当前 [ViewModelStoreOwner] 作用域挂起发送普通事件。
 */
suspend fun <T : Any> ViewModelStoreOwner.emitScopedEvent(
    channel: EventChannel<T>,
    event: T,
    delayMillis: Long = 0L
) {
    channel.emitTo(owner = this, value = event, delayMillis = delayMillis)
}

/**
 * 以当前 [ViewModelStoreOwner] 作为事件作用域发送粘性事件。
 */
inline fun <reified T : Any> ViewModelStoreOwner.postScopedStickyEvent(
    event: T,
    delayMillis: Long = 0L,
    eventName: String = defaultEventName<T>()
) {
    postStickyEventTo(owner = this, event = event, delayMillis = delayMillis, eventName = eventName)
}

/**
 * 使用命名 [channel] 向当前 [ViewModelStoreOwner] 作用域发送粘性事件。
 */
fun <T : Any> ViewModelStoreOwner.postScopedStickyEvent(
    channel: EventChannel<T>,
    event: T,
    delayMillis: Long = 0L
) {
    channel.postStickyTo(owner = this, value = event, delayMillis = delayMillis)
}

/**
 * 以当前 [ViewModelStoreOwner] 作为事件作用域挂起发送粘性事件。
 */
suspend inline fun <reified T : Any> ViewModelStoreOwner.emitScopedStickyEvent(
    event: T,
    delayMillis: Long = 0L,
    eventName: String = defaultEventName<T>()
) {
    emitStickyEventTo(owner = this, event = event, delayMillis = delayMillis, eventName = eventName)
}

/**
 * 使用命名 [channel] 向当前 [ViewModelStoreOwner] 作用域挂起发送粘性事件。
 */
suspend fun <T : Any> ViewModelStoreOwner.emitScopedStickyEvent(
    channel: EventChannel<T>,
    event: T,
    delayMillis: Long = 0L
) {
    channel.emitStickyTo(owner = this, value = event, delayMillis = delayMillis)
}
