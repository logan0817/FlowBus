package com.logan.flowbus.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

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
 * - 当前 scope 对应的内部 store 会被移除，并清空其中缓存
 * - 该句柄不允许继续使用
 * - 已经拿到手的旧 [Flow] 引用不会被主动 cancel
 *
 * 如果你只想复用同一个命名 scope，而不想自己管理 close，优先使用 [ScopedFlowBus]。
 */
class FlowBusScope internal constructor(
    override val busScopeName: String,
    private val scopedBus: ScopedFlowBus,
    private val closeAction: (String, FlowBusScope) -> Unit,
    autoCloseDispatcher: CoroutineDispatcher = Dispatchers.Default
) : FlowBusOwner, AutoCloseable {
    private val autoCloseScope = CoroutineScope(SupervisorJob() + autoCloseDispatcher)
    private val lifecycleBindings = CopyOnWriteArrayList<DisposableHandle>()
    private val operationLock = ReentrantLock()
    private val operationFinished = operationLock.newCondition()
    private var isClosing: Boolean = false
    private var inFlightOperationCount: Int = 0

    /** 当前 scope 是否已经关闭。 */
    @Volatile
    var isClosed: Boolean = false
        private set

    /** 与 [FlowBusOwner.busScopeName] 相同的别名，便于阅读。 */
    val scopeName: String
        get() = busScopeName

    /** 尝试向当前 scope 发送普通事件。 */
    fun <T : Any> post(key: EventKey<T>, value: T): Boolean {
        return withOpenOperation {
            scopedBus.post(key, value)
        }
    }

    /** 尝试向当前 scope 发送普通事件，并返回总线层面的诊断结果。 */
    fun <T : Any> tryPostResult(key: EventKey<T>, value: T): FlowBusPostResult {
        return withOpenOperation {
            scopedBus.tryPostResult(key, value)
        }
    }

    /** 挂起直到普通事件成功发送到当前 scope。 */
    suspend fun <T : Any> emit(key: EventKey<T>, value: T) {
        withOpenOperationSuspend {
            scopedBus.emit(key, value)
        }
    }

    /** 尝试向当前 scope 发送粘性事件。 */
    fun <T : Any> postSticky(key: EventKey<T>, value: T): Boolean {
        return withOpenOperation {
            scopedBus.postSticky(key, value)
        }
    }

    /** 尝试向当前 scope 发送粘性事件，并返回总线层面的诊断结果。 */
    fun <T : Any> tryPostStickyResult(key: EventKey<T>, value: T): FlowBusPostResult {
        return withOpenOperation {
            scopedBus.tryPostStickyResult(key, value)
        }
    }

    /** 挂起直到粘性事件成功发送到当前 scope。 */
    suspend fun <T : Any> emitSticky(key: EventKey<T>, value: T) {
        withOpenOperationSuspend {
            scopedBus.emitSticky(key, value)
        }
    }

    /** 返回当前 scope 中指定普通事件对应的 [Flow]。 */
    fun <T : Any> flow(key: EventKey<T>): Flow<T> {
        return withOpenOperation {
            scopedBus.flow(key)
        }
    }

    /** 返回当前 scope 中指定粘性事件对应的 [Flow]。 */
    fun <T : Any> stickyFlow(key: EventKey<T>): Flow<T> {
        return withOpenOperation {
            scopedBus.stickyFlow(key)
        }
    }

    /**
     * 按顺序收集当前 scope 中的普通事件，并自动使用所属 [FlowBus] 的日志和错误处理策略。
     */
    suspend fun <T : Any> collect(
        key: EventKey<T>,
        dispatcher: CoroutineDispatcher? = null,
        onReceived: (T) -> Unit
    ) {
        val targetFlow = flow(key)
        val config = scopedBus.currentConfig()
        collectFlowBusSequentially(
            flow = targetFlow,
            eventKey = key,
            scopeName = scopeName,
            dispatcher = dispatcher,
            logger = config.logger,
            errorHandler = config.errorHandler,
            onReceived = onReceived
        )
    }

    /**
     * 按顺序收集当前 scope 中的粘性事件，并自动使用所属 [FlowBus] 的日志和错误处理策略。
     */
    suspend fun <T : Any> collectSticky(
        key: EventKey<T>,
        dispatcher: CoroutineDispatcher? = null,
        onReceived: (T) -> Unit
    ) {
        val targetFlow = stickyFlow(key)
        val config = scopedBus.currentConfig()
        collectFlowBusSequentially(
            flow = targetFlow,
            eventKey = key,
            scopeName = scopeName,
            isSticky = true,
            dispatcher = dispatcher,
            logger = config.logger,
            errorHandler = config.errorHandler,
            onReceived = onReceived
        )
    }

    /** 从当前 scope 的当前 store 中移除指定普通事件。 */
    fun <T : Any> removeEvent(key: EventKey<T>) {
        withOpenOperation {
            scopedBus.removeEvent(key)
        }
    }

    /** 清空当前 scope 中指定粘性事件的 replay 缓存。 */
    fun <T : Any> clearSticky(key: EventKey<T>) {
        withOpenOperation {
            scopedBus.clearSticky(key)
        }
    }

    /** 从当前 scope 的当前 store 中移除指定粘性事件，并清空现有 replay 缓存。 */
    fun <T : Any> removeSticky(key: EventKey<T>) {
        withOpenOperation {
            scopedBus.removeSticky(key)
        }
    }

    /** 读取当前 scope 中指定粘性事件的最新 replay 值，并清空该 sticky replay 缓存。 */
    fun <T : Any> consumeStickyLatest(key: EventKey<T>): T? {
        return withOpenOperation {
            scopedBus.consumeStickyLatest(key)
        }
    }

    /**
     * 将当前 scope 绑定到指定 [Job] 生命周期。
     *
     * 当 [Job] 完成或取消时，当前 scope 会自动开始关闭；关闭等待会切到后台调度器执行，
     * 不会阻塞 [Job] 的 completion handler。
     */
    fun bindTo(job: Job): FlowBusScope {
        ensureOpen()
        val handle = job.invokeOnCompletion {
            closeFromLifecycleBinding()
        }

        var shouldDisposeHandle = false
        operationLock.withLock {
            if (isClosing || isClosed) {
                shouldDisposeHandle = true
            } else {
                lifecycleBindings += handle
            }
        }

        if (shouldDisposeHandle) {
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
     *
     * 关闭前会先等待当前句柄上已经开始的发送 / 订阅获取动作结束，避免 close 与在途操作互相打架。
     * 如果当前 scope 存在可能挂起的 `emit`，不要在同一个单线程调度器或 UI 关键路径上调用同步 [close]。
     * 等待期间如果当前线程被中断，会恢复线程中断状态并重新抛出 [InterruptedException]。
     */
    override fun close() {
        try {
            closeInternal(timeoutMillis = null)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        }
    }

    /**
     * 在后台调度器上等待当前 scope 关闭，避免把调用线程卡在同步等待里。
     */
    suspend fun closeSuspending(): FlowBusCloseResult {
        return withContext(Dispatchers.Default) {
            closeInternal(timeoutMillis = null)
        }
    }

    /**
     * 尝试在 [timeoutMillis] 内关闭当前 scope。
     *
     * 超时返回后，scope 会恢复为可继续使用状态，调用方可以稍后重试关闭。
     */
    fun tryClose(timeoutMillis: Long): FlowBusCloseResult {
        require(timeoutMillis >= 0) { "timeoutMillis must be >= 0" }
        return try {
            closeInternal(timeoutMillis = timeoutMillis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw e
        }
    }

    private fun ensureOpen() {
        operationLock.withLock {
            check(!isClosing && !isClosed) { "FlowBusScope '$scopeName' is already closed." }
        }
    }

    private inline fun <T> withOpenOperation(block: () -> T): T {
        beginOperation()
        return try {
            block()
        } finally {
            endOperation()
        }
    }

    private suspend inline fun <T> withOpenOperationSuspend(crossinline block: suspend () -> T): T {
        beginOperation()
        return try {
            block()
        } finally {
            endOperation()
        }
    }

    private fun beginOperation() {
        operationLock.withLock {
            check(!isClosing && !isClosed) { "FlowBusScope '$scopeName' is already closed." }
            inFlightOperationCount++
        }
    }

    private fun endOperation() {
        operationLock.withLock {
            inFlightOperationCount--
            if (inFlightOperationCount == 0) {
                operationFinished.signalAll()
            }
        }
    }

    private fun closeInternal(timeoutMillis: Long?): FlowBusCloseResult {
        val shouldClose = operationLock.withLock {
            if (isClosed) {
                return FlowBusCloseResult(
                    scopeName = scopeName,
                    closed = true,
                    outcome = FlowBusCloseOutcome.AlreadyClosed,
                    inFlightOperationCount = inFlightOperationCount
                )
            }
            if (isClosing) {
                return FlowBusCloseResult(
                    scopeName = scopeName,
                    closed = false,
                    outcome = FlowBusCloseOutcome.ClosingInProgress,
                    inFlightOperationCount = inFlightOperationCount
                )
            }

            isClosing = true
            try {
                if (timeoutMillis == null) {
                    while (inFlightOperationCount > 0) {
                        operationFinished.await()
                    }
                } else {
                    var remainingNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
                    while (inFlightOperationCount > 0 && remainingNanos > 0) {
                        remainingNanos = operationFinished.awaitNanos(remainingNanos)
                    }
                    if (inFlightOperationCount > 0) {
                        isClosing = false
                        return FlowBusCloseResult(
                            scopeName = scopeName,
                            closed = false,
                            outcome = FlowBusCloseOutcome.Timeout,
                            inFlightOperationCount = inFlightOperationCount
                        )
                    }
                }
                true
            } catch (e: InterruptedException) {
                isClosing = false
                throw e
            }
        }

        if (shouldClose) {
            finishClose()
        }

        return FlowBusCloseResult(
            scopeName = scopeName,
            closed = true,
            outcome = FlowBusCloseOutcome.Closed,
            inFlightOperationCount = 0
        )
    }

    private fun closeFromLifecycleBinding() {
        if (!markClosingForLifecycleBinding()) {
            return
        }
        launchLifecycleCloseWait()
    }

    private fun launchLifecycleCloseWait() {
        autoCloseScope.launch {
            val shouldClose = try {
                operationLock.withLock {
                    while (inFlightOperationCount > 0) {
                        operationFinished.await()
                    }
                    !isClosed
                }
            } catch (_: InterruptedException) {
                launchLifecycleCloseWait()
                false
            }
            if (shouldClose) {
                finishClose()
            }
        }
    }

    private fun markClosingForLifecycleBinding(): Boolean {
        return operationLock.withLock {
            if (isClosed || isClosing) {
                false
            } else {
                isClosing = true
                true
            }
        }
    }

    private fun finishClose() {
        lifecycleBindings.forEach { it.dispose() }
        lifecycleBindings.clear()
        closeAction(scopeName, this)
        autoCloseScope.cancel()
        operationLock.withLock {
            isClosed = true
            isClosing = false
            operationFinished.signalAll()
        }
    }
}
