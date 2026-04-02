package com.logan.flowbus

import kotlin.reflect.KClass
import com.logan.flowbus.core.EventChannel as CoreEventChannel
import com.logan.flowbus.core.eventChannel as coreEventChannel

/**
 * Android 模块对 core [CoreEventChannel] 的公开别名。
 *
 * 这样 Android 用户可以直接从 `com.logan.flowbus` 包下发现和使用命名事件句柄，
 * 不必再额外切换到 core 包名。
 */
typealias EventChannel<T> = CoreEventChannel<T>

/**
 * 创建一个 Android 场景可直接使用的命名事件句柄。
 */
inline fun <reified T : Any> eventChannel(name: String): EventChannel<T> {
    return coreEventChannel(name = name)
}

/**
 * 使用显式 [KClass] 创建命名事件句柄。
 */
fun <T : Any> eventChannel(name: String, valueType: KClass<T>): EventChannel<T> {
    return coreEventChannel(name = name, valueType = valueType)
}

/**
 * Java 友好的 [Class] 重载。
 */
fun <T : Any> eventChannel(name: String, valueType: Class<T>): EventChannel<T> {
    return coreEventChannel(name = name, valueType = valueType)
}
