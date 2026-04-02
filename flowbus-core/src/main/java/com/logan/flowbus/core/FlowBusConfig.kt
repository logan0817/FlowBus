package com.logan.flowbus.core

import kotlinx.coroutines.channels.BufferOverflow

/**
 * `FlowBus` 的基础配置。
 *
 * 普通事件和粘性事件共用这一套缓冲、日志与错误处理策略。
 */
data class FlowBusConfig(
    /** 普通事件流的额外缓冲容量。 */
    val normalBufferCapacity: Int = 64,
    /** 粘性事件流的 replay 数量，默认只保留最新一条。 */
    val stickyReplay: Int = 1,
    /** 粘性事件流的额外缓冲容量。 */
    val stickyExtraBufferCapacity: Int = 0,
    /** 缓冲区满时的溢出策略。 */
    val overflowPolicy: BufferOverflow = BufferOverflow.DROP_OLDEST,
    /** 订阅回调异常或类型不匹配时的日志接口。 */
    val logger: FlowBusLogger = FlowBusLogger.None,
    /** 订阅回调异常或类型不匹配时的处理策略。 */
    val errorHandler: FlowBusErrorHandler = FlowBusErrorHandler.Rethrow
)
