package com.logan.flowbus.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow

/**
 * [FlowBus] 的局部作用域视图。
 *
 * 一个 [ScopedFlowBus] 只操作自己的 [scopeName] 对应的数据，不会影响根总线或其他 scope。
 * 它本身不负责生命周期关闭；如果你需要显式 close，请改用 [FlowBusScope]。
 */
class ScopedFlowBus internal constructor(
    val scopeName: String,
    private val storeProvider: (String) -> FlowBusStore,
    private val removeScopeAction: (String) -> Unit,
    private val configProvider: () -> FlowBusConfig = { FlowBusConfig() }
) {
    /**
     * 尝试向当前 scoped bus 发送普通事件。
     */
    fun <T : Any> post(key: EventKey<T>, value: T): Boolean {
        return storeProvider(scopeName).post(key = key, value = value, isSticky = false)
    }

    /**
     * 尝试向当前 scoped bus 发送普通事件，并返回总线层面的诊断结果。
     */
    fun <T : Any> tryPostResult(key: EventKey<T>, value: T): FlowBusPostResult {
        return storeProvider(scopeName).postResult(key = key, value = value, isSticky = false, scopeName = scopeName)
    }

    /**
     * 挂起直到普通事件成功发送到当前 scoped bus。
     */
    suspend fun <T : Any> emit(key: EventKey<T>, value: T) {
        storeProvider(scopeName).emit(key = key, value = value, isSticky = false, scopeName = scopeName)
    }

    /**
     * 尝试向当前 scoped bus 发送粘性事件。
     */
    fun <T : Any> postSticky(key: EventKey<T>, value: T): Boolean {
        return storeProvider(scopeName).post(key = key, value = value, isSticky = true)
    }

    /**
     * 尝试向当前 scoped bus 发送粘性事件，并返回总线层面的诊断结果。
     */
    fun <T : Any> tryPostStickyResult(key: EventKey<T>, value: T): FlowBusPostResult {
        return storeProvider(scopeName).postResult(key = key, value = value, isSticky = true, scopeName = scopeName)
    }

    /**
     * 挂起直到粘性事件成功发送到当前 scoped bus。
     */
    suspend fun <T : Any> emitSticky(key: EventKey<T>, value: T) {
        storeProvider(scopeName).emit(key = key, value = value, isSticky = true, scopeName = scopeName)
    }

    /** 返回当前 scoped bus 中的普通事件流。 */
    fun <T : Any> flow(key: EventKey<T>): Flow<T> {
        return storeProvider(scopeName).flow(key = key, isSticky = false)
    }

    /** 返回当前 scoped bus 中的粘性事件流。 */
    fun <T : Any> stickyFlow(key: EventKey<T>): Flow<T> {
        return storeProvider(scopeName).flow(key = key, isSticky = true)
    }

    /**
     * 按顺序收集当前 scoped bus 中的普通事件，并自动使用所属 [FlowBus] 的日志和错误处理策略。
     */
    suspend fun <T : Any> collect(
        key: EventKey<T>,
        dispatcher: CoroutineDispatcher? = null,
        onReceived: (T) -> Unit
    ) {
        val config = currentConfig()
        collectFlowBusSequentially(
            flow = flow(key),
            eventKey = key,
            scopeName = scopeName,
            dispatcher = dispatcher,
            logger = config.logger,
            errorHandler = config.errorHandler,
            onReceived = onReceived
        )
    }

    /**
     * 按顺序收集当前 scoped bus 中的粘性事件，并自动使用所属 [FlowBus] 的日志和错误处理策略。
     */
    suspend fun <T : Any> collectSticky(
        key: EventKey<T>,
        dispatcher: CoroutineDispatcher? = null,
        onReceived: (T) -> Unit
    ) {
        val config = currentConfig()
        collectFlowBusSequentially(
            flow = stickyFlow(key),
            eventKey = key,
            scopeName = scopeName,
            isSticky = true,
            dispatcher = dispatcher,
            logger = config.logger,
            errorHandler = config.errorHandler,
            onReceived = onReceived
        )
    }

    internal fun currentConfig(): FlowBusConfig {
        return configProvider()
    }

    /** 从当前 scoped bus 的当前 store 中移除指定普通事件。 */
    fun <T : Any> removeEvent(key: EventKey<T>) {
        storeProvider(scopeName).removeEvent(key)
    }

    /** 清空当前 scoped bus 中指定粘性事件的 replay 缓存。 */
    fun <T : Any> clearSticky(key: EventKey<T>) {
        storeProvider(scopeName).clearSticky(key)
    }

    /** 从当前 scoped bus 的当前 store 中移除指定粘性事件，并清空现有 replay 缓存。 */
    fun <T : Any> removeSticky(key: EventKey<T>) {
        storeProvider(scopeName).removeSticky(key)
    }

    /**
     * 读取当前 scoped bus 中指定粘性事件的最新 replay 值，并清空该 sticky replay 缓存。
     */
    fun <T : Any> consumeStickyLatest(key: EventKey<T>): T? {
        return storeProvider(scopeName).consumeStickyLatest(key)
    }

    /**
     * 移除当前同名 scope 的内部 store，并清空其中缓存。
     *
     * 后续再次访问同名 scope 时，会按需创建新的 store。
     * 已经拿到手的旧 [Flow] 引用不会被主动 cancel。
     */
    fun removeScope() {
        removeScopeAction(scopeName)
    }
}
