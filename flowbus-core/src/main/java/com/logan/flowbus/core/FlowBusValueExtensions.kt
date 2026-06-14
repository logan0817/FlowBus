package com.logan.flowbus.core

/*
 * 这组扩展适合“以事件值为主语”来写发送代码的场景。
 *
 * 和 [FlowBusDefaultExtensions] 相比，它更适合把发送动作写短；
 * 和 [EventChannel] 相比，它不负责把通道定义成稳定对象，更适合局部一次性调用点。
 */
/**
 * 直接将当前对象发送到 [DefaultFlowBus] 的默认事件通道。
 *
 * 当你希望用“事件对象自己会发自己”的写法时，这个扩展会比
 * `DefaultFlowBus.post(value = ...)` 更轻量。
 * 行为上与 `DefaultFlowBus.post(...)` 一致：立即尝试写入，失败时返回 `false`。
 * 如果你需要显式指定目标 bus / scope，请改用 [sendOn]。
 */
inline fun <reified T : Any> T.send(eventName: String = defaultEventName<T>()): Boolean {
    return DefaultFlowBus.post(value = this, eventName = eventName)
}

/**
 * 挂起直到当前对象成功发送到 [DefaultFlowBus] 的默认事件通道。
 *
 * 行为上与 `DefaultFlowBus.emit(...)` 一致：遵循背压，直到写入成功才返回。
 * 如果你需要显式指定目标 bus / scope，请改用 [awaitSendOn]。
 */
suspend inline fun <reified T : Any> T.awaitSend(eventName: String = defaultEventName<T>()) {
    DefaultFlowBus.emit(value = this, eventName = eventName)
}

/**
 * 直接将当前对象作为粘性事件发送到 [DefaultFlowBus]。
 *
 * 行为上与 `DefaultFlowBus.postSticky(...)` 一致：立即尝试写入，失败时返回 `false`。
 */
inline fun <reified T : Any> T.sendSticky(eventName: String = defaultEventName<T>()): Boolean {
    return DefaultFlowBus.postSticky(value = this, eventName = eventName)
}

/**
 * 挂起直到当前对象作为粘性事件成功发送到 [DefaultFlowBus]。
 *
 * 行为上与 `DefaultFlowBus.emitSticky(...)` 一致：遵循背压，直到写入成功才返回。
 */
suspend inline fun <reified T : Any> T.awaitSendSticky(eventName: String = defaultEventName<T>()) {
    DefaultFlowBus.emitSticky(value = this, eventName = eventName)
}

/**
 * 直接将当前对象发送到指定 [FlowBus]。
 *
 * 这里不会落到默认单例，而是只会进入你传入的这个 [FlowBus] 实例。
 */
inline fun <reified T : Any> T.sendOn(
    flowBus: FlowBus,
    eventName: String = defaultEventName<T>()
): Boolean {
    return flowBus.post(value = this, eventName = eventName)
}

/**
 * 挂起直到当前对象成功发送到指定 [FlowBus]。
 *
 * 这里不会落到默认单例，而是只会进入你传入的这个 [FlowBus] 实例。
 */
suspend inline fun <reified T : Any> T.awaitSendOn(
    flowBus: FlowBus,
    eventName: String = defaultEventName<T>()
) {
    flowBus.emit(value = this, eventName = eventName)
}

/**
 * 直接将当前对象作为粘性事件发送到指定 [FlowBus]。
 *
 * 这里不会落到默认单例，而是只会进入你传入的这个 [FlowBus] 实例的 sticky 通道。
 */
inline fun <reified T : Any> T.sendStickyOn(
    flowBus: FlowBus,
    eventName: String = defaultEventName<T>()
): Boolean {
    return flowBus.postSticky(value = this, eventName = eventName)
}

/**
 * 挂起直到当前对象作为粘性事件成功发送到指定 [FlowBus]。
 *
 * 这里不会落到默认单例，而是只会进入你传入的这个 [FlowBus] 实例的 sticky 通道。
 */
