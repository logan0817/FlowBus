package com.logan.flowbus.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

/**
 * FlowBus 的核心入口。
 *
 * 它同时管理：
 * - 根总线（全局事件）
 * - 多个 scoped bus（局部事件）
 */
class FlowBus(
    val config: FlowBusConfig = FlowBusConfig()
) {
    init {
        require(config.normalBufferCapacity >= 0) { "normalBufferCapacity must be >= 0" }
        require(config.stickyReplay >= 0) { "stickyReplay must be >= 0" }
        require(config.stickyExtraBufferCapacity >= 0) { "stickyExtraBufferCapacity must be >= 0" }
    }

    private val rootStore = FlowBusStore(config)
    private val scopedStores = ConcurrentHashMap<String, FlowBusStore>()

    /**
     * 尝试向根总线发送普通事件。
     *
     * 返回 `false` 表示当前共享流未能立刻接收该事件；如果你需要挂起直到发送成功，
     * 请改用 [emit]。
     */
    fun <T : Any> post(key: EventKey<T>, value: T): Boolean {
        return rootStore.post(key = key, value = value, isSticky = false)
    }

    /**
     * 挂起直到普通事件成功发送到根总线。
     */
    suspend fun <T : Any> emit(key: EventKey<T>, value: T) {
        rootStore.emit(key = key, value = value, isSticky = false)
    }

    /**
     * 尝试向根总线发送粘性事件。
     *
     * 返回 `false` 表示当前共享流未能立刻接收该事件；如果你需要挂起直到发送成功，
     * 请改用 [emitSticky]。
     */
    fun <T : Any> postSticky(key: EventKey<T>, value: T): Boolean {
        return rootStore.post(key = key, value = value, isSticky = true)
    }

    /**
     * 挂起直到粘性事件成功发送到根总线。
     */
    suspend fun <T : Any> emitSticky(key: EventKey<T>, value: T) {
        rootStore.emit(key = key, value = value, isSticky = true)
    }

    /** 返回根总线中的普通事件流。 */
    fun <T : Any> flow(key: EventKey<T>): Flow<T> {
        return rootStore.flow(key = key, isSticky = false)
    }

    /** 返回根总线中的粘性事件流。 */
    fun <T : Any> stickyFlow(key: EventKey<T>): Flow<T> {
        return rootStore.flow(key = key, isSticky = true)
    }

    /** 清空根总线中指定粘性事件的 replay 缓存。 */
    fun <T : Any> clearSticky(key: EventKey<T>) {
        rootStore.clearSticky(key)
    }

    /** 从根总线中彻底移除指定粘性事件。 */
    fun <T : Any> removeSticky(key: EventKey<T>) {
        rootStore.removeSticky(key)
    }

    /**
     * 返回指定名称对应的 scoped bus。
     */
    fun scoped(scopeName: String): ScopedFlowBus {
        require(scopeName.isNotBlank()) { "scopeName must not be blank" }
        return ScopedFlowBus(
            scopeName = scopeName,
            storeProvider = ::getScopedStore,
            removeScopeAction = ::removeScope
        )
    }

    /**
     * 返回指定 [FlowBusOwner] 对应的 scoped bus。
     */
    fun scoped(owner: FlowBusOwner): ScopedFlowBus {
        require(owner !is FlowBusScope || !owner.isClosed) {
            "FlowBusScope '${owner.busScopeName}' is already closed."
        }
        return scoped(owner.busScopeName)
    }

    /**
     * 打开一个需要显式关闭的 scope 句柄。
     *
     * 适合 Session、任务链路、Repository、后台 Worker 等需要显式控制生命周期的场景。
     * 关闭后会自动移除该 scope 的缓存与事件流，并阻止句柄继续被误用。
     */
    fun openScope(scopeName: String): FlowBusScope {
        require(scopeName.isNotBlank()) { "scopeName must not be blank" }
        return FlowBusScope(
            busScopeName = scopeName,
            scopedBus = scoped(scopeName),
            closeAction = ::removeScope
        )
    }

    /**
     * 打开一个会跟随 [Job] 生命周期自动关闭的 scope。
     */
    fun openScope(scopeName: String, closeWhen: Job): FlowBusScope {
        return openScope(scopeName).bindTo(closeWhen)
    }

    /**
     * 打开一个会跟随 [CoroutineScope] 生命周期自动关闭的 scope。
     */
    fun openScope(scopeName: String, closeWhen: CoroutineScope): FlowBusScope {
        return openScope(scopeName).bindTo(closeWhen)
    }

    /**
     * 基于已有 [FlowBusOwner] 打开一个需要显式关闭的 scope 句柄。
     */
    fun openScope(owner: FlowBusOwner): FlowBusScope {
        require(owner !is FlowBusScope || !owner.isClosed) {
            "FlowBusScope '${owner.busScopeName}' is already closed."
        }
        return openScope(owner.busScopeName)
    }

    /**
     * 基于已有 [FlowBusOwner] 打开一个会跟随 [Job] 生命周期自动关闭的 scope。
     */
    fun openScope(owner: FlowBusOwner, closeWhen: Job): FlowBusScope {
        return openScope(owner).bindTo(closeWhen)
    }

    /**
     * 基于已有 [FlowBusOwner] 打开一个会跟随 [CoroutineScope] 生命周期自动关闭的 scope。
     */
    fun openScope(owner: FlowBusOwner, closeWhen: CoroutineScope): FlowBusScope {
        return openScope(owner).bindTo(closeWhen)
    }

    /**
     * 删除指定名称对应的 scoped bus，并清空其中缓存。
     */
    fun removeScope(scopeName: String) {
        require(scopeName.isNotBlank()) { "scopeName must not be blank" }
        scopedStores.remove(scopeName)?.clearAll()
    }

    /**
     * 删除指定 [FlowBusOwner] 对应的 scoped bus，并清空其中缓存。
     */
    fun removeScope(owner: FlowBusOwner) {
        removeScope(owner.busScopeName)
    }

    internal fun hasScope(scopeName: String): Boolean {
        return scopedStores.containsKey(scopeName)
    }

    internal fun hasEventFlow(eventName: String, isSticky: Boolean): Boolean {
        return rootStore.hasEventFlow(eventName = eventName, isSticky = isSticky)
    }

    internal fun stickyReplayCache(eventName: String): List<Any> {
        return rootStore.stickyReplayCache(eventName)
    }

    private fun getScopedStore(scopeName: String): FlowBusStore {
        require(scopeName.isNotBlank()) { "scopeName must not be blank" }
        return scopedStores.computeIfAbsent(scopeName) { FlowBusStore(config) }
    }
}
