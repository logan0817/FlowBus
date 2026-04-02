package com.logan.flowbus.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 一个带显式生命周期的 FlowBus scope 句柄。
 *
 * 它既是 [FlowBusOwner]，也直接提供 scoped bus 的收发能力，适合：
 * - session scope
 * - repository scope
 * - worker scope
 * - task scope
 *
 * 调用 [close] 后：
 * - 当前 scope 的缓存会被清空
 * - 当前 scope 的 Flow 会被移除
 * - 该句柄不允许继续使用
 */
class FlowBusScope internal constructor(
    override val busScopeName: String,
    private val scopedBus: ScopedFlowBus,
    private val closeAction: (String) -> Unit
) : FlowBusOwner, AutoCloseable {
    private val lifecycleBindings = CopyOnWriteArrayList<DisposableHandle>()

    @Volatile
    /** 当前 scope 是否已经关闭。 */
    var isClosed: Boolean = false
        private set

    /** 与 [FlowBusOwner.busScopeName] 相同的别名，便于阅读。 */
    val scopeName: String
        get() = busScopeName

    /** 尝试向当前 scope 发送普通事件。 */
    fun <T : Any> post(key: EventKey<T>, value: T): Boolean {
        ensureOpen()
        return scopedBus.post(key, value)
    }

    /** 挂起直到普通事件成功发送到当前 scope。 */
    suspend fun <T : Any> emit(key: EventKey<T>, value: T) {
        ensureOpen()
        scopedBus.emit(key, value)
    }

    /** 尝试向当前 scope 发送粘性事件。 */
    fun <T : Any> postSticky(key: EventKey<T>, value: T): Boolean {
        ensureOpen()
        return scopedBus.postSticky(key, value)
    }

    /** 挂起直到粘性事件成功发送到当前 scope。 */
    suspend fun <T : Any> emitSticky(key: EventKey<T>, value: T) {
        ensureOpen()
        scopedBus.emitSticky(key, value)
    }

    /** 返回当前 scope 中指定普通事件对应的 [Flow]。 */
    fun <T : Any> flow(key: EventKey<T>): Flow<T> {
        ensureOpen()
        return scopedBus.flow(key)
    }

    /** 返回当前 scope 中指定粘性事件对应的 [Flow]。 */
    fun <T : Any> stickyFlow(key: EventKey<T>): Flow<T> {
        ensureOpen()
        return scopedBus.stickyFlow(key)
    }

    /** 清空当前 scope 中指定粘性事件的 replay 缓存。 */
    fun <T : Any> clearSticky(key: EventKey<T>) {
        ensureOpen()
        scopedBus.clearSticky(key)
    }

    /** 从当前 scope 中彻底移除指定粘性事件。 */
    fun <T : Any> removeSticky(key: EventKey<T>) {
        ensureOpen()
        scopedBus.removeSticky(key)
    }

    /**
     * 将当前 scope 绑定到指定 [Job] 生命周期。
     *
     * 当 [Job] 完成或取消时，当前 scope 会自动 [close]。
     */
    fun bindTo(job: Job): FlowBusScope {
        ensureOpen()
        val handle = job.invokeOnCompletion {
            close()
        }
        lifecycleBindings += handle

        if (isClosed) {
            handle.dispose()
        }

        return this
    }

    /**
     * 将当前 scope 绑定到指定 [CoroutineScope] 的生命周期。
     *
     * 要求该 [CoroutineScope] 的上下文中存在 [Job]。
     */
    fun bindTo(scope: CoroutineScope): FlowBusScope {
        val job = requireNotNull(scope.coroutineContext[Job]) {
            "CoroutineScope must contain a Job to bind FlowBusScope '$scopeName'."
        }
        return bindTo(job)
    }

    /**
     * 关闭当前 scope，并清理该 scope 下的事件流与缓存。
     */
    override fun close() {
        if (isClosed) return
        isClosed = true
        lifecycleBindings.forEach { it.dispose() }
        lifecycleBindings.clear()
        closeAction(scopeName)
    }

    private fun ensureOpen() {
        check(!isClosed) { "FlowBusScope '$scopeName' is already closed." }
    }
}
