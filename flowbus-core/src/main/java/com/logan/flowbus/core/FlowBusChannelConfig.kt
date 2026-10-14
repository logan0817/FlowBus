package com.logan.flowbus.core

import kotlinx.coroutines.channels.BufferOverflow

/**
 * 单个通道的缓冲与溢出策略；null 字段继承所属总线的默认配置。
 *
 * 在首次发送或取流前调用 configureChannel。普通与 sticky 策略一次绑定，之后只能重复
 * 配置相同的有效值；移除单条流不会解除绑定，整个 scope store 清理后才会释放。
 * logger 和 errorHandler 仍由所属总线管理。SUSPEND 也不保证离线留存或业务处理成功。
 */
data class FlowBusChannelConfig(
    val normalBufferCapacity: Int? = null,
    val normalOverflowPolicy: BufferOverflow? = null,
    val stickyReplay: Int? = null,
    val stickyExtraBufferCapacity: Int? = null,
    val stickyOverflowPolicy: BufferOverflow? = null
)

internal data class ResolvedFlowBusChannelConfig(
    val normalBufferCapacity: Int,
    val normalOverflowPolicy: BufferOverflow,
    val stickyReplay: Int,
    val stickyExtraBufferCapacity: Int,
    val stickyOverflowPolicy: BufferOverflow
)

internal fun FlowBusChannelConfig.resolve(defaults: FlowBusConfig): ResolvedFlowBusChannelConfig {
    val resolved = ResolvedFlowBusChannelConfig(
        normalBufferCapacity = normalBufferCapacity ?: defaults.normalBufferCapacity,
        normalOverflowPolicy = normalOverflowPolicy ?: defaults.overflowPolicy,
        stickyReplay = stickyReplay ?: defaults.stickyReplay,
        stickyExtraBufferCapacity = stickyExtraBufferCapacity ?: defaults.stickyExtraBufferCapacity,
        stickyOverflowPolicy = stickyOverflowPolicy ?: defaults.overflowPolicy
    )
    require(resolved.normalBufferCapacity >= 0) { "normalBufferCapacity must be >= 0" }
    require(resolved.stickyReplay >= 0) { "stickyReplay must be >= 0" }
    require(resolved.stickyExtraBufferCapacity >= 0) { "stickyExtraBufferCapacity must be >= 0" }
    require(resolved.normalBufferCapacity > 0 || resolved.normalOverflowPolicy == BufferOverflow.SUSPEND) {
        "normalBufferCapacity must be > 0 when normalOverflowPolicy is not SUSPEND."
    }
    val stickyCapacity = resolved.stickyReplay.toLong() + resolved.stickyExtraBufferCapacity
    require(stickyCapacity <= Int.MAX_VALUE) { "stickyReplay + stickyExtraBufferCapacity must not overflow Int." }
    require(stickyCapacity > 0 || resolved.stickyOverflowPolicy == BufferOverflow.SUSPEND) {
        "stickyReplay or stickyExtraBufferCapacity must be > 0 when stickyOverflowPolicy is not SUSPEND."
    }
    return resolved
}
