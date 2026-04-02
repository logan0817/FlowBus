package com.logan.flowbus.core

import kotlin.reflect.KClass

/**
 * FlowBus 订阅失败时的上下文信息。
 *
 * 该模型用于描述：
 * - 哪个事件通道出错
 * - 是否来自 sticky 事件
 * - 是否属于某个 scoped bus
 * - 失败发生在类型转换还是订阅回调阶段
 */
data class FlowBusErrorContext(
    /** 出错事件通道的名字。 */
    val eventName: String? = null,
    /** 当前事件期望的类型。 */
    val expectedValueType: KClass<out Any>? = null,
    /** 实际收到事件的运行时类型。 */
    val actualValueType: KClass<out Any>? = null,
    /** 当前错误是否来自 sticky 事件流。 */
    val isSticky: Boolean = false,
    /** 当前错误是否来自某个 scoped bus。`null` 表示根总线或未知。 */
    val scopeName: String? = null,
    /** 当前错误发生在哪个阶段。 */
    val phase: FlowBusErrorPhase,
    /** 回调切换所使用的调度器字符串表示。 */
    val dispatcher: String? = null
)

/**
 * FlowBus 订阅失败阶段。
 */
enum class FlowBusErrorPhase {
    /** 事件值在分发前发生类型转换失败。 */
    ValueCast,

    /** 订阅回调执行过程中抛出异常。 */
    SubscriberCallback
}
