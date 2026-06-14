package com.logan.flowbus.core

/**
 * [FlowBusScope] 关闭动作的结果。
 */
data class FlowBusCloseResult(
    val scopeName: String,
    val closed: Boolean,
    val outcome: FlowBusCloseOutcome,
    val inFlightOperationCount: Int
)

/**
 * [FlowBusScope] 关闭动作的总线层结果分类。
 */
enum class FlowBusCloseOutcome {
    /** 本次调用完成了关闭和资源清理。 */
    Closed,
    /** 调用前 scope 已经关闭。 */
    AlreadyClosed,
    /** 另一个关闭动作正在进行中，本次调用没有重复等待或抢占。 */
    ClosingInProgress,
    /** 等待在途操作结束时超时，scope 仍保持打开。 */
    Timeout
}
