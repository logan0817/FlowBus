package com.logan.flowbus.core

import kotlin.reflect.KClass

/**
 * FlowBus 中事件通道的唯一键。
 *
 * [name] 决定通道身份，[valueType] 用于在同名通道被不同类型复用时做保护。
 * 对外请通过 [eventKey] 工厂创建，以便保留类型信息。
 * 如果你是业务层调用方，通常优先用更语义化的 [EventChannel]；[EventKey] 更适合底层 API 或 Java 互操作。
 */
class EventKey<T : Any> internal constructor(
    val name: String,
    val valueType: KClass<out Any>?
) {
    init {
        require(name.isNotBlank()) { "EventKey name must not be blank." }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EventKey<*>) return false
        return name == other.name && valueType == other.valueType
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + (valueType?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        return "EventKey(name=$name, valueType=${valueType?.qualifiedName})"
    }
}

/**
 * 创建带类型信息的 [EventKey]。
 *
 * `name` 不能为空白字符串。
 */
inline fun <reified T : Any> eventKey(name: String): EventKey<T> {
    return eventKey(name = name, valueType = T::class)
}

/**
 * 使用显式 [KClass] 创建带类型信息的 [EventKey]。
 */
fun <T : Any> eventKey(name: String, valueType: KClass<T>): EventKey<T> {
    return EventKey(name = name, valueType = valueType)
}

/**
 * Java 友好的 [Class] 重载。
 */
fun <T : Any> eventKey(name: String, valueType: Class<T>): EventKey<T> {
    return eventKey(name = name, valueType = valueType.kotlin)
}

internal fun rawEventKey(name: String): EventKey<Any> {
    return EventKey(name = name, valueType = null)
}
