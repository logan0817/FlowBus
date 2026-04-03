@file:OptIn(ExperimentalCoroutinesApi::class)

package com.logan.flowbus.core

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass

internal class FlowBusStore(
    private val config: FlowBusConfig
) {
    private val normalEventFlows = ConcurrentHashMap<String, MutableSharedFlow<Any>>()
    private val stickyEventFlows = ConcurrentHashMap<String, MutableSharedFlow<Any>>()
    private val keyTypes = ConcurrentHashMap<String, KClass<out Any>>()

    fun <T : Any> post(key: EventKey<T>, value: T, isSticky: Boolean): Boolean {
        registerKeyType(key)
        return getEventFlow(key.name, isSticky).tryEmit(value)
    }

    suspend fun <T : Any> emit(key: EventKey<T>, value: T, isSticky: Boolean) {
        registerKeyType(key)
        getEventFlow(key.name, isSticky).emit(value)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> flow(key: EventKey<T>, isSticky: Boolean): Flow<T> {
        registerKeyType(key)
        return getEventFlow(key.name, isSticky) as Flow<T>
    }

    fun <T : Any> clearSticky(key: EventKey<T>) {
        registerKeyType(key)
        stickyEventFlows[key.name]?.resetReplayCache()
    }

    fun <T : Any> removeSticky(key: EventKey<T>) {
        registerKeyType(key)
        stickyEventFlows.remove(key.name)
    }

    fun clearAll() {
        normalEventFlows.clear()
        stickyEventFlows.clear()
        keyTypes.clear()
    }

    fun hasEventFlow(eventName: String, isSticky: Boolean): Boolean {
        val targetMap = if (isSticky) stickyEventFlows else normalEventFlows
        return targetMap.containsKey(eventName)
    }

    fun stickyReplayCache(eventName: String): List<Any> {
        return stickyEventFlows[eventName]?.replayCache.orEmpty()
    }

    private fun registerKeyType(key: EventKey<*>) {
        val expectedType = key.valueType ?: return
        val existingType = keyTypes.putIfAbsent(key.name, expectedType)
        require(existingType == null || existingType == expectedType) {
            "Event key '${key.name}' is already bound to ${existingType?.qualifiedName}, cannot reuse it with ${expectedType.qualifiedName}."
        }
    }

    private fun getEventFlow(eventName: String, isSticky: Boolean): MutableSharedFlow<Any> {
        val targetMap = if (isSticky) stickyEventFlows else normalEventFlows
        return targetMap.computeIfAbsent(eventName) {
            MutableSharedFlow(
                replay = if (isSticky) config.stickyReplay else 0,
                extraBufferCapacity = if (isSticky) config.stickyExtraBufferCapacity else config.normalBufferCapacity,
                onBufferOverflow = config.overflowPolicy
            )
        }
    }
}

