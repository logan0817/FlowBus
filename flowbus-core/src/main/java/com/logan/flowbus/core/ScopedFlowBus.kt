package com.logan.flowbus.core

import kotlinx.coroutines.flow.Flow

/**
 * [FlowBus] 的局部作用域视图。
 *
 * 一个 [ScopedFlowBus] 只操作自己的 [scopeName] 对应的数据，不会影响根总线或其他 scope。
 */
class ScopedFlowBus internal constructor(
    val scopeName: String,
    private val storeProvider: (String) -> FlowBusStore,
    private val removeScopeAction: (String) -> Unit
) {
    /**
     * 尝试向当前 scoped bus 发送普通事件。
     */
    fun <T : Any> post(key: EventKey<T>, value: T): Boolean {
        return storeProvider(scopeName).post(key = key, value = value, isSticky = false)
    }

    /**
     * 挂起直到普通事件成功发送到当前 scoped bus。
     */
    suspend fun <T : Any> emit(key: EventKey<T>, value: T) {
        storeProvider(scopeName).emit(key = key, value = value, isSticky = false)
    }

    /**
     * 尝试向当前 scoped bus 发送粘性事件。
     */
    fun <T : Any> postSticky(key: EventKey<T>, value: T): Boolean {
        return storeProvider(scopeName).post(key = key, value = value, isSticky = true)
    }

    /**
     * 挂起直到粘性事件成功发送到当前 scoped bus。
     */
    suspend fun <T : Any> emitSticky(key: EventKey<T>, value: T) {
        storeProvider(scopeName).emit(key = key, value = value, isSticky = true)
    }

    /** 返回当前 scoped bus 中的普通事件流。 */
    fun <T : Any> flow(key: EventKey<T>): Flow<T> {
        return storeProvider(scopeName).flow(key = key, isSticky = false)
    }

    /** 返回当前 scoped bus 中的粘性事件流。 */
    fun <T : Any> stickyFlow(key: EventKey<T>): Flow<T> {
        return storeProvider(scopeName).flow(key = key, isSticky = true)
    }

    /** 清空当前 scoped bus 中指定粘性事件的 replay 缓存。 */
    fun <T : Any> clearSticky(key: EventKey<T>) {
        storeProvider(scopeName).clearSticky(key)
    }

    /** 从当前 scoped bus 中彻底移除指定粘性事件。 */
    fun <T : Any> removeSticky(key: EventKey<T>) {
        storeProvider(scopeName).removeSticky(key)
    }

    /** 删除当前 scope，并清空其中所有事件缓存。 */
    fun removeScope() {
        removeScopeAction(scopeName)
    }
}
