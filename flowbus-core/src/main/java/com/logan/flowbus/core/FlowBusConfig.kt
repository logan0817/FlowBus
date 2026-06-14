package com.logan.flowbus.core

import kotlinx.coroutines.channels.BufferOverflow

/**
 * `FlowBus` 的基础配置。
 *
 * 普通事件和粘性事件共用这一套缓冲、日志与错误处理策略。
 * 如果你第一次接触 FlowBus，可以先只关心两件事：
 * - `normalBufferCapacity` / `stickyReplay` / `stickyExtraBufferCapacity` 会影响 `post` / `send` 这类非挂起发送的表现
 * - `errorHandler` 默认是 [FlowBusErrorHandler.Rethrow]，也就是订阅回调异常不会被静默吞掉
 *
 * 如果某类事件完全没有 replay 或额外缓冲，`overflowPolicy` 必须使用 [BufferOverflow.SUSPEND]。
 */
data class FlowBusConfig(
    /** 普通事件流的额外缓冲容量；设为 `0` 时，相当于没有额外缓冲。 */
    val normalBufferCapacity: Int = 64,
    /** 粘性事件流的 replay 数量，默认只保留最新一条。 */
    val stickyReplay: Int = 1,
    /** 粘性事件流的额外缓冲容量。 */
    val stickyExtraBufferCapacity: Int = 0,
    /** 缓冲区满时的溢出策略，会影响 `post` / `send` 这类立即尝试发送的结果。 */
    val overflowPolicy: BufferOverflow = BufferOverflow.DROP_OLDEST,
    /** 订阅回调异常或类型不匹配时的日志接口。 */
    val logger: FlowBusLogger = FlowBusLogger.None,
    /** 订阅回调异常或类型不匹配时的处理策略；默认会继续向外抛。 */
    val errorHandler: FlowBusErrorHandler = FlowBusErrorHandler.Rethrow
)
