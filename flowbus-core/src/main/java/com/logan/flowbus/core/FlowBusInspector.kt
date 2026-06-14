package com.logan.flowbus.core

import kotlinx.coroutines.channels.BufferOverflow

/**
 * FlowBus 的只读诊断入口。
 *
 * 诊断快照只暴露事件元数据，不暴露 sticky replay 中的业务 payload。快照是读取瞬间的
 * 排查视图，不是跨线程一致性事务结果；不要用它驱动正式业务分支。
 */
class FlowBusInspector internal constructor(
    private val snapshotProvider: () -> FlowBusSnapshot
) {
    /** 返回当前总线的只读快照。 */
    fun snapshot(): FlowBusSnapshot {
        return snapshotProvider()
    }
}

/** FlowBus 当前状态的只读快照。 */
data class FlowBusSnapshot(
    val config: FlowBusConfigSnapshot,
    val root: FlowBusScopeSnapshot,
    val scopes: List<FlowBusScopeSnapshot>
)

/** FlowBus 配置摘要，不包含 logger / errorHandler 实例。 */
data class FlowBusConfigSnapshot(
    val normalBufferCapacity: Int,
    val stickyReplay: Int,
    val stickyExtraBufferCapacity: Int,
    val overflowPolicy: BufferOverflow
)

/** root 或 scoped bus 的事件元数据快照；[scopeName] 可能来自业务命名，写日志前请按需脱敏。 */
data class FlowBusScopeSnapshot(
    val scopeName: String?,
    val events: List<FlowBusEventSnapshot>,
    val hasOpenScopeHandle: Boolean = false,
    val isClosed: Boolean = false
) {
    constructor(
        scopeName: String?,
        events: List<FlowBusEventSnapshot>
    ) : this(
        scopeName = scopeName,
        events = events,
        hasOpenScopeHandle = false,
        isClosed = false
    )
}

/** 单个事件名在普通流和 sticky 流上的诊断信息；[eventName] 可能来自业务命名，写日志前请按需脱敏。 */
data class FlowBusEventSnapshot(
    val eventName: String,
    val valueTypeName: String?,
    val hasNormalFlow: Boolean,
    val hasStickyFlow: Boolean,
    val stickyReplayCount: Int,
    val subscriptionCount: Int = 0,
    val metrics: FlowBusEventMetricsSnapshot = emptyFlowBusEventMetricsSnapshot(),
    val normalMetrics: FlowBusEventMetricsSnapshot = emptyFlowBusEventMetricsSnapshot(),
    val stickyMetrics: FlowBusEventMetricsSnapshot = emptyFlowBusEventMetricsSnapshot()
) {
    constructor(
        eventName: String,
        valueTypeName: String?,
        hasNormalFlow: Boolean,
        hasStickyFlow: Boolean,
        stickyReplayCount: Int
    ) : this(
        eventName = eventName,
        valueTypeName = valueTypeName,
        hasNormalFlow = hasNormalFlow,
        hasStickyFlow = hasStickyFlow,
        stickyReplayCount = stickyReplayCount,
        subscriptionCount = 0,
        metrics = emptyFlowBusEventMetricsSnapshot(),
        normalMetrics = emptyFlowBusEventMetricsSnapshot(),
        stickyMetrics = emptyFlowBusEventMetricsSnapshot()
    )
}

/** 单个事件名的发送诊断计数；这些计数只描述总线接收情况，不描述业务处理成功。 */
data class FlowBusEventMetricsSnapshot(
    val acceptedPostCount: Long,
    val rejectedPostCount: Long,
    val lastPostTimeMillis: Long?,
    val lastRejectedReason: FlowBusPostOutcome?
)

internal fun FlowBusConfig.toSnapshot(): FlowBusConfigSnapshot {
    return FlowBusConfigSnapshot(
        normalBufferCapacity = normalBufferCapacity,
        stickyReplay = stickyReplay,
        stickyExtraBufferCapacity = stickyExtraBufferCapacity,
        overflowPolicy = overflowPolicy
    )
}
