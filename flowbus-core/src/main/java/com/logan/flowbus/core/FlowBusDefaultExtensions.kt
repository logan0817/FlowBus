package com.logan.flowbus.core

import kotlinx.coroutines.CoroutineDispatcher

/*
 * 这组扩展适合“手里已经有 `FlowBus` / `ScopedFlowBus` / `FlowBusScope`，想直接按事件类型或显式
 * `eventName` 收发事件”的场景。
 *
 * 和 [EventChannel] 相比，它更轻，不需要额外定义通道对象；
 * 和值糖 API 相比，它更适合“以 bus 为主语”来写发送和订阅代码；
 * 如果你想把发送动作写得最短，用值糖 API；如果你想把通道本身定义成稳定对象，用 [EventChannel]。
 */
/**
 * 返回 `flowbus-core` 默认使用的事件名。
 *
 * 默认以事件类型全名作为 channel name，避免额外声明 [EventKey]。
 * 这意味着同一个事件类型默认会进入同一个通道；如果你想给同一类型拆多个通道，
 * 请显式传入 `eventName`，或改用 [EventChannel]。
 */
inline fun <reified T : Any> defaultEventName(): String = T::class.java.name

/**
 * 在 [FlowBus] 上以默认事件名尝试发送普通事件。
 */
inline fun <reified T : Any> FlowBus.post(value: T, eventName: String = defaultEventName<T>()): Boolean {
    return post(key = eventKey<T>(name = eventName), value = value)
}

/**
 * 在 [FlowBus] 上以默认事件名尝试发送普通事件，并返回总线层面的诊断结果。
 */
