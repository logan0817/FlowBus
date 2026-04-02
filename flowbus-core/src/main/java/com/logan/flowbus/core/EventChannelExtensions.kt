package com.logan.flowbus.core

/**
 * 向 [DefaultFlowBus] 的默认根总线发送普通事件。
 */
fun <T : Any> EventChannel<T>.post(value: T): Boolean {
    return DefaultFlowBus.raw().post(asEventKey(), value)
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
 * 清空 [DefaultFlowBus] 默认根总线上该事件的 sticky replay 缓存。
 */
fun <T : Any> EventChannel<T>.clearSticky() {
    DefaultFlowBus.raw().clearSticky(asEventKey())
}

/**
 * 从 [DefaultFlowBus] 默认根总线上彻底移除该 sticky 事件。
 */
fun <T : Any> EventChannel<T>.removeSticky() {
    DefaultFlowBus.raw().removeSticky(asEventKey())
}

/**
 * 在指定 [FlowBus] 上发送普通事件。
 */
fun <T : Any> EventChannel<T>.postOn(flowBus: FlowBus, value: T): Boolean {
    return flowBus.post(asEventKey(), value)
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
 * 清空指定 [FlowBus] 上该事件的 sticky replay 缓存。
 */
fun <T : Any> EventChannel<T>.clearStickyOn(flowBus: FlowBus) {
    flowBus.clearSticky(asEventKey())
}

/**
 * 从指定 [FlowBus] 上彻底移除该 sticky 事件。
 */
fun <T : Any> EventChannel<T>.removeStickyOn(flowBus: FlowBus) {
    flowBus.removeSticky(asEventKey())
}

/**
 * 在指定 [ScopedFlowBus] 上发送普通事件。
 */
fun <T : Any> EventChannel<T>.postOn(scopedFlowBus: ScopedFlowBus, value: T): Boolean {
    return scopedFlowBus.post(asEventKey(), value)
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
 * 清空指定 [ScopedFlowBus] 上该事件的 sticky replay 缓存。
 */
fun <T : Any> EventChannel<T>.clearStickyOn(scopedFlowBus: ScopedFlowBus) {
    scopedFlowBus.clearSticky(asEventKey())
}

/**
 * 从指定 [ScopedFlowBus] 上彻底移除该 sticky 事件。
 */
fun <T : Any> EventChannel<T>.removeStickyOn(scopedFlowBus: ScopedFlowBus) {
    scopedFlowBus.removeSticky(asEventKey())
}

/**
 * 在指定 [FlowBusScope] 上发送普通事件。
 */
fun <T : Any> EventChannel<T>.postOn(flowBusScope: FlowBusScope, value: T): Boolean {
    return flowBusScope.post(asEventKey(), value)
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
 * 清空指定 [FlowBusScope] 上该事件的 sticky replay 缓存。
 */
fun <T : Any> EventChannel<T>.clearStickyOn(flowBusScope: FlowBusScope) {
    flowBusScope.clearSticky(asEventKey())
}

/**
 * 从指定 [FlowBusScope] 上彻底移除该 sticky 事件。
 */
fun <T : Any> EventChannel<T>.removeStickyOn(flowBusScope: FlowBusScope) {
    flowBusScope.removeSticky(asEventKey())
}
