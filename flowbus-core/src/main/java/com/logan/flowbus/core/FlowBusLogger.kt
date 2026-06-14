package com.logan.flowbus.core

/**
 * FlowBus 的最小日志接口。
 *
 * 它只负责记录告警，不参与异常分流；真正决定异常是否继续抛出的是 [FlowBusErrorHandler]。
 */
interface FlowBusLogger {
    /**
     * 记录告警日志。
     */
    fun warn(tag: String, message: String, throwable: Throwable? = null)

    /**
     * 默认空实现，不输出任何日志。
     */
    object None : FlowBusLogger {
        override fun warn(tag: String, message: String, throwable: Throwable?) = Unit
    }
}
