package com.logan.flowbus.core

/**
 * FlowBus 订阅回调异常时的处理策略。
 */
fun interface FlowBusErrorHandler {
    /**
     * 处理订阅回调中的异常，并附带当前事件上下文。
     */
    fun handle(context: FlowBusErrorContext, throwable: Throwable)

    companion object {
        /** 默认策略：继续向外抛出异常。 */
        val Rethrow = FlowBusErrorHandler { _, throwable -> throw throwable }

        /** 忽略异常，仅保留日志。 */
        val Ignore = FlowBusErrorHandler { _, _ -> }

        /**
         * 兼容旧版只接收 [Throwable] 的处理器。
         */
        fun fromThrowableHandler(handler: (Throwable) -> Unit): FlowBusErrorHandler {
            return FlowBusErrorHandler { _, throwable -> handler(throwable) }
        }
    }
}
