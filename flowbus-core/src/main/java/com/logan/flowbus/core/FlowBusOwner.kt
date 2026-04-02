package com.logan.flowbus.core

/**
 * 提供 FlowBus scoped bus 身份的最小接口。
 */
interface FlowBusOwner {
    /** scoped bus 的唯一名称。 */
    val busScopeName: String
}

/**
 * 基于字符串作用域名的默认 [FlowBusOwner] 实现。
 */
data class NamedFlowBusOwner(
    override val busScopeName: String
) : FlowBusOwner

/**
 * 通过字符串创建一个 [FlowBusOwner]。
 */
fun flowBusOwner(scopeName: String): FlowBusOwner {
    require(scopeName.isNotBlank()) { "scopeName must not be blank" }
    return NamedFlowBusOwner(scopeName)
}
