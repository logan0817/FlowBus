package com.logan.flowbus.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

private const val FLOWBUS_TAG = "FlowBus"

/**
 * 按顺序收集 FlowBus 事件。
 *
 * 这是 core 与 Android 适配层共享的底层收集器。应用侧优先使用 [FlowBus.collect]、
 * [ScopedFlowBus.collect]、[FlowBusScope.collect] 或 Android 模块的 `onEvent(...)` /
 * `collectEvent(...)`，不要直接把业务订阅耦合到这个函数的内部参数上。
 *
 * 该函数会逐个执行 [onReceived]，不会并发处理同一条订阅链路上的事件。
 *
 * 几个关键边界：
 * - 如果提供了 [eventKey]，会先按它携带的运行时类型做校验
 * - 普通异常会写入 [logger]，再交给 [errorHandler]
 * - [CancellationException] 会继续向外抛，不会被吞掉
 * - 指定 [dispatcher] 时，回调会切到该调度器执行，但仍保持串行
 *
 * [errorHandler] 收到的阶段分两类：
 * - [FlowBusErrorPhase.ValueCast]：事件值与期望类型不匹配
 * - [FlowBusErrorPhase.SubscriberCallback]：订阅回调自身抛出普通异常
 */
suspend fun <T : Any> collectFlowBusSequentially(
    flow: Flow<Any>,
    eventKey: EventKey<out Any>? = null,
    scopeName: String? = null,
    isSticky: Boolean = false,
    dispatcher: CoroutineDispatcher? = null,
    logger: FlowBusLogger = FlowBusLogger.None,
    errorHandler: FlowBusErrorHandler = FlowBusErrorHandler.Rethrow,
    onReceived: (T) -> Unit
) {
    flow.collectWithFlowBusDiagnostics { value, session ->
        handleReceivedFlowBusSuspendingEventSequentially<T>(
            value = value,
            eventKey = eventKey,
            scopeName = scopeName,
            isSticky = isSticky,
            dispatcher = dispatcher,
            logger = logger,
            errorHandler = errorHandler,
            diagnosticSession = session,
            onReceived = { onReceived(it) }
        )
    }
}

/**
 * 按顺序执行可挂起的事件回调；上一条回调完成后才处理下一条事件。
 *
 * 与 [collectFlowBusSequentially] 共用类型校验、日志、错误策略和调度器行为。
 * 回调取消会继续传播；普通异常由 [errorHandler] 决定继续收集或终止。
 * 挂起回调不会改变源 Flow 的缓冲和丢弃策略。
 */
suspend fun <T : Any> collectFlowBusSuspendingSequentially(
    flow: Flow<Any>,
    eventKey: EventKey<out Any>? = null,
    scopeName: String? = null,
    isSticky: Boolean = false,
    dispatcher: CoroutineDispatcher? = null,
    logger: FlowBusLogger = FlowBusLogger.None,
    errorHandler: FlowBusErrorHandler = FlowBusErrorHandler.Rethrow,
    onReceived: suspend (T) -> Unit
) {
    flow.collectWithFlowBusDiagnostics { value, session ->
        handleReceivedFlowBusSuspendingEventSequentially(
            value = value,
            eventKey = eventKey,
            scopeName = scopeName,
            isSticky = isSticky,
            dispatcher = dispatcher,
            logger = logger,
            errorHandler = errorHandler,
            diagnosticSession = session,
            onReceived = onReceived
        )
    }
}

@Suppress("UNCHECKED_CAST")
internal suspend fun <T : Any> handleReceivedFlowBusEventSequentially(
    value: Any,
    eventKey: EventKey<out Any>? = null,
    scopeName: String? = null,
    isSticky: Boolean = false,
    dispatcher: CoroutineDispatcher? = null,
    logger: FlowBusLogger = FlowBusLogger.None,
    errorHandler: FlowBusErrorHandler = FlowBusErrorHandler.Rethrow,
    onReceived: (T) -> Unit
) {
    handleReceivedFlowBusSuspendingEventSequentially<T>(
        value = value,
        eventKey = eventKey,
        scopeName = scopeName,
        isSticky = isSticky,
        dispatcher = dispatcher,
        logger = logger,
        errorHandler = errorHandler,
        onReceived = { onReceived(it) }
    )
}

@Suppress("UNCHECKED_CAST")
private suspend fun <T : Any> handleReceivedFlowBusSuspendingEventSequentially(
    value: Any,
    eventKey: EventKey<out Any>?,
    scopeName: String?,
    isSticky: Boolean,
    dispatcher: CoroutineDispatcher?,
    logger: FlowBusLogger,
    errorHandler: FlowBusErrorHandler,
    diagnosticSession: FlowBusDiagnosticSession? = null,
    onReceived: suspend (T) -> Unit
) {
    val expectedType = eventKey?.valueType

    if (expectedType != null && !expectedType.isInstance(value)) {
        val exception = ClassCastException(
            "Expected ${expectedType.qualifiedName} for event '${eventKey.name}', but received ${value::class.qualifiedName}."
        )
        diagnosticSession?.failure(FlowBusDiagnosticFailurePhase.ValueCast, exception)
        logger.warnSafely("Subscriber received an event with an unexpected type.", exception)
        errorHandler.handle(
            context = FlowBusErrorContext(
                eventName = eventKey.name,
                expectedValueType = expectedType,
                actualValueType = value::class,
                isSticky = isSticky,
                scopeName = scopeName,
                phase = FlowBusErrorPhase.ValueCast,
                dispatcher = dispatcher?.toString()
            ),
            throwable = exception
        )
        return
    }

    val typedValue = try {
        value as T
    } catch (e: ClassCastException) {
        diagnosticSession?.failure(FlowBusDiagnosticFailurePhase.ValueCast, e)
        logger.warnSafely("Subscriber received an event with an unexpected type.", e)
        errorHandler.handle(
            context = FlowBusErrorContext(
                eventName = eventKey?.name,
                expectedValueType = expectedType,
                actualValueType = value::class,
                isSticky = isSticky,
                scopeName = scopeName,
                phase = FlowBusErrorPhase.ValueCast,
                dispatcher = dispatcher?.toString()
            ),
            throwable = e
        )
        return
    }

    try {
        if (dispatcher == null) {
            onReceived(typedValue)
        } else {
            withContext(dispatcher) {
                onReceived(typedValue)
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        diagnosticSession?.failure(FlowBusDiagnosticFailurePhase.SubscriberCallback, e)
        logger.warnSafely("Subscriber callback failed.", e)
        errorHandler.handle(
            context = FlowBusErrorContext(
                eventName = eventKey?.name,
                expectedValueType = eventKey?.valueType,
                actualValueType = value::class,
                isSticky = isSticky,
                scopeName = scopeName,
                phase = FlowBusErrorPhase.SubscriberCallback,
                dispatcher = dispatcher?.toString()
            ),
            throwable = e
        )
    }
}

private fun FlowBusLogger.warnSafely(message: String, error: Throwable) {
    try {
        warn(FLOWBUS_TAG, message, error)
    } catch (_: Exception) {
        // 日志是辅助路径；其普通异常不能阻止原异常进入既定 errorHandler。
    }
}
