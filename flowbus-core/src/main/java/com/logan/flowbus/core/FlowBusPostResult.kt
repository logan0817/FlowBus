package com.logan.flowbus.core

import kotlinx.coroutines.channels.BufferOverflow

/**
 * 一次非挂起发送的诊断结果。
 *
 * [accepted] 只表示本次 `tryEmit` 没有被底层流拒绝，不表示业务订阅者已经处理成功。
 */
data class FlowBusPostResult(
    val eventName: String,
    val scopeName: String?,
    val isSticky: Boolean,
    val accepted: Boolean,
    val outcome: FlowBusPostOutcome,
    val subscriptionCount: Int,
    val stickyReplayCount: Int,
    val overflowPolicy: BufferOverflow
)

/**
 * 非挂起发送在总线层面的结果分类。
 */
enum class FlowBusPostOutcome {
    /** 事件已被底层流接收，且发送瞬间至少存在一个活跃订阅者。 */
    Accepted,
    /** 事件已被底层流接收，但发送瞬间没有活跃订阅者。 */
    AcceptedWithoutActiveSubscriber,
    /** 事件已被底层流接收；当前配置允许在缓冲满时丢弃最旧事件。 */
    AcceptedWithDropOldestPolicy,
    /** 本次 `tryEmit` 未被拒绝；当前配置允许在缓冲满时丢弃最新值，因此当前值仍可能被丢弃。 */
    AcceptedWithDropLatestPolicy,
    /** 事件未被底层流接收，通常是无缓冲或挂起策略下存在慢订阅者。 */
    RejectedByBuffer
}
