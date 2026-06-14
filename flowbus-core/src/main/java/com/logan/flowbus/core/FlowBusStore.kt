package com.logan.flowbus.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.transform
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.reflect.KClass

internal class FlowBusStore(
    private val config: FlowBusConfig
) {
    private val normalEventFlows = ConcurrentHashMap<String, MutableSharedFlow<Any>>()
    private val stickyEventFlows = ConcurrentHashMap<String, StickyEventFlow>()
    private val keyTypes = ConcurrentHashMap<String, KClass<out Any>>()
    private val eventMetrics = ConcurrentHashMap<EventFlowIdentity, FlowBusMetrics>()
    private val stickyReplayLock = Any()

    fun <T : Any> post(key: EventKey<T>, value: T, isSticky: Boolean): Boolean {
        return postResult(key = key, value = value, isSticky = isSticky, scopeName = null).accepted
    }

    fun <T : Any> postResult(
        key: EventKey<T>,
        value: T,
        isSticky: Boolean,
        scopeName: String?
    ): FlowBusPostResult {
        registerKeyType(key)
        var stickyReplayCount = 0
        val subscriptionCount: Int
        val accepted = if (isSticky) {
            synchronized(stickyReplayLock) {
                val flow = getStickyEventFlow(key.name)
                subscriptionCount = flow.subscriptionCount
                val accepted = flow.tryEmit(value)
                stickyReplayCount = flow.replayCache.size
                accepted
            }
        } else {
            val flow = getNormalEventFlow(key.name)
            subscriptionCount = flow.subscriptionCount.value
            flow.tryEmit(value)
        }
        val result = FlowBusPostResult(
            eventName = key.name,
            scopeName = scopeName,
            isSticky = isSticky,
            accepted = accepted,
            outcome = resolvePostOutcome(accepted = accepted, subscriptionCount = subscriptionCount),
            subscriptionCount = subscriptionCount,
            stickyReplayCount = stickyReplayCount,
            overflowPolicy = config.overflowPolicy
        )
        recordResult(key.name, isSticky, result)
        return result
    }

    suspend fun <T : Any> emit(
        key: EventKey<T>,
        value: T,
        isSticky: Boolean,
        scopeName: String? = null
    ) {
        registerKeyType(key)
        if (isSticky) {
            emitSticky(key = key, value = value, scopeName = scopeName)
            return
        }

        val flow = getNormalEventFlow(key.name)
        val subscriptionCount = flow.subscriptionCount.value
        flow.emit(value)
        val result = FlowBusPostResult(
            eventName = key.name,
            scopeName = scopeName,
            isSticky = false,
            accepted = true,
            outcome = resolvePostOutcome(accepted = true, subscriptionCount = subscriptionCount),
            subscriptionCount = subscriptionCount,
            stickyReplayCount = 0,
            overflowPolicy = config.overflowPolicy
        )
        recordResult(key.name, isSticky = false, result = result)
    }

    private suspend fun <T : Any> emitSticky(
        key: EventKey<T>,
        value: T,
        scopeName: String?
    ) {
        if (!hasStickyReplayStorage()) {
            val flow = getStickyEventFlow(key.name)
            val subscriptionCount = flow.subscriptionCount
            flow.emit(value)
            val result = FlowBusPostResult(
                eventName = key.name,
                scopeName = scopeName,
                isSticky = true,
                accepted = true,
                outcome = resolvePostOutcome(accepted = true, subscriptionCount = subscriptionCount),
                subscriptionCount = subscriptionCount,
                stickyReplayCount = 0,
                overflowPolicy = config.overflowPolicy
            )
            recordResult(key.name, isSticky = true, result = result)
            return
        }

        val flow = synchronized(stickyReplayLock) {
            getStickyEventFlow(key.name)
        }
        val subscriptionCount = flow.subscriptionCount
        flow.emit(value)
        val stickyReplayCount = synchronized(stickyReplayLock) {
            flow.replayCache.size
        }
        val result = FlowBusPostResult(
            eventName = key.name,
            scopeName = scopeName,
            isSticky = true,
            accepted = true,
            outcome = resolvePostOutcome(accepted = true, subscriptionCount = subscriptionCount),
            subscriptionCount = subscriptionCount,
            stickyReplayCount = stickyReplayCount,
            overflowPolicy = config.overflowPolicy
        )
        recordResult(key.name, isSticky = true, result = result)
    }

    private fun hasStickyReplayStorage(): Boolean {
        return config.stickyReplay > 0 || config.stickyExtraBufferCapacity > 0
    }

    @Deprecated(
        message = "Use emit(key, value, isSticky, scopeName) so inspector metrics can include scope information.",
        level = DeprecationLevel.HIDDEN
    )
    suspend fun <T : Any> emit(key: EventKey<T>, value: T, isSticky: Boolean) {
        emit(key = key, value = value, isSticky = isSticky, scopeName = null)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> flow(key: EventKey<T>, isSticky: Boolean): Flow<T> {
        registerKeyType(key)
        return if (isSticky) {
            getStickyEventFlow(key.name).asFlow() as Flow<T>
        } else {
            getNormalEventFlow(key.name) as Flow<T>
        }
    }

    fun <T : Any> clearSticky(key: EventKey<T>) {
        synchronized(stickyReplayLock) {
            val flow = stickyEventFlows[key.name]
            if (flow == null) {
                requireKnownKeyTypeMatches(key)
                return
            }
            registerKeyType(key)
            flow.resetReplayCache()
        }
    }

    fun <T : Any> removeSticky(key: EventKey<T>) {
        synchronized(stickyReplayLock) {
            val flow = stickyEventFlows[key.name]
            if (flow == null) {
                requireKnownKeyTypeMatches(key)
                return
            }
            registerKeyType(key)
            stickyEventFlows.remove(key.name, flow)
            flow.resetReplayCache()
        }
    }

    fun <T : Any> removeEvent(key: EventKey<T>) {
        val flow = normalEventFlows[key.name]
        if (flow == null) {
            requireKnownKeyTypeMatches(key)
            return
        }
        registerKeyType(key)
        normalEventFlows.remove(key.name, flow)
    }

    fun clearAll() {
        normalEventFlows.clear()
        synchronized(stickyReplayLock) {
            stickyEventFlows.values.forEach { it.resetReplayCache() }
            stickyEventFlows.clear()
        }
        keyTypes.clear()
        eventMetrics.clear()
    }

    fun hasEventFlow(eventName: String, isSticky: Boolean): Boolean {
        val targetMap = if (isSticky) stickyEventFlows else normalEventFlows
        return targetMap.containsKey(eventName)
    }

    fun stickyReplayCache(eventName: String): List<Any> {
        return synchronized(stickyReplayLock) {
            stickyEventFlows[eventName]?.replayCache.orEmpty()
        }
    }

    fun <T : Any> consumeStickyLatest(key: EventKey<T>): T? {
        return synchronized(stickyReplayLock) {
            val flow = stickyEventFlows[key.name]
            if (flow == null) {
                requireKnownKeyTypeMatches(key)
                return@synchronized null
            }
            registerKeyType(key)
            val latest = flow.consumeLatestAndReset() ?: return@synchronized null
            @Suppress("UNCHECKED_CAST")
            latest as T
        }
    }

    fun snapshot(): List<FlowBusEventSnapshot> {
        val eventNames = buildSet {
            addAll(keyTypes.keys)
            addAll(normalEventFlows.keys)
            addAll(stickyEventFlows.keys)
        }
        return eventNames
            .map { eventName ->
                val normalMetrics = eventMetrics[EventFlowIdentity(eventName, isSticky = false)]
                    ?.snapshot()
                    ?: emptyFlowBusEventMetricsSnapshot()
                val stickyMetrics = eventMetrics[EventFlowIdentity(eventName, isSticky = true)]
                    ?.snapshot()
                    ?: emptyFlowBusEventMetricsSnapshot()
                FlowBusEventSnapshot(
                    eventName = eventName,
                    valueTypeName = keyTypes[eventName]?.qualifiedName,
                    hasNormalFlow = normalEventFlows.containsKey(eventName),
                    hasStickyFlow = stickyEventFlows.containsKey(eventName),
                    stickyReplayCount = stickyReplayCount(eventName),
                    subscriptionCount = subscriptionCount(eventName),
                    metrics = combineFlowBusEventMetricsSnapshots(normalMetrics, stickyMetrics),
                    normalMetrics = normalMetrics,
                    stickyMetrics = stickyMetrics
                )
            }
            .sortedBy { it.eventName }
    }

    private fun stickyReplayCount(eventName: String): Int {
        return synchronized(stickyReplayLock) {
            stickyEventFlows[eventName]?.replayCache?.size ?: 0
        }
    }

    fun subscriptionCount(eventName: String): Int {
        val normalCount = normalEventFlows[eventName]?.subscriptionCount?.value ?: 0
        val stickyCount = stickyEventFlows[eventName]?.subscriptionCount ?: 0
        return normalCount + stickyCount
    }

    private fun recordResult(eventName: String, isSticky: Boolean, result: FlowBusPostResult) {
        eventMetrics.getOrPut(EventFlowIdentity(eventName, isSticky)) { FlowBusMetrics() }.record(result)
    }

    private fun registerKeyType(key: EventKey<*>) {
        val expectedType = key.valueType ?: return
        val existingType = keyTypes.putIfAbsent(key.name, expectedType)
        require(existingType == null || existingType == expectedType) {
            keyTypeConflictMessage(key.name, existingType, expectedType)
        }
    }

    private fun requireKnownKeyTypeMatches(key: EventKey<*>) {
        val expectedType = key.valueType ?: return
        val existingType = keyTypes[key.name] ?: return
        require(existingType == expectedType) {
            keyTypeConflictMessage(key.name, existingType, expectedType)
        }
    }

    private fun keyTypeConflictMessage(
        eventName: String,
        existingType: KClass<out Any>?,
        expectedType: KClass<out Any>
    ): String {
        return "Event key '$eventName' is already bound to ${existingType?.qualifiedName}, " +
            "cannot reuse it with ${expectedType.qualifiedName}."
    }

    private fun getNormalEventFlow(eventName: String): MutableSharedFlow<Any> {
        return normalEventFlows.computeIfAbsent(eventName) {
            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = config.normalBufferCapacity,
                onBufferOverflow = config.overflowPolicy
            )
        }
    }

    private fun getStickyEventFlow(eventName: String): StickyEventFlow {
        return stickyEventFlows.computeIfAbsent(eventName) {
            StickyEventFlow(
                replay = config.stickyReplay,
                extraBufferCapacity = config.stickyExtraBufferCapacity,
                onBufferOverflow = config.overflowPolicy
            )
        }
    }

    private fun resolvePostOutcome(accepted: Boolean, subscriptionCount: Int): FlowBusPostOutcome {
        if (!accepted) return FlowBusPostOutcome.RejectedByBuffer
        if (subscriptionCount == 0) return FlowBusPostOutcome.AcceptedWithoutActiveSubscriber
        if (config.overflowPolicy == BufferOverflow.DROP_OLDEST) {
            return FlowBusPostOutcome.AcceptedWithDropOldestPolicy
        }
        if (config.overflowPolicy == BufferOverflow.DROP_LATEST) {
            return FlowBusPostOutcome.AcceptedWithDropLatestPolicy
        }
        return FlowBusPostOutcome.Accepted
    }

    private data class EventFlowIdentity(
        val eventName: String,
        val isSticky: Boolean
    )

    private class StickyValue(
        val generation: Long,
        val value: Any
    ) {
        private val committed = AtomicBoolean(false)

        fun commit() {
            committed.set(true)
        }

        fun isCommitted(): Boolean {
            return committed.get()
        }
    }

    private class StickyEventFlow(
        replay: Int,
        extraBufferCapacity: Int,
        onBufferOverflow: BufferOverflow
    ) {
        private val replayCapacity = replay
        private val backing = MutableSharedFlow<StickyDelivery>(
            replay = 0,
            extraBufferCapacity = replay + extraBufferCapacity,
            onBufferOverflow = onBufferOverflow
        )
        private val replayValues = ArrayDeque<StickyValue>()
        private val commitLock = Any()
        @Volatile
        private var generation = 0L

        val replayCache: List<Any>
            get() = synchronized(commitLock) {
                replayValues.map { it.value }
            }

        val subscriptionCount: Int
            get() = backing.subscriptionCount.value

        suspend fun emit(value: Any) {
            val stickyValue = stickyValueOf(value)
            backing.emit(StickyDelivery.live(stickyValue))
            commitIfCurrent(stickyValue)
        }

        fun tryEmit(value: Any): Boolean {
            val stickyValue = stickyValueOf(value)
            val accepted = backing.tryEmit(StickyDelivery.live(stickyValue))
            if (accepted) {
                commitIfCurrent(stickyValue)
            }
            return accepted
        }

        fun resetReplayCache() {
            synchronized(commitLock) {
                generation += 1
                replayValues.clear()
            }
        }

        fun consumeLatestAndReset(): Any? {
            return synchronized(commitLock) {
                val latest = replayValues.lastOrNull()?.value ?: return@synchronized null
                generation += 1
                replayValues.clear()
                latest
            }
        }

        fun asFlow(): Flow<Any> = backing
            .onSubscription {
                val replaySnapshot = replaySnapshot()
                replaySnapshot.values.forEach { stickyValue ->
                    if (replaySnapshot.generation == generation) {
                        emit(StickyDelivery.replay(stickyValue, replaySnapshot.generation))
                    }
                }
            }
            .transform { delivery ->
                val stickyValue = delivery.stickyValue
                val replayGeneration = delivery.replayGeneration
                val shouldEmit = if (replayGeneration == null) {
                    stickyValue.generation == generation || stickyValue.isCommitted()
                } else {
                    replayGeneration == generation &&
                        stickyValue.generation == generation &&
                        stickyValue.isCommitted()
                }
                if (shouldEmit) {
                    emit(stickyValue.value)
                }
            }

        private fun stickyValueOf(value: Any): StickyValue {
            return synchronized(commitLock) {
                StickyValue(generation = generation, value = value)
            }
        }

        private fun replaySnapshot(): StickyReplaySnapshot {
            return synchronized(commitLock) {
                StickyReplaySnapshot(
                    generation = generation,
                    values = replayValues.toList()
                )
            }
        }

        private fun commitIfCurrent(stickyValue: StickyValue) {
            synchronized(commitLock) {
                if (stickyValue.generation == generation) {
                    stickyValue.commit()
                    if (replayCapacity > 0) {
                        replayValues.addLast(stickyValue)
                        while (replayValues.size > replayCapacity) {
                            replayValues.removeFirst()
                        }
                    }
                }
            }
        }

        private class StickyDelivery(
            val stickyValue: StickyValue,
            val replayGeneration: Long?
        ) {
            companion object {
                fun live(stickyValue: StickyValue): StickyDelivery {
                    return StickyDelivery(stickyValue = stickyValue, replayGeneration = null)
                }

                fun replay(stickyValue: StickyValue, replayGeneration: Long): StickyDelivery {
                    return StickyDelivery(stickyValue = stickyValue, replayGeneration = replayGeneration)
                }
            }
        }

        private class StickyReplaySnapshot(
            val generation: Long,
            val values: List<StickyValue>
        )
    }
}
