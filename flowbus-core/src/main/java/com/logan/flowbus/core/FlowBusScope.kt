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
 * - 当前句柄会立即拒绝新的收发操作
 * - 当前 scope 对应的内部 store 会在已开始的操作结束后移除，并清空其中缓存
 * - 已经拿到手的旧 [Flow] 引用不会被主动 cancel
 *
 * 如果你只想复用同一个命名 scope，而不想自己管理 close，优先使用 [ScopedFlowBus]。
 */
class FlowBusScope internal constructor(
    override val busScopeName: String,
    private val scopedBus: ScopedFlowBus,
    private val closeAction: (String, FlowBusScope) -> Unit,
    private val operationScopedBusProvider: (() -> ScopedFlowBus)? = null,
    private val prepareCloseAction: (String, FlowBusScope) -> () -> Unit = { scopeName, scope ->
        { closeAction(scopeName, scope) }
    },
    autoCloseDispatcher: CoroutineDispatcher = Dispatchers.Default
) : FlowBusOwner, AutoCloseable {
    private val autoCloseScope = CoroutineScope(SupervisorJob() + autoCloseDispatcher)
    private val lifecycleBindings = CopyOnWriteArrayList<DisposableHandle>()
    private val operationLock = ReentrantLock()
    private val operationFinished = operationLock.newCondition()
    private var isClosing: Boolean = false
    private var closeActionStarted: Boolean = false
    private var closeActionCompleted: Boolean = false
    private var closeCompletionAction: (() -> Unit)? = null
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
        return withOpenOperation { operationBus ->
            operationBus.post(key, value)
        }
    }

    /** 尝试向当前 scope 发送普通事件，并返回总线层面的诊断结果。 */
    fun <T : Any> tryPostResult(key: EventKey<T>, value: T): FlowBusPostResult {
        return withOpenOperation { operationBus ->
            operationBus.tryPostResult(key, value)
        }
    }

    /** 挂起直到普通事件成功发送到当前 scope。 */
    suspend fun <T : Any> emit(key: EventKey<T>, value: T) {
        withOpenOperationSuspend { operationBus ->
            operationBus.emit(key, value)
        }
    }

    /** 尝试向当前 scope 发送粘性事件。 */
    fun <T : Any> postSticky(key: EventKey<T>, value: T): Boolean {
        return withOpenOperation { operationBus ->
            operationBus.postSticky(key, value)
        }
    }

    /** 尝试向当前 scope 发送粘性事件，并返回总线层面的诊断结果。 */
    fun <T : Any> tryPostStickyResult(key: EventKey<T>, value: T): FlowBusPostResult {
        return withOpenOperation { operationBus ->
            operationBus.tryPostStickyResult(key, value)
        }
    }

    /** 挂起直到粘性事件成功发送到当前 scope。 */
    suspend fun <T : Any> emitSticky(key: EventKey<T>, value: T) {
        withOpenOperationSuspend { operationBus ->
            operationBus.emitSticky(key, value)
        }
    }

    /** 返回当前 scope 中指定普通事件对应的 [Flow]。 */
    fun <T : Any> flow(key: EventKey<T>): Flow<T> {
        return withOpenOperation { operationBus ->
            operationBus.flow(key)
        }
    }

    /** 返回当前 scope 中指定粘性事件对应的 [Flow]。 */
    fun <T : Any> stickyFlow(key: EventKey<T>): Flow<T> {
        return withOpenOperation { operationBus ->
            operationBus.stickyFlow(key)
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
        withOpenOperation { operationBus ->
            operationBus.removeEvent(key)
        }
    }

    /** 清空当前 scope 中指定粘性事件的 replay 缓存。 */
    fun <T : Any> clearSticky(key: EventKey<T>) {
        withOpenOperation { operationBus ->
            operationBus.clearSticky(key)
        }
    }

    /** 从当前 scope 的当前 store 中移除指定粘性事件，并清空现有 replay 缓存。 */
    fun <T : Any> removeSticky(key: EventKey<T>) {
        withOpenOperation { operationBus ->
            operationBus.removeSticky(key)
        }
    }

    /** 读取当前 scope 中指定粘性事件的最新 replay 值，并清空该 sticky replay 缓存。 */
    fun <T : Any> consumeStickyLatest(key: EventKey<T>): T? {
        return withOpenOperation { operationBus ->
            operationBus.consumeStickyLatest(key)
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
     * 该调用会立即让当前句柄失效，不会阻塞等待挂起中的发送。
     * 已经开始的发送 / 订阅获取动作会继续使用原 store，等这些动作结束后再完成 store 清理。
     * 如果调用方需要等待清理完成，请使用 [closeSuspending]；如果需要带超时结果，请使用 [tryClose]。
     */
    override fun close() {
        val action = operationLock.withLock {
            if (isClosing || isClosed) return
            isClosing = true
            closeCompletionAction = prepareCloseAction(scopeName, this)
            isClosed = true
            claimCloseActionIfIdleLocked()
        }
        if (action != null) {
            finishClose(action)
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

    private inline fun <T> withOpenOperation(block: (ScopedFlowBus) -> T): T {
        val operationBus = beginOperation()
        return try {
            block(operationBus)
        } finally {
            endOperation()
        }
    }

    private suspend inline fun <T> withOpenOperationSuspend(crossinline block: suspend (ScopedFlowBus) -> T): T {
        val operationBus = beginOperation()
        return try {
            block(operationBus)
        } finally {
            endOperation()
        }
    }

    private fun beginOperation(): ScopedFlowBus {
        return operationLock.withLock {
            check(!isClosing && !isClosed) { "FlowBusScope '$scopeName' is already closed." }
            val operationBus = operationScopedBusProvider?.invoke() ?: scopedBus
            inFlightOperationCount++
            operationBus
        }
    }

    private fun endOperation() {
        operationLock.withLock {
            inFlightOperationCount--
            if (inFlightOperationCount == 0) {
                operationFinished.signalAll()
            }
        }
        completeCloseIfIdle()
    }

    private fun completeCloseIfIdle() {
        val action = operationLock.withLock {
            claimCloseActionIfIdleLocked()
        }
        if (action != null) {
            finishClose(action)
        }
    }

    private fun closeInternal(timeoutMillis: Long?): FlowBusCloseResult {
        val action = operationLock.withLock {
            if (closeActionCompleted) {
                return FlowBusCloseResult(
                    scopeName = scopeName,
                    closed = true,
                    outcome = FlowBusCloseOutcome.AlreadyClosed,
                    inFlightOperationCount = inFlightOperationCount
                )
            }
            if (isClosed && isClosing) {
                return waitPreparedCloseCompletionLocked(timeoutMillis)
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
                if (!awaitInFlightOperationsLocked(timeoutMillis)) {
                    isClosing = false
                    operationFinished.signalAll()
                    return FlowBusCloseResult(
                        scopeName = scopeName,
                        closed = false,
                        outcome = FlowBusCloseOutcome.Timeout,
                        inFlightOperationCount = inFlightOperationCount
                    )
                }
                closeCompletionAction = prepareCloseAction(scopeName, this)
                claimCloseActionIfIdleLocked()
            } catch (e: InterruptedException) {
                isClosing = false
                operationFinished.signalAll()
                throw e
            }
        }

        if (action != null) {
            finishClose(action)
        }

        return FlowBusCloseResult(
            scopeName = scopeName,
            closed = true,
            outcome = FlowBusCloseOutcome.Closed,
            inFlightOperationCount = 0
        )
    }

    private fun closeFromLifecycleBinding() {
        val closeStart = markClosingForLifecycleBinding() ?: return
        if (closeStart.action != null) {
            finishClose(requireNotNull(closeStart.action))
            return
        }
        launchLifecycleCloseWait()
    }

    private fun launchLifecycleCloseWait() {
        autoCloseScope.launch {
            val action = try {
                operationLock.withLock {
                    while (inFlightOperationCount > 0) {
                        operationFinished.await()
                    }
                    claimCloseActionIfIdleLocked()
                }
            } catch (_: InterruptedException) {
                launchLifecycleCloseWait()
                null
            }
            if (action != null) {
                finishClose(action)
            }
        }
    }

    private fun markClosingForLifecycleBinding(): CloseStart? {
        return operationLock.withLock {
            if (isClosed || isClosing) {
                null
            } else {
                isClosing = true
                closeCompletionAction = prepareCloseAction(scopeName, this)
                isClosed = true
                CloseStart(action = claimCloseActionIfIdleLocked())
            }
        }
    }

    private fun claimCloseActionIfIdleLocked(): (() -> Unit)? {
        if (!isClosing || closeActionStarted || inFlightOperationCount > 0) {
            return null
        }
        val action = closeCompletionAction ?: return null
        closeActionStarted = true
        return action
    }

    private fun awaitInFlightOperationsLocked(timeoutMillis: Long?): Boolean {
        if (timeoutMillis == null) {
            while (inFlightOperationCount > 0) {
                operationFinished.await()
            }
            return true
        }

        var remainingNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (inFlightOperationCount > 0 && remainingNanos > 0) {
            remainingNanos = operationFinished.awaitNanos(remainingNanos)
        }
        return inFlightOperationCount == 0
    }

    private fun waitPreparedCloseCompletionLocked(timeoutMillis: Long?): FlowBusCloseResult {
        if (timeoutMillis == null) {
            while (!closeActionCompleted) {
                operationFinished.await()
            }
            return FlowBusCloseResult(
                scopeName = scopeName,
                closed = true,
                outcome = FlowBusCloseOutcome.Closed,
                inFlightOperationCount = 0
            )
        }

        var remainingNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (!closeActionCompleted && remainingNanos > 0) {
            remainingNanos = operationFinished.awaitNanos(remainingNanos)
        }
        return if (closeActionCompleted) {
            FlowBusCloseResult(
                scopeName = scopeName,
                closed = true,
                outcome = FlowBusCloseOutcome.Closed,
                inFlightOperationCount = 0
            )
        } else {
            FlowBusCloseResult(
                scopeName = scopeName,
                closed = false,
                outcome = FlowBusCloseOutcome.Timeout,
                inFlightOperationCount = inFlightOperationCount
            )
        }
    }

    private fun finishClose(action: () -> Unit) {
        lifecycleBindings.forEach { it.dispose() }
        lifecycleBindings.clear()
        action()
        autoCloseScope.cancel()
        operationLock.withLock {
            isClosed = true
            isClosing = false
            closeActionCompleted = true
            operationFinished.signalAll()
        }
    }

    private data class CloseStart(
        val action: (() -> Unit)?
    )
}
