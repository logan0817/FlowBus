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
 * 该函数会逐个执行 [onReceived]，不会并发处理同一条订阅链路上的事件。
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