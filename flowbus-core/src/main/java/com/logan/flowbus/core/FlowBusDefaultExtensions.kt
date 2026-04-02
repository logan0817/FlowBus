package com.logan.flowbus.core

/**
 * 返回 `flowbus-core` 默认使用的事件名。
 *
 * 默认以事件类型全名作为 channel name，避免额外声明 [EventKey]。
 */
inline fun <reified T : Any> defaultEventName(): String = T::class.java.name

/**
 * 在 [FlowBus] 上以默认事件名尝试发送普通事件。
 */
inline fun <reified T : Any> FlowBus.post(value: T, eventName: String = defaultEventName<T>()): Boolean {
    return post(key = eventKey<T>(name = eventName), value = value)
}

/**
 * 在 [FlowBus] 上以默认事件名挂起发送普通事件。
 */
suspend inline fun <reified T : Any> FlowBus.emit(value: T, eventName: String = defaultEventName<T>()) {
    emit(key = eventKey<T>(name = eventName), value = value)
}

/**
 * 在 [FlowBus] 上以默认事件名尝试发送粘性事件。
 */
inline fun <reified T : Any> FlowBus.postSticky(value: T, eventName: String = defaultEventName<T>()): Boolean {
    return postSticky(key = eventKey<T>(name = eventName), value = value)
}

/**
 * 在 [FlowBus] 上以默认事件名挂起发送粘性事件。
 */
suspend inline fun <reified T : Any> FlowBus.emitSticky(value: T, eventName: String = defaultEventName<T>()) {
    emitSticky(key = eventKey<T>(name = eventName), value = value)
}

/**
 * 在 [FlowBus] 上以默认事件名读取普通事件流。
 */
inline fun <reified T : Any> FlowBus.flow(eventName: String = defaultEventName<T>()) =
    flow(eventKey<T>(name = eventName))

/**
 * 在 [FlowBus] 上以默认事件名读取粘性事件流。
 */
inline fun <reified T : Any> FlowBus.stickyFlow(eventName: String = defaultEventName<T>()) =
    stickyFlow(eventKey<T>(name = eventName))

/**
 * 在 [FlowBus] 上清空指定粘性事件的 replay 缓存。
 */
inline fun <reified T : Any> FlowBus.clearSticky(eventName: String = defaultEventName<T>()) {
    clearSticky(eventKey<T>(name = eventName))
}

/**
 * 在 [FlowBus] 上彻底移除指定粘性事件。
 */
inline fun <reified T : Any> FlowBus.removeSticky(eventName: String = defaultEventName<T>()) {
    removeSticky(eventKey<T>(name = eventName))
}

/**
 * 在 [ScopedFlowBus] 上以默认事件名尝试发送普通事件。
 */
inline fun <reified T : Any> ScopedFlowBus.post(value: T, eventName: String = defaultEventName<T>()): Boolean {
    return post(key = eventKey<T>(name = eventName), value = value)
}

/**
 * 在 [ScopedFlowBus] 上以默认事件名挂起发送普通事件。
 */
suspend inline fun <reified T : Any> ScopedFlowBus.emit(value: T, eventName: String = defaultEventName<T>()) {
    emit(key = eventKey<T>(name = eventName), value = value)
}

/**
 * 在 [ScopedFlowBus] 上以默认事件名尝试发送粘性事件。
 */
inline fun <reified T : Any> ScopedFlowBus.postSticky(value: T, eventName: String = defaultEventName<T>()): Boolean {
    return postSticky(key = eventKey<T>(name = eventName), value = value)
}

/**
 * 在 [ScopedFlowBus] 上以默认事件名挂起发送粘性事件。
 */
suspend inline fun <reified T : Any> ScopedFlowBus.emitSticky(value: T, eventName: String = defaultEventName<T>()) {
    emitSticky(key = eventKey<T>(name = eventName), value = value)
}

/**
 * 在 [ScopedFlowBus] 上以默认事件名读取普通事件流。
 */
inline fun <reified T : Any> ScopedFlowBus.flow(eventName: String = defaultEventName<T>()) =
    flow(eventKey<T>(name = eventName))

/**
 * 在 [ScopedFlowBus] 上以默认事件名读取粘性事件流。
 */
inline fun <reified T : Any> ScopedFlowBus.stickyFlow(eventName: String = defaultEventName<T>()) =
    stickyFlow(eventKey<T>(name = eventName))

/**
 * 在 [ScopedFlowBus] 上清空指定粘性事件的 replay 缓存。
 */
inline fun <reified T : Any> ScopedFlowBus.clearSticky(eventName: String = defaultEventName<T>()) {
    clearSticky(eventKey<T>(name = eventName))
}

/**
 * 在 [ScopedFlowBus] 上彻底移除指定粘性事件。
 */
inline fun <reified T : Any> ScopedFlowBus.removeSticky(eventName: String = defaultEventName<T>()) {
    removeSticky(eventKey<T>(name = eventName))
}

/**
 * 在 [FlowBusScope] 上以默认事件名尝试发送普通事件。
 */
inline fun <reified T : Any> FlowBusScope.post(value: T, eventName: String = defaultEventName<T>()): Boolean {
    return post(key = eventKey<T>(name = eventName), value = value)
}

/**
 * 在 [FlowBusScope] 上以默认事件名挂起发送普通事件。
 */
suspend inline fun <reified T : Any> FlowBusScope.emit(value: T, eventName: String = defaultEventName<T>()) {
    emit(key = eventKey<T>(name = eventName), value = value)
}

/**
 * 在 [FlowBusScope] 上以默认事件名尝试发送粘性事件。
 */
inline fun <reified T : Any> FlowBusScope.postSticky(value: T, eventName: String = defaultEventName<T>()): Boolean {
    return postSticky(key = eventKey<T>(name = eventName), value = value)
}

/**
 * 在 [FlowBusScope] 上以默认事件名挂起发送粘性事件。
 */
suspend inline fun <reified T : Any> FlowBusScope.emitSticky(value: T, eventName: String = defaultEventName<T>()) {
    emitSticky(key = eventKey<T>(name = eventName), value = value)
}

/**
 * 在 [FlowBusScope] 上以默认事件名读取普通事件流。
 */
inline fun <reified T : Any> FlowBusScope.flow(eventName: String = defaultEventName<T>()) =
    flow(eventKey<T>(name = eventName))

/**
 * 在 [FlowBusScope] 上以默认事件名读取粘性事件流。
 */
inline fun <reified T : Any> FlowBusScope.stickyFlow(eventName: String = defaultEventName<T>()) =
    stickyFlow(eventKey<T>(name = eventName))

/**
 * 在 [FlowBusScope] 上清空指定粘性事件的 replay 缓存。
 */
inline fun <reified T : Any> FlowBusScope.clearSticky(eventName: String = defaultEventName<T>()) {
    clearSticky(eventKey<T>(name = eventName))
}

/**
 * 在 [FlowBusScope] 上彻底移除指定粘性事件。
 */
inline fun <reified T : Any> FlowBusScope.removeSticky(eventName: String = defaultEventName<T>()) {
    removeSticky(eventKey<T>(name = eventName))
}