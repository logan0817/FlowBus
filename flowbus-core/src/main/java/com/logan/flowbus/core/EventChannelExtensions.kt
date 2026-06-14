package com.logan.flowbus.core

import kotlinx.coroutines.CoroutineDispatcher

/*
 * [EventChannel] 这组扩展更适合“围绕同一个命名通道组织发送、订阅和 sticky 清理”的场景。
 *
 * 如果你只是局部一次性发送一个事件对象，值糖 API 通常更轻；
 * 如果你希望这个通道在多个文件里复用，或者要把通道本身作为公开语义暴露出来，
 * [EventChannel] 会更稳定、更不容易把字符串写散。
 */
/**
 * 向 [DefaultFlowBus] 的默认根总线发送普通事件。
 */
fun <T : Any> EventChannel<T>.post(value: T): Boolean {
    return DefaultFlowBus.raw().post(asEventKey(), value)
}

/**
 * 向 [DefaultFlowBus] 的默认根总线发送普通事件，并返回总线层面的诊断结果。
 */
fun <T : Any> EventChannel<T>.tryPostResult(value: T): FlowBusPostResult {
    return DefaultFlowBus.raw().tryPostResult(asEventKey(), value)
}

/**
 * 挂起直到普通事件成功发送到 [DefaultFlowBus] 的默认根总线。
 */
suspend fun <T : Any> EventChannel<T>.emit(value: T) {
    DefaultFlowBus.raw().emit(asEventKey(), value)
}

/**
 * 向 [DefaultFlowBus] 的默认根总线发送粘性事件。
 */
fun <T : Any> EventChannel<T>.postSticky(value: T): Boolean {
    return DefaultFlowBus.raw().postSticky(asEventKey(), value)
}

/**
 * 向 [DefaultFlowBus] 的默认根总线发送粘性事件，并返回总线层面的诊断结果。
 */
fun <T : Any> EventChannel<T>.tryPostStickyResult(value: T): FlowBusPostResult {
    return DefaultFlowBus.raw().tryPostStickyResult(asEventKey(), value)
}

/**
 * 挂起直到粘性事件成功发送到 [DefaultFlowBus] 的默认根总线。
 */
suspend fun <T : Any> EventChannel<T>.emitSticky(value: T) {
    DefaultFlowBus.raw().emitSticky(asEventKey(), value)
}

/**
 * 返回 [DefaultFlowBus] 默认根总线上的普通事件流。
 */
fun <T : Any> EventChannel<T>.flow() = DefaultFlowBus.raw().flow(asEventKey())

/**
 * 返回 [DefaultFlowBus] 默认根总线上的粘性事件流。
 */
fun <T : Any> EventChannel<T>.stickyFlow() = DefaultFlowBus.raw().stickyFlow(asEventKey())

/**
 * 按顺序收集 [DefaultFlowBus] 默认根总线上的普通事件。
 */
suspend fun <T : Any> EventChannel<T>.collect(
    dispatcher: CoroutineDispatcher? = null,
    onReceived: (T) -> Unit
) {
    DefaultFlowBus.raw().collect(key = asEventKey(), dispatcher = dispatcher, onReceived = onReceived)
}

/**
 * 按顺序收集 [DefaultFlowBus] 默认根总线上的粘性事件。
 */
suspend fun <T : Any> EventChannel<T>.collectSticky(
    dispatcher: CoroutineDispatcher? = null,
    onReceived: (T) -> Unit
) {
    DefaultFlowBus.raw().collectSticky(key = asEventKey(), dispatcher = dispatcher, onReceived = onReceived)
}

/**
 * 从 [DefaultFlowBus] 默认根总线中移除该普通事件的当前 store 条目。
 */
fun <T : Any> EventChannel<T>.removeEvent() {
    DefaultFlowBus.raw().removeEvent(asEventKey())
}

/**
 * 清空 [DefaultFlowBus] 默认根总线上该事件的 sticky replay 缓存。
 */
fun <T : Any> EventChannel<T>.clearSticky() {
    DefaultFlowBus.raw().clearSticky(asEventKey())
}

/**
 * 从 [DefaultFlowBus] 默认根总线的当前 store 中移除该 sticky 事件，并清空现有 replay 缓存。
 */
fun <T : Any> EventChannel<T>.removeSticky() {
    DefaultFlowBus.raw().removeSticky(asEventKey())
}

/**
 * 读取 [DefaultFlowBus] 默认根总线上该 sticky 事件的最新 replay 值，并清空 replay 缓存。
 */
fun <T : Any> EventChannel<T>.consumeStickyLatest(): T? {
    return DefaultFlowBus.raw().consumeStickyLatest(asEventKey())
}

/**
 * 在指定 [FlowBus] 上发送普通事件。
 */
fun <T : Any> EventChannel<T>.postOn(flowBus: FlowBus, value: T): Boolean {
    return flowBus.post(asEventKey(), value)
}

/**
 * 在指定 [FlowBus] 上发送普通事件，并返回总线层面的诊断结果。
 */
fun <T : Any> EventChannel<T>.tryPostResultOn(flowBus: FlowBus, value: T): FlowBusPostResult {
    return flowBus.tryPostResult(asEventKey(), value)
}

/**
 * 在指定 [FlowBus] 上挂起发送普通事件。
 */
suspend fun <T : Any> EventChannel<T>.emitOn(flowBus: FlowBus, value: T) {
    flowBus.emit(asEventKey(), value)
}

/**
 * 在指定 [FlowBus] 上发送粘性事件。
 */
fun <T : Any> EventChannel<T>.postStickyOn(flowBus: FlowBus, value: T): Boolean {
    return flowBus.postSticky(asEventKey(), value)
}

/**
 * 在指定 [FlowBus] 上发送粘性事件，并返回总线层面的诊断结果。
 */
fun <T : Any> EventChannel<T>.tryPostStickyResultOn(flowBus: FlowBus, value: T): FlowBusPostResult {
    return flowBus.tryPostStickyResult(asEventKey(), value)
}

/**
 * 在指定 [FlowBus] 上挂起发送粘性事件。
 */
suspend fun <T : Any> EventChannel<T>.emitStickyOn(flowBus: FlowBus, value: T) {
    flowBus.emitSticky(asEventKey(), value)
}

/**
 * 返回指定 [FlowBus] 上的普通事件流。
 */
fun <T : Any> EventChannel<T>.flowOn(flowBus: FlowBus) = flowBus.flow(asEventKey())

/**
 * 返回指定 [FlowBus] 上的粘性事件流。
 */
fun <T : Any> EventChannel<T>.stickyFlowOn(flowBus: FlowBus) = flowBus.stickyFlow(asEventKey())

/**
 * 按顺序收集指定 [FlowBus] 上的普通事件。
 */
suspend fun <T : Any> EventChannel<T>.collectOn(
    flowBus: FlowBus,
    dispatcher: CoroutineDispatcher? = null,
    onReceived: (T) -> Unit
) {
    flowBus.collect(key = asEventKey(), dispatcher = dispatcher, onReceived = onReceived)
}

/**
 * 按顺序收集指定 [FlowBus] 上的粘性事件。
 */
suspend fun <T : Any> EventChannel<T>.collectStickyOn(
    flowBus: FlowBus,
    dispatcher: CoroutineDispatcher? = null,
    onReceived: (T) -> Unit
) {
    flowBus.collectSticky(key = asEventKey(), dispatcher = dispatcher, onReceived = onReceived)
}

/**
 * 从指定 [FlowBus] 中移除该普通事件的当前 store 条目。
 */
fun <T : Any> EventChannel<T>.removeEventOn(flowBus: FlowBus) {
    flowBus.removeEvent(asEventKey())
}

/**
 * 清空指定 [FlowBus] 上该事件的 sticky replay 缓存。
 */
fun <T : Any> EventChannel<T>.clearStickyOn(flowBus: FlowBus) {
    flowBus.clearSticky(asEventKey())
}

/**
 * 从指定 [FlowBus] 的当前 store 中移除该 sticky 事件，并清空现有 replay 缓存。
 */
fun <T : Any> EventChannel<T>.removeStickyOn(flowBus: FlowBus) {
    flowBus.removeSticky(asEventKey())
}

/**
 * 读取指定 [FlowBus] 上该 sticky 事件的最新 replay 值，并清空 replay 缓存。
 */
fun <T : Any> EventChannel<T>.consumeStickyLatestOn(flowBus: FlowBus): T? {
    return flowBus.consumeStickyLatest(asEventKey())
}

/**
 * 在指定 [ScopedFlowBus] 上发送普通事件。
 */
fun <T : Any> EventChannel<T>.postOn(scopedFlowBus: ScopedFlowBus, value: T): Boolean {
    return scopedFlowBus.post(asEventKey(), value)
}

/**
 * 在指定 [ScopedFlowBus] 上发送普通事件，并返回总线层面的诊断结果。
 */
fun <T : Any> EventChannel<T>.tryPostResultOn(scopedFlowBus: ScopedFlowBus, value: T): FlowBusPostResult {
    return scopedFlowBus.tryPostResult(asEventKey(), value)
}

/**
 * 在指定 [ScopedFlowBus] 上挂起发送普通事件。
 */
suspend fun <T : Any> EventChannel<T>.emitOn(scopedFlowBus: ScopedFlowBus, value: T) {
    scopedFlowBus.emit(asEventKey(), value)
}

/**
 * 在指定 [ScopedFlowBus] 上发送粘性事件。
 */
fun <T : Any> EventChannel<T>.postStickyOn(scopedFlowBus: ScopedFlowBus, value: T): Boolean {
    return scopedFlowBus.postSticky(asEventKey(), value)
}

/**
 * 在指定 [ScopedFlowBus] 上发送粘性事件，并返回总线层面的诊断结果。
 */
fun <T : Any> EventChannel<T>.tryPostStickyResultOn(scopedFlowBus: ScopedFlowBus, value: T): FlowBusPostResult {
    return scopedFlowBus.tryPostStickyResult(asEventKey(), value)
}

/**
 * 在指定 [ScopedFlowBus] 上挂起发送粘性事件。
 */
suspend fun <T : Any> EventChannel<T>.emitStickyOn(scopedFlowBus: ScopedFlowBus, value: T) {
    scopedFlowBus.emitSticky(asEventKey(), value)
}

/**
 * 返回指定 [ScopedFlowBus] 上的普通事件流。
 */
fun <T : Any> EventChannel<T>.flowOn(scopedFlowBus: ScopedFlowBus) = scopedFlowBus.flow(asEventKey())

/**
 * 返回指定 [ScopedFlowBus] 上的粘性事件流。
 */
fun <T : Any> EventChannel<T>.stickyFlowOn(scopedFlowBus: ScopedFlowBus) = scopedFlowBus.stickyFlow(asEventKey())

/**
 * 按顺序收集指定 [ScopedFlowBus] 上的普通事件。
 */
suspend fun <T : Any> EventChannel<T>.collectOn(
    scopedFlowBus: ScopedFlowBus,
    dispatcher: CoroutineDispatcher? = null,
    onReceived: (T) -> Unit
) {
    scopedFlowBus.collect(key = asEventKey(), dispatcher = dispatcher, onReceived = onReceived)
}

/**
 * 按顺序收集指定 [ScopedFlowBus] 上的粘性事件。
 */
suspend fun <T : Any> EventChannel<T>.collectStickyOn(
    scopedFlowBus: ScopedFlowBus,
    dispatcher: CoroutineDispatcher? = null,
    onReceived: (T) -> Unit
) {
    scopedFlowBus.collectSticky(key = asEventKey(), dispatcher = dispatcher, onReceived = onReceived)
}

/**
 * 从指定 [ScopedFlowBus] 中移除该普通事件的当前 store 条目。
 */
fun <T : Any> EventChannel<T>.removeEventOn(scopedFlowBus: ScopedFlowBus) {
    scopedFlowBus.removeEvent(asEventKey())
}

/**
 * 清空指定 [ScopedFlowBus] 上该事件的 sticky replay 缓存。
 */
fun <T : Any> EventChannel<T>.clearStickyOn(scopedFlowBus: ScopedFlowBus) {
    scopedFlowBus.clearSticky(asEventKey())
}

/**
 * 从指定 [ScopedFlowBus] 的当前 store 中移除该 sticky 事件，并清空现有 replay 缓存。
 */
fun <T : Any> EventChannel<T>.removeStickyOn(scopedFlowBus: ScopedFlowBus) {
    scopedFlowBus.removeSticky(asEventKey())
}

/**
 * 读取指定 [ScopedFlowBus] 上该 sticky 事件的最新 replay 值，并清空 replay 缓存。
 */
fun <T : Any> EventChannel<T>.consumeStickyLatestOn(scopedFlowBus: ScopedFlowBus): T? {
    return scopedFlowBus.consumeStickyLatest(asEventKey())
}

/**
 * 在指定 [FlowBusScope] 上发送普通事件。
 */
fun <T : Any> EventChannel<T>.postOn(flowBusScope: FlowBusScope, value: T): Boolean {
    return flowBusScope.post(asEventKey(), value)
}

/**
 * 在指定 [FlowBusScope] 上发送普通事件，并返回总线层面的诊断结果。
 */
fun <T : Any> EventChannel<T>.tryPostResultOn(flowBusScope: FlowBusScope, value: T): FlowBusPostResult {
    return flowBusScope.tryPostResult(asEventKey(), value)
}

/**
 * 在指定 [FlowBusScope] 上挂起发送普通事件。
 */
suspend fun <T : Any> EventChannel<T>.emitOn(flowBusScope: FlowBusScope, value: T) {
    flowBusScope.emit(asEventKey(), value)
}

/**
 * 在指定 [FlowBusScope] 上发送粘性事件。
 */
fun <T : Any> EventChannel<T>.postStickyOn(flowBusScope: FlowBusScope, value: T): Boolean {
    return flowBusScope.postSticky(asEventKey(), value)
}

/**
 * 在指定 [FlowBusScope] 上发送粘性事件，并返回总线层面的诊断结果。
 */
fun <T : Any> EventChannel<T>.tryPostStickyResultOn(flowBusScope: FlowBusScope, value: T): FlowBusPostResult {
    return flowBusScope.tryPostStickyResult(asEventKey(), value)
}

/**
 * 在指定 [FlowBusScope] 上挂起发送粘性事件。
 */
suspend fun <T : Any> EventChannel<T>.emitStickyOn(flowBusScope: FlowBusScope, value: T) {
    flowBusScope.emitSticky(asEventKey(), value)
}

/**
 * 返回指定 [FlowBusScope] 上的普通事件流。
 */
fun <T : Any> EventChannel<T>.flowOn(flowBusScope: FlowBusScope) = flowBusScope.flow(asEventKey())

/**
 * 返回指定 [FlowBusScope] 上的粘性事件流。
 */
fun <T : Any> EventChannel<T>.stickyFlowOn(flowBusScope: FlowBusScope) = flowBusScope.stickyFlow(asEventKey())

/**
 * 按顺序收集指定 [FlowBusScope] 上的普通事件。
 */
suspend fun <T : Any> EventChannel<T>.collectOn(
    flowBusScope: FlowBusScope,
    dispatcher: CoroutineDispatcher? = null,
    onReceived: (T) -> Unit
) {
    flowBusScope.collect(key = asEventKey(), dispatcher = dispatcher, onReceived = onReceived)
}

/**
 * 按顺序收集指定 [FlowBusScope] 上的粘性事件。
 */
suspend fun <T : Any> EventChannel<T>.collectStickyOn(
    flowBusScope: FlowBusScope,
    dispatcher: CoroutineDispatcher? = null,
    onReceived: (T) -> Unit
) {
    flowBusScope.collectSticky(key = asEventKey(), dispatcher = dispatcher, onReceived = onReceived)
}

/**
 * 从指定 [FlowBusScope] 中移除该普通事件的当前 store 条目。
 */
fun <T : Any> EventChannel<T>.removeEventOn(flowBusScope: FlowBusScope) {
    flowBusScope.removeEvent(asEventKey())
}

/**
 * 清空指定 [FlowBusScope] 上该事件的 sticky replay 缓存。
 */
fun <T : Any> EventChannel<T>.clearStickyOn(flowBusScope: FlowBusScope) {
    flowBusScope.clearSticky(asEventKey())
}

/**
 * 从指定 [FlowBusScope] 的当前 store 中移除该 sticky 事件，并清空现有 replay 缓存。
 */
fun <T : Any> EventChannel<T>.removeStickyOn(flowBusScope: FlowBusScope) {
    flowBusScope.removeSticky(asEventKey())
}

/**
 * 读取指定 [FlowBusScope] 上该 sticky 事件的最新 replay 值，并清空 replay 缓存。
 */
fun <T : Any> EventChannel<T>.consumeStickyLatestOn(flowBusScope: FlowBusScope): T? {
    return flowBusScope.consumeStickyLatest(asEventKey())
}