suspend inline fun <reified T : Any> T.awaitSendStickyOn(
    flowBus: FlowBus,
    eventName: String = defaultEventName<T>()
) {
    flowBus.emitSticky(value = this, eventName = eventName)
}

/**
 * 直接将当前对象发送到指定 [ScopedFlowBus]。
 *
 * 这条调用只会进入当前命名 scope，对根总线和其他 scope 都不生效。
 */
inline fun <reified T : Any> T.sendOn(
    scopedFlowBus: ScopedFlowBus,
    eventName: String = defaultEventName<T>()
): Boolean {
    return scopedFlowBus.post(value = this, eventName = eventName)
}

/**
 * 挂起直到当前对象成功发送到指定 [ScopedFlowBus]。
 *
 * 这条调用只会进入当前命名 scope，对根总线和其他 scope 都不生效。
 */
suspend inline fun <reified T : Any> T.awaitSendOn(
    scopedFlowBus: ScopedFlowBus,
    eventName: String = defaultEventName<T>()
) {
    scopedFlowBus.emit(value = this, eventName = eventName)
}

/**
 * 直接将当前对象作为粘性事件发送到指定 [ScopedFlowBus]。
 *
 * 这条调用只会进入当前命名 scope 的 sticky 通道。
 */
inline fun <reified T : Any> T.sendStickyOn(
    scopedFlowBus: ScopedFlowBus,
    eventName: String = defaultEventName<T>()
): Boolean {
    return scopedFlowBus.postSticky(value = this, eventName = eventName)
}

/**
 * 挂起直到当前对象作为粘性事件成功发送到指定 [ScopedFlowBus]。
 *
 * 这条调用只会进入当前命名 scope 的 sticky 通道。
 */
suspend inline fun <reified T : Any> T.awaitSendStickyOn(
    scopedFlowBus: ScopedFlowBus,
    eventName: String = defaultEventName<T>()
) {
    scopedFlowBus.emitSticky(value = this, eventName = eventName)
}

/**
 * 直接将当前对象发送到指定 [FlowBusScope]。
 *
 * 这条调用只会进入该 scope 句柄当前绑定的 scope；如果句柄已关闭，会直接失败。
 */
inline fun <reified T : Any> T.sendOn(
    flowBusScope: FlowBusScope,
    eventName: String = defaultEventName<T>()
): Boolean {
    return flowBusScope.post(value = this, eventName = eventName)
}

/**
 * 挂起直到当前对象成功发送到指定 [FlowBusScope]。
 *
 * 这条调用只会进入该 scope 句柄当前绑定的 scope；如果句柄已关闭，会直接失败。
 */
suspend inline fun <reified T : Any> T.awaitSendOn(
    flowBusScope: FlowBusScope,
    eventName: String = defaultEventName<T>()
) {
    flowBusScope.emit(value = this, eventName = eventName)
}

/**
 * 直接将当前对象作为粘性事件发送到指定 [FlowBusScope]。
 *
 * 这条调用只会进入该 scope 句柄当前绑定的 sticky 通道；如果句柄已关闭，会直接失败。
 */
inline fun <reified T : Any> T.sendStickyOn(
    flowBusScope: FlowBusScope,
    eventName: String = defaultEventName<T>()
): Boolean {
    return flowBusScope.postSticky(value = this, eventName = eventName)
}

/**
 * 挂起直到当前对象作为粘性事件成功发送到指定 [FlowBusScope]。
 *
 * 这条调用只会进入该 scope 句柄当前绑定的 sticky 通道；如果句柄已关闭，会直接失败。
 */
suspend inline fun <reified T : Any> T.awaitSendStickyOn(
    flowBusScope: FlowBusScope,
    eventName: String = defaultEventName<T>()
) {
    flowBusScope.emitSticky(value = this, eventName = eventName)
}
