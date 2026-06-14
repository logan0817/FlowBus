package com.logan.flowbus.core

/**
 * FlowBus 订阅失败时的处理策略。
 *
 * 这里处理的不只是回调里抛出的异常，也包括事件值与期望类型不匹配的情况。
 * 如果你想区分到底是哪一类失败，请结合 [FlowBusErrorContext.phase] 来判断。
 */
fun interface FlowBusErrorHandler {
    /**
     * 处理订阅失败，并附带当前事件上下文。
     */
    fun handle(context: FlowBusErrorContext, throwable: Throwable)

    companion object {
        /** 默认策略：继续向外抛出异常，让调用方尽快看到问题。 */
        val Rethrow = FlowBusErrorHandler { _, throwable -> throw throwable }

        /** 忽略异常，仅保留日志。 */
        val Ignore = FlowBusErrorHandler { _, _ -> }

        /**
         * 兼容旧版只接收 [Throwable] 的处理器。
         *
         * 如果你还需要事件名、失败阶段、scopeName 等上下文，请直接实现 [handle]。
         */
        fun fromThrowableHandler(handler: (Throwable) -> Unit): FlowBusErrorHandler {
            return FlowBusErrorHandler { _, throwable -> handler(throwable) }
        }
    }
}
