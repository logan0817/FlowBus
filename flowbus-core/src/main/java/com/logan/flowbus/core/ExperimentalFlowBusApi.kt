package com.logan.flowbus.core

/**
 * 标记尚未稳定的 FlowBus API。
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.WARNING,
    message = "This FlowBus API is experimental and may change before it becomes stable."
)
annotation class ExperimentalFlowBusApi
