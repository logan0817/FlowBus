package com.logan.flowbus.core

import kotlin.reflect.KClass

/**
 * 一个面向业务语义的命名事件句柄。
 *
 * 相比直接在多个地方重复写 `eventName` 字符串，[EventChannel] 更适合把同一个事件通道
 * 抽成一个稳定对象，然后把发送、订阅、sticky 清理等操作都围绕它组织起来。
 */
class EventChannel<T : Any> internal constructor(
    val name: String,
    val valueType: KClass<T>
) {
    init {
        require(name.isNotBlank()) { "EventChannel name must not be blank." }
    }

    /**
     * 转回底层 [EventKey]，用于与原始 API 互操作。
     */
    fun asEventKey(): EventKey<T> {
        return eventKey(name = name, valueType = valueType)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EventChannel<*>) return false
        return name == other.name && valueType == other.valueType
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + valueType.hashCode()
        return result
    }

    override fun toString(): String {
        return "EventChannel(name=$name, valueType=${valueType.qualifiedName})"
    }
}

/**
 * 创建一个类型安全的命名事件句柄。
 */
inline fun <reified T : Any> eventChannel(name: String): EventChannel<T> {
    return eventChannel(name = name, valueType = T::class)
}

/**
 * 使用显式 [KClass] 创建一个类型安全的命名事件句柄。
 */
fun <T : Any> eventChannel(name: String, valueType: KClass<T>): EventChannel<T> {
    return EventChannel(name = name, valueType = valueType)
}

/**
 * Java 友好的 [Class] 重载。
 */
fun <T : Any> eventChannel(name: String, valueType: Class<T>): EventChannel<T> {
    return eventChannel(name = name, valueType = valueType.kotlin)
}

/**
 * 把现有 [EventKey] 转成更适合公开业务语义的 [EventChannel]。
 */
@Suppress("UNCHECKED_CAST")
fun <T : Any> EventKey<T>.asEventChannel(): EventChannel<T> {
    val typedValueType = requireNotNull(valueType) {
        "EventKey '$name' does not carry runtime type information and cannot be converted to EventChannel."
    } as KClass<T>
    return eventChannel(name = name, valueType = typedValueType)
}
