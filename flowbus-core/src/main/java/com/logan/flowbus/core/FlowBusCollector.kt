package com.logan.flowbus.core

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
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
    flow.collect { value ->
        handleReceivedFlowBusEventSequentially(
            value = value,
            eventKey = eventKey,
            scopeName = scopeName,
            isSticky = isSticky,
            dispatcher = dispatcher,
            logger = logger,
            errorHandler = errorHandler,
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
    val dispatcherName = dispatcher?.toString()
    val expectedType = eventKey?.valueType

    if (expectedType != null && !expectedType.isInstance(value)) {
        val exception = ClassCastException(
            "Expected ${expectedType.qualifiedName} for event '${eventKey.name}', but received ${value::class.qualifiedName}."
        )
        logger.warn(FLOWBUS_TAG, "Subscriber received an event with an unexpected type.", exception)
        errorHandler.handle(
            context = FlowBusErrorContext(
                eventName = eventKey.name,
                expectedValueType = expectedType,
                actualValueType = value::class,
                isSticky = isSticky,
                scopeName = scopeName,
                phase = FlowBusErrorPhase.ValueCast,
                dispatcher = dispatcherName
            ),
            throwable = exception
        )
        return
    }

    val typedValue = try {
        value as T
    } catch (e: ClassCastException) {
        logger.warn(FLOWBUS_TAG, "Subscriber received an event with an unexpected type.", e)
        errorHandler.handle(
            context = FlowBusErrorContext(
                eventName = eventKey?.name,
                expectedValueType = expectedType,
                actualValueType = value::class,
                isSticky = isSticky,
                scopeName = scopeName,
                phase = FlowBusErrorPhase.ValueCast,
                dispatcher = dispatcherName
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
        logger.warn(FLOWBUS_TAG, "Subscriber callback failed.", e)
        errorHandler.handle(
            context = FlowBusErrorContext(
                eventName = eventKey?.name,
                expectedValueType = eventKey?.valueType,
                actualValueType = value::class,
                isSticky = isSticky,
                scopeName = scopeName,
                phase = FlowBusErrorPhase.SubscriberCallback,
                dispatcher = dispatcherName
            ),
            throwable = e
        )
    }
}
