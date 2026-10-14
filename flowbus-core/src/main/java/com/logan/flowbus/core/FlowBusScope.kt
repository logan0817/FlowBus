package com.logan.flowbus.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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
    private val operationChanges = MutableStateFlow(0L)
    private var isClosing: Boolean = false
    private var closeActionStarted: Boolean = false
    private var closeActionCompleted: Boolean = false
    private var closeCompletionAction: (() -> Unit)? = null
    private var inFlightOperationCount: Int = 0
    private var permanentCloseRequested: Boolean = false

    /** 当前 scope 是否已经关闭。 */
    @Volatile
    var isClosed: Boolean = false
        private set

    /** 与 [FlowBusOwner.busScopeName] 相同的别名，便于阅读。 */
    val scopeName: String
        get() = busScopeName

    /** 在当前句柄首次使用通道前配置策略；参与在途操作保护，关闭中或已关闭时拒绝。 */
    fun <T : Any> configureChannel(key: EventKey<T>, config: FlowBusChannelConfig) {
        withOpenOperation { operationBus -> operationBus.configureChannel(key, config) }
    }

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

    /** 按既定溢出策略发送普通事件到当前 scope；SUSPEND 下有慢订阅者时才挂起，不代表业务处理完成。 */
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

    /** 按既定溢出策略发送粘性事件到当前 scope；SUSPEND 下有慢订阅者时才挂起，不代表业务处理完成。 */
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

    /** 按顺序执行可挂起回调，沿用当前总线的类型校验、日志和错误策略。 */
    suspend fun <T : Any> collectSuspending(
        key: EventKey<T>,
        dispatcher: CoroutineDispatcher? = null,
        onReceived: suspend (T) -> Unit
    ) {
        val targetFlow = flow(key)
        val config = scopedBus.currentConfig()
        collectFlowBusSuspendingSequentially(
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

    /** 按顺序执行可挂起回调，沿用当前总线的类型校验、日志和错误策略。 */
    suspend fun <T : Any> collectStickySuspending(
        key: EventKey<T>,
        dispatcher: CoroutineDispatcher? = null,
        onReceived: suspend (T) -> Unit
    ) {
        val targetFlow = stickyFlow(key)
        val config = scopedBus.currentConfig()
        collectFlowBusSuspendingSequentially(
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
     * 若已有等待关闭正在进行，会保留永久关闭请求；isClosed 沿用该等待的状态直到准备清理。
     * 已经开始的发送 / 订阅获取动作会继续使用原 store，等这些动作结束后再完成 store 清理。
     * 如果调用方需要等待清理完成，请使用 [closeSuspending]；如果需要带超时结果，请使用 [tryClose]。
     */
    override fun close() {
        val action = operationLock.withLock {
            // 即使已有可取消/超时的等待，也必须保留显式 close（含 removeScope）的永久关闭请求。
            permanentCloseRequested = true
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
     * 可取消地挂起等待当前 scope 关闭，不占用线程等待在途发送。
     *
     * 本次调用发起关闭、尚未准备清理且无永久关闭请求时，取消会恢复句柄可用状态。
     * 若等待期间调用了 close/removeScope 或绑定 Job 已完成，会继续关闭，不恢复句柄。
     * 若 close() 或生命周期绑定已让句柄失效，取消只结束本次等待，不回滚关闭。
     */
    suspend fun closeSuspending(): FlowBusCloseResult {
        currentCoroutineContext().ensureActive()
        val initiatedClose = operationLock.withLock {
            if (closeActionCompleted) return closeResult(FlowBusCloseOutcome.AlreadyClosed)
            if (isClosed && isClosing) {
                false
            } else if (isClosing) {
                return FlowBusCloseResult(scopeName, false, FlowBusCloseOutcome.ClosingInProgress, inFlightOperationCount)
            } else {
                isClosing = true
                true
            }
        }
        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                var action: (() -> Unit)? = null
                val observedVersion = operationLock.withLock {
                    if (closeActionCompleted) return closeResult(FlowBusCloseOutcome.Closed)
                    if (initiatedClose && inFlightOperationCount == 0) {
                        closeCompletionAction = prepareCloseAction(scopeName, this)
                        action = claimCloseActionIfIdleLocked()
                    }
                    operationChanges.value
                }
                if (action != null) {
                    finishClose(requireNotNull(action))
                    return closeResult(FlowBusCloseOutcome.Closed)
                }
                // 版本在同一锁内读取，状态变化先于订阅也不会丢失唤醒。
                operationChanges.first { it != observedVersion }
            }
        } catch (e: CancellationException) {
            val permanentCloseStart = operationLock.withLock {
                if (initiatedClose && !closeActionStarted) {
                    resetFailedCloseWaitLocked()
                } else {
                    null
                }
            }
            permanentCloseStart?.let { completePreparedCloseStart(it) }
            throw e
        }
    }

    private fun closeResult(outcome: FlowBusCloseOutcome): FlowBusCloseResult {
        return FlowBusCloseResult(scopeName, true, outcome, 0)
    }

    internal fun isCloseCompletedForRemoval(): Boolean {
        return operationLock.withLock { closeActionCompleted }
    }

    /**
     * 尝试在 [timeoutMillis] 内关闭当前 scope。
     *
     * 如果本次调用刚开始关闭且等待在途操作超时，且无永久关闭请求，scope 会恢复可用以便稍后重试。
     * 如果等待期间收到 [close] 或生命周期关闭请求，本次超时只结束等待，旧句柄不会恢复可用。
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
        val notifyCloseWaiters = operationLock.withLock {
            inFlightOperationCount--
            if (inFlightOperationCount == 0) {
                operationFinished.signalAll()
            }
            isClosing && inFlightOperationCount == 0
        }
        // 在 operation 锁外恢复挂起等待者，避免 Unconfined 回调重入时反向获取 registry 锁。
        if (notifyCloseWaiters) operationChanges.update { it + 1 }
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

    private fun closeInternal(timeoutMillis: Long): FlowBusCloseResult {
        var permanentCloseStart: CloseStart? = null
        try {
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
                        permanentCloseStart = resetFailedCloseWaitLocked()
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
                    permanentCloseStart = resetFailedCloseWaitLocked()
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
        } finally {
            permanentCloseStart?.let { completePreparedCloseStart(it) }
        }
    }

    private fun closeFromLifecycleBinding() {
        val closeStart = markClosingForLifecycleBinding() ?: return
        completePreparedCloseStart(closeStart)
    }

    private fun completePreparedCloseStart(closeStart: CloseStart) {
        if (closeStart.action != null) {
            finishClose(requireNotNull(closeStart.action))
            return
        }
        launchLifecycleCloseWait()
    }

    private fun resetFailedCloseWaitLocked(): CloseStart? {
        if (permanentCloseRequested) {
            // 已有永久关闭请求时直接准备关闭，不先恢复开放状态，避免并发操作趁隙进入。
            closeCompletionAction = prepareCloseAction(scopeName, this)
            isClosed = true
            return CloseStart(action = claimCloseActionIfIdleLocked())
        }
        isClosing = false
        operationFinished.signalAll()
        return null
    }

    private fun launchLifecycleCloseWait() {
        autoCloseScope.launch {
            while (true) {
                var action: (() -> Unit)?
                val observedVersion = operationLock.withLock {
                    if (closeActionStarted) return@launch
                    action = claimCloseActionIfIdleLocked()
                    operationChanges.value
                }
                if (action != null) {
                    finishClose(requireNotNull(action))
                    return@launch
                }
                operationChanges.first { it != observedVersion }
            }
        }
    }

    private fun markClosingForLifecycleBinding(): CloseStart? {
        return operationLock.withLock {
            // 显式关闭等待取消/超时后仍要履行已经到达的永久关闭请求。
            permanentCloseRequested = true
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

    private fun awaitInFlightOperationsLocked(timeoutMillis: Long): Boolean {
        var remainingNanos = TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        while (inFlightOperationCount > 0 && remainingNanos > 0) {
            remainingNanos = operationFinished.awaitNanos(remainingNanos)
        }
        return inFlightOperationCount == 0
    }

    private fun waitPreparedCloseCompletionLocked(timeoutMillis: Long): FlowBusCloseResult {
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
        operationChanges.update { it + 1 }
    }

    private data class CloseStart(
        val action: (() -> Unit)?
    )
}
