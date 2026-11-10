package com.logan.flowbus.core

import kotlinx.coroutines.channels.BufferOverflow

/**
 * 一个已绑定通道的有效配置和分流订阅数，不包含业务 payload。
 *
 * 快照不创建通道或 Flow，不是跨线程一致性事务，也不能用作某次订阅的就绪屏障。
 * eventName 可能来自业务命名，写日志前请按需脱敏。
 */
data class FlowBusChannelSnapshot(
    val eventName: String,
    val valueTypeName: String?,
    val normalBufferCapacity: Int,
    val normalOverflowPolicy: BufferOverflow,
    val stickyReplay: Int,
    val stickyExtraBufferCapacity: Int,
    val stickyOverflowPolicy: BufferOverflow,
    val hasNormalFlow: Boolean,
    val hasStickyFlow: Boolean,
    val normalSubscriptionCount: Int,
    val stickySubscriptionCount: Int
)