inline fun <reified T : Any> FlowBus.tryPostResult(
    value: T,
    eventName: String = defaultEventName<T>()
): FlowBusPostResult {
    return tryPostResult(key = eventKey<T>(name = eventName), value = value)
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
 * 在 [FlowBus] 上以默认事件名尝试发送粘性事件，并返回总线层面的诊断结果。
 */
inline fun <reified T : Any> FlowBus.tryPostStickyResult(
    value: T,
    eventName: String = defaultEventName<T>()
): FlowBusPostResult {
    return tryPostStickyResult(key = eventKey<T>(name = eventName), value = value)
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
 * 在 [FlowBus] 上以默认事件名顺序收集普通事件，并自动使用该 bus 的错误处理配置。
 */
suspend inline fun <reified T : Any> FlowBus.collect(
    eventName: String = defaultEventName<T>(),
    dispatcher: CoroutineDispatcher? = null,
    noinline onReceived: (T) -> Unit
) {
    collect(key = eventKey<T>(name = eventName), dispatcher = dispatcher, onReceived = onReceived)
}

/**
 * 在 [FlowBus] 上以默认事件名顺序收集粘性事件，并自动使用该 bus 的错误处理配置。
 */
suspend inline fun <reified T : Any> FlowBus.collectSticky(
    eventName: String = defaultEventName<T>(),
    dispatcher: CoroutineDispatcher? = null,
    noinline onReceived: (T) -> Unit
) {
    collectSticky(key = eventKey<T>(name = eventName), dispatcher = dispatcher, onReceived = onReceived)
}

/**
 * 在 [FlowBus] 上移除指定普通事件的当前 store 条目。
 */
inline fun <reified T : Any> FlowBus.removeEvent(eventName: String = defaultEventName<T>()) {
    removeEvent(eventKey<T>(name = eventName))
}

/**
 * 在 [FlowBus] 上清空指定粘性事件的 replay 缓存。
 */
inline fun <reified T : Any> FlowBus.clearSticky(eventName: String = defaultEventName<T>()) {
    clearSticky(eventKey<T>(name = eventName))
}

/**
 * 在 [FlowBus] 上移除指定粘性事件的当前 store 条目，并清空现有 replay 缓存。
 */
inline fun <reified T : Any> FlowBus.removeSticky(eventName: String = defaultEventName<T>()) {
    removeSticky(eventKey<T>(name = eventName))
}

/**
 * 在 [FlowBus] 上读取指定粘性事件的最新 replay 值，并清空该 sticky replay 缓存。
 */
inline fun <reified T : Any> FlowBus.consumeStickyLatest(eventName: String = defaultEventName<T>()): T? {
    return consumeStickyLatest(eventKey<T>(name = eventName))
}

/**
 * 在 [ScopedFlowBus] 上以默认事件名尝试发送普通事件。
 */
inline fun <reified T : Any> ScopedFlowBus.post(value: T, eventName: String = defaultEventName<T>()): Boolean {
    return post(key = eventKey<T>(name = eventName), value = value)
}

/**
 * 在 [ScopedFlowBus] 上以默认事件名尝试发送普通事件，并返回总线层面的诊断结果。
 */
inline fun <reified T : Any> ScopedFlowBus.tryPostResult(
    value: T,
    eventName: String = defaultEventName<T>()
): FlowBusPostResult {
    return tryPostResult(key = eventKey<T>(name = eventName), value = value)
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
 * 在 [ScopedFlowBus] 上以默认事件名尝试发送粘性事件，并返回总线层面的诊断结果。
 */
inline fun <reified T : Any> ScopedFlowBus.tryPostStickyResult(
    value: T,
    eventName: String = defaultEventName<T>()
): FlowBusPostResult {
    return tryPostStickyResult(key = eventKey<T>(name = eventName), value = value)
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
 * 在 [ScopedFlowBus] 上以默认事件名顺序收集普通事件，并自动使用所属 bus 的错误处理配置。
 */
suspend inline fun <reified T : Any> ScopedFlowBus.collect(
    eventName: String = defaultEventName<T>(),
    dispatcher: CoroutineDispatcher? = null,
    noinline onReceived: (T) -> Unit
) {
    collect(key = eventKey<T>(name = eventName), dispatcher = dispatcher, onReceived = onReceived)
}

/**
 * 在 [ScopedFlowBus] 上以默认事件名顺序收集粘性事件，并自动使用所属 bus 的错误处理配置。
 */
suspend inline fun <reified T : Any> ScopedFlowBus.collectSticky(
    eventName: String = defaultEventName<T>(),
    dispatcher: CoroutineDispatcher? = null,
    noinline onReceived: (T) -> Unit
) {
    collectSticky(key = eventKey<T>(name = eventName), dispatcher = dispatcher, onReceived = onReceived)
}

/**
 * 在 [ScopedFlowBus] 上移除指定普通事件的当前 store 条目。
 */
inline fun <reified T : Any> ScopedFlowBus.removeEvent(eventName: String = defaultEventName<T>()) {
    removeEvent(eventKey<T>(name = eventName))
}

/**
 * 在 [ScopedFlowBus] 上清空指定粘性事件的 replay 缓存。
 */
inline fun <reified T : Any> ScopedFlowBus.clearSticky(eventName: String = defaultEventName<T>()) {
    clearSticky(eventKey<T>(name = eventName))
}

/**
 * 在 [ScopedFlowBus] 上移除指定粘性事件的当前 store 条目，并清空现有 replay 缓存。
 */
inline fun <reified T : Any> ScopedFlowBus.removeSticky(eventName: String = defaultEventName<T>()) {
    removeSticky(eventKey<T>(name = eventName))
}

/**
 * 在 [ScopedFlowBus] 上读取指定粘性事件的最新 replay 值，并清空该 sticky replay 缓存。
 */
inline fun <reified T : Any> ScopedFlowBus.consumeStickyLatest(eventName: String = defaultEventName<T>()): T? {
    return consumeStickyLatest(eventKey<T>(name = eventName))
}

/**
 * 在 [FlowBusScope] 上以默认事件名尝试发送普通事件。
 */
inline fun <reified T : Any> FlowBusScope.post(value: T, eventName: String = defaultEventName<T>()): Boolean {
    return post(key = eventKey<T>(name = eventName), value = value)
}

/**
 * 在 [FlowBusScope] 上以默认事件名尝试发送普通事件，并返回总线层面的诊断结果。
 */
inline fun <reified T : Any> FlowBusScope.tryPostResult(
    value: T,
    eventName: String = defaultEventName<T>()
): FlowBusPostResult {
    return tryPostResult(key = eventKey<T>(name = eventName), value = value)
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
 * 在 [FlowBusScope] 上以默认事件名尝试发送粘性事件，并返回总线层面的诊断结果。
 */
inline fun <reified T : Any> FlowBusScope.tryPostStickyResult(
    value: T,
    eventName: String = defaultEventName<T>()
): FlowBusPostResult {
    return tryPostStickyResult(key = eventKey<T>(name = eventName), value = value)
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
 * 在 [FlowBusScope] 上以默认事件名顺序收集普通事件，并自动使用所属 bus 的错误处理配置。
 */
suspend inline fun <reified T : Any> FlowBusScope.collect(
    eventName: String = defaultEventName<T>(),
    dispatcher: CoroutineDispatcher? = null,
    noinline onReceived: (T) -> Unit
) {
    collect(key = eventKey<T>(name = eventName), dispatcher = dispatcher, onReceived = onReceived)
}

/**
 * 在 [FlowBusScope] 上以默认事件名顺序收集粘性事件，并自动使用所属 bus 的错误处理配置。
 */
suspend inline fun <reified T : Any> FlowBusScope.collectSticky(
    eventName: String = defaultEventName<T>(),
    dispatcher: CoroutineDispatcher? = null,
    noinline onReceived: (T) -> Unit
) {
    collectSticky(key = eventKey<T>(name = eventName), dispatcher = dispatcher, onReceived = onReceived)
}

/**
 * 在 [FlowBusScope] 上移除指定普通事件的当前 store 条目。
 */
inline fun <reified T : Any> FlowBusScope.removeEvent(eventName: String = defaultEventName<T>()) {
    removeEvent(eventKey<T>(name = eventName))
}

/**
 * 在 [FlowBusScope] 上清空指定粘性事件的 replay 缓存。
 */
inline fun <reified T : Any> FlowBusScope.clearSticky(eventName: String = defaultEventName<T>()) {
    clearSticky(eventKey<T>(name = eventName))
}

/**
 * 在 [FlowBusScope] 上移除指定粘性事件的当前 store 条目，并清空现有 replay 缓存。
 */
inline fun <reified T : Any> FlowBusScope.removeSticky(eventName: String = defaultEventName<T>()) {
    removeSticky(eventKey<T>(name = eventName))
}

/**
 * 在 [FlowBusScope] 上读取指定粘性事件的最新 replay 值，并清空该 sticky replay 缓存。
 */
inline fun <reified T : Any> FlowBusScope.consumeStickyLatest(eventName: String = defaultEventName<T>()): T? {
    return consumeStickyLatest(eventKey<T>(name = eventName))
}
