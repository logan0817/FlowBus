package com.logan.flowbus.core

/**
 * 直接将当前对象发送到 [DefaultFlowBus] 的默认事件通道。
 *
 * 当你希望用“事件对象自己会发自己”的写法时，这个扩展会比
 * `DefaultFlowBus.post(value = ...)` 更轻量。
 */
inline fun <reified T : Any> T.send(eventName: String = defaultEventName<T>()): Boolean {
    return DefaultFlowBus.post(value = this, eventName = eventName)
}

/**
 * 挂起直到当前对象成功发送到 [DefaultFlowBus] 的默认事件通道。
 */
suspend inline fun <reified T : Any> T.awaitSend(eventName: String = defaultEventName<T>()) {
    DefaultFlowBus.emit(value = this, eventName = eventName)
}

/**
 * 直接将当前对象作为粘性事件发送到 [DefaultFlowBus]。
 */
inline fun <reified T : Any> T.sendSticky(eventName: String = defaultEventName<T>()): Boolean {
    return DefaultFlowBus.postSticky(value = this, eventName = eventName)
}

/**
 * 挂起直到当前对象作为粘性事件成功发送到 [DefaultFlowBus]。
 */
suspend inline fun <reified T : Any> T.awaitSendSticky(eventName: String = defaultEventName<T>()) {
    DefaultFlowBus.emitSticky(value = this, eventName = eventName)
}

/**
 * 直接将当前对象发送到指定 [FlowBus]。
 */
inline fun <reified T : Any> T.sendOn(
    flowBus: FlowBus,
    eventName: String = defaultEventName<T>()
): Boolean {
    return flowBus.post(value = this, eventName = eventName)
}

/**
 * 挂起直到当前对象成功发送到指定 [FlowBus]。
 */
suspend inline fun <reified T : Any> T.awaitSendOn(
    flowBus: FlowBus,
    eventName: String = defaultEventName<T>()
) {
    flowBus.emit(value = this, eventName = eventName)
}

/**
 * 直接将当前对象作为粘性事件发送到指定 [FlowBus]。
 */
inline fun <reified T : Any> T.sendStickyOn(
    flowBus: FlowBus,
    eventName: String = defaultEventName<T>()
): Boolean {
    return flowBus.postSticky(value = this, eventName = eventName)
}

/**
 * 挂起直到当前对象作为粘性事件成功发送到指定 [FlowBus]。
 */
suspend inline fun <reified T : Any> T.awaitSendStickyOn(
    flowBus: FlowBus,
    eventName: String = defaultEventName<T>()
) {
    flowBus.emitSticky(value = this, eventName = eventName)
}

/**
 * 直接将当前对象发送到指定 [ScopedFlowBus]。
 */
inline fun <reified T : Any> T.sendOn(
    scopedFlowBus: ScopedFlowBus,
    eventName: String = defaultEventName<T>()
): Boolean {
    return scopedFlowBus.post(value = this, eventName = eventName)
}

/**
 * 挂起直到当前对象成功发送到指定 [ScopedFlowBus]。
 */
suspend inline fun <reified T : Any> T.awaitSendOn(
    scopedFlowBus: ScopedFlowBus,
    eventName: String = defaultEventName<T>()
) {
    scopedFlowBus.emit(value = this, eventName = eventName)
}

/**
 * 直接将当前对象作为粘性事件发送到指定 [ScopedFlowBus]。
 */
inline fun <reified T : Any> T.sendStickyOn(
    scopedFlowBus: ScopedFlowBus,
    eventName: String = defaultEventName<T>()
): Boolean {
    return scopedFlowBus.postSticky(value = this, eventName = eventName)
}

/**
 * 挂起直到当前对象作为粘性事件成功发送到指定 [ScopedFlowBus]。
 */
suspend inline fun <reified T : Any> T.awaitSendStickyOn(
    scopedFlowBus: ScopedFlowBus,
    eventName: String = defaultEventName<T>()
) {
    scopedFlowBus.emitSticky(value = this, eventName = eventName)
}

/**
 * 直接将当前对象发送到指定 [FlowBusScope]。
 */
inline fun <reified T : Any> T.sendOn(
    flowBusScope: FlowBusScope,
    eventName: String = defaultEventName<T>()
): Boolean {
    return flowBusScope.post(value = this, eventName = eventName)
}

/**
 * 挂起直到当前对象成功发送到指定 [FlowBusScope]。
 */
suspend inline fun <reified T : Any> T.awaitSendOn(
    flowBusScope: FlowBusScope,
    eventName: String = defaultEventName<T>()
) {
    flowBusScope.emit(value = this, eventName = eventName)
}

/**
 * 直接将当前对象作为粘性事件发送到指定 [FlowBusScope]。
 */
inline fun <reified T : Any> T.sendStickyOn(
    flowBusScope: FlowBusScope,
    eventName: String = defaultEventName<T>()
): Boolean {
    return flowBusScope.postSticky(value = this, eventName = eventName)
}

/**
 * 挂起直到当前对象作为粘性事件成功发送到指定 [FlowBusScope]。
 */
suspend inline fun <reified T : Any> T.awaitSendStickyOn(
    flowBusScope: FlowBusScope,
    eventName: String = defaultEventName<T>()
) {
    flowBusScope.emitSticky(value = this, eventName = eventName)
}
