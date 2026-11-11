package com.logan.flowbus.core

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.transform
import java.util.TreeMap
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn
import kotlin.reflect.KClass

internal class FlowBusStore(
    private val config: FlowBusConfig
) {
    private val normalEventFlows = ConcurrentHashMap<String, MutableSharedFlow<Any>>()
    private val stickyEventFlows = ConcurrentHashMap<String, StickyEventFlow>()
    private val channelBindings = ConcurrentHashMap<String, ChannelBinding>()
    private val registrationLock = Any()
    private val defaultChannelConfig = FlowBusChannelConfig().resolve(config)
    private val eventMetrics = ConcurrentHashMap<EventFlowIdentity, FlowBusMetrics>()
    private val stickyReplayLock = Any()

    fun <T : Any> configureChannel(key: EventKey<T>, channelConfig: FlowBusChannelConfig) {
        bindChannel(key, channelConfig.resolve(config))
    }

    fun <T : Any> post(key: EventKey<T>, value: T, isSticky: Boolean): Boolean {
        return postResult(key = key, value = value, isSticky = isSticky, scopeName = null).accepted
    }

    fun <T : Any> postResult(
        key: EventKey<T>,
        value: T,
        isSticky: Boolean,
        scopeName: String?
    ): FlowBusPostResult {
        val channelConfig = bindChannel(key)
        val overflowPolicy = if (isSticky) channelConfig.stickyOverflowPolicy else channelConfig.normalOverflowPolicy
        var stickyReplayCount = 0
        val subscriptionCount: Int
        val accepted = if (isSticky) {
            val (flow, stickyValue) = synchronized(stickyReplayLock) {
                val flow = getStickyEventFlow(key.name, channelConfig)
                subscriptionCount = flow.subscriptionCount
                flow to flow.prepare(value)
            }
            val accepted = flow.tryEmit(stickyValue)
            stickyReplayCount = flow.replaySize
            accepted
        } else {
            val flow = getNormalEventFlow(key.name, channelConfig)
            subscriptionCount = flow.subscriptionCount.value
            flow.tryEmit(value)
        }
        val result = FlowBusPostResult(
            eventName = key.name,
            scopeName = scopeName,
            isSticky = isSticky,
            accepted = accepted,
            outcome = resolvePostOutcome(accepted = accepted, subscriptionCount = subscriptionCount, overflowPolicy = overflowPolicy),
            subscriptionCount = subscriptionCount,
            stickyReplayCount = stickyReplayCount,
            overflowPolicy = overflowPolicy
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
        val channelConfig = bindChannel(key)
        if (isSticky) {
            emitSticky(key = key, value = value, scopeName = scopeName, channelConfig = channelConfig)
            return
        }

        val flow = getNormalEventFlow(key.name, channelConfig)
        val subscriptionCount = flow.subscriptionCount.value
        flow.emit(value)
        val result = FlowBusPostResult(
            eventName = key.name,
            scopeName = scopeName,
            isSticky = false,
            accepted = true,
            outcome = resolvePostOutcome(accepted = true, subscriptionCount = subscriptionCount, overflowPolicy = channelConfig.normalOverflowPolicy),
            subscriptionCount = subscriptionCount,
            stickyReplayCount = 0,
            overflowPolicy = channelConfig.normalOverflowPolicy
        )
        recordResult(key.name, isSticky = false, result = result)
    }

    private suspend fun <T : Any> emitSticky(
        key: EventKey<T>,
        value: T,
        scopeName: String?,
        channelConfig: ResolvedFlowBusChannelConfig
    ) {
        val (flow, stickyValue) = synchronized(stickyReplayLock) {
            val flow = getStickyEventFlow(key.name, channelConfig)
            flow to flow.prepare(value)
        }
        val subscriptionCount = flow.subscriptionCount
        flow.emit(stickyValue)
        val stickyReplayCount = flow.replaySize
        val result = FlowBusPostResult(
            eventName = key.name,
            scopeName = scopeName,
            isSticky = true,
            accepted = true,
            outcome = resolvePostOutcome(accepted = true, subscriptionCount = subscriptionCount, overflowPolicy = channelConfig.stickyOverflowPolicy),
            subscriptionCount = subscriptionCount,
            stickyReplayCount = stickyReplayCount,
            overflowPolicy = channelConfig.stickyOverflowPolicy
        )
        recordResult(key.name, isSticky = true, result = result)
    }

    @Deprecated(
        message = "Use emit(key, value, isSticky, scopeName) so inspector metrics can include scope information.",
        level = DeprecationLevel.HIDDEN
    )
    suspend fun <T : Any> emit(key: EventKey<T>, value: T, isSticky: Boolean) {
        emit(key = key, value = value, isSticky = isSticky, scopeName = null)
    }

    fun <T : Any> flow(key: EventKey<T>, isSticky: Boolean): Flow<T> {
        return flow(key, isSticky, onSubscribed = null)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> flow(key: EventKey<T>, isSticky: Boolean, onSubscribed: (() -> Unit)?): Flow<T> {
        val channelConfig = bindChannel(key)
        return if (isSticky) {
            getStickyEventFlow(key.name, channelConfig).asFlow(onSubscribed) as Flow<T>
        } else {
            val flow = getNormalEventFlow(key.name, channelConfig)
            if (onSubscribed == null) flow as Flow<T>
            else flow.onSubscription {
                currentCoroutineContext().ensureActive()
                onSubscribed()
            } as Flow<T>
        }
    }

    fun inspectChannel(key: EventKey<*>): FlowBusChannelSnapshot? {
        val binding = channelBindings[key.name] ?: return null
        val expectedType = key.valueType
        require(binding.valueType == null || expectedType == null || binding.valueType == expectedType) {
            keyTypeConflictMessage(key.name, binding.valueType, checkNotNull(expectedType))
        }
        val channelConfig = binding.config
        val normalFlow = normalEventFlows[key.name]
        val stickyFlow = stickyEventFlows[key.name]
        return FlowBusChannelSnapshot(
            eventName = key.name,
            valueTypeName = binding.valueType?.qualifiedName,
            normalBufferCapacity = channelConfig.normalBufferCapacity,
            normalOverflowPolicy = channelConfig.normalOverflowPolicy,
            stickyReplay = channelConfig.stickyReplay,
            stickyExtraBufferCapacity = channelConfig.stickyExtraBufferCapacity,
            stickyOverflowPolicy = channelConfig.stickyOverflowPolicy,
            hasNormalFlow = normalFlow != null,
            hasStickyFlow = stickyFlow != null,
            normalSubscriptionCount = normalFlow?.subscriptionCount?.value ?: 0,
            stickySubscriptionCount = stickyFlow?.subscriptionCount ?: 0
        )
    }

    fun <T : Any> clearSticky(key: EventKey<T>) {
        if (!prepareCleanupKey(key, stickyEventFlows.containsKey(key.name))) return
        synchronized(stickyReplayLock) {
            stickyEventFlows[key.name]?.resetReplayCache()
        }
    }

    fun <T : Any> removeSticky(key: EventKey<T>) {
        if (!prepareCleanupKey(key, stickyEventFlows.containsKey(key.name))) return
        synchronized(stickyReplayLock) {
            val flow = stickyEventFlows[key.name] ?: return
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
        bindChannel(key)
        normalEventFlows.remove(key.name, flow)
    }

    fun clearAll() {
        synchronized(registrationLock) {
            normalEventFlows.clear()
            synchronized(stickyReplayLock) {
                stickyEventFlows.values.forEach { it.resetReplayCache() }
                stickyEventFlows.clear()
            }
            channelBindings.clear()
            eventMetrics.clear()
        }
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
        if (!prepareCleanupKey(key, stickyEventFlows.containsKey(key.name))) return null
        return synchronized(stickyReplayLock) {
            val flow = stickyEventFlows[key.name] ?: return@synchronized null
            val latest = flow.consumeLatestAndReset() ?: return@synchronized null
            @Suppress("UNCHECKED_CAST")
            latest as T
        }
    }

    fun snapshot(): List<FlowBusEventSnapshot> {
        val eventNames = buildSet {
            addAll(channelBindings.keys)
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
                    valueTypeName = channelBindings[eventName]?.valueType?.qualifiedName,
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

    private fun bindChannel(
        key: EventKey<*>,
        requestedConfig: ResolvedFlowBusChannelConfig? = null
    ): ResolvedFlowBusChannelConfig {
        val existing = channelBindings[key.name]
        if (existing != null && (key.valueType == null || existing.valueType == key.valueType)) {
            requireSameConfig(key.name, existing.config, requestedConfig)
            return existing.config
        }
        return synchronized(registrationLock) {
            val binding = channelBindings[key.name]
            val expectedType = key.valueType
            require(binding?.valueType == null || expectedType == null || binding.valueType == expectedType) {
                keyTypeConflictMessage(key.name, binding?.valueType, checkNotNull(expectedType))
            }
            if (binding != null) requireSameConfig(key.name, binding.config, requestedConfig)
            val resolved = binding?.config ?: requestedConfig ?: defaultChannelConfig
            // 类型与策略一起发布；失败不留下半注册。raw key 后续可由带类型的 key 补全。
            if (binding == null || (binding.valueType == null && expectedType != null)) {
                channelBindings[key.name] = ChannelBinding(expectedType, resolved)
            }
            resolved
        }
    }

    private fun requireSameConfig(
        eventName: String,
        existing: ResolvedFlowBusChannelConfig,
        requested: ResolvedFlowBusChannelConfig?
    ) {
        check(requested == null || existing == requested) {
            "Event key '$eventName' already has a bound configuration. Configure it before first use."
        }
    }

    private fun prepareCleanupKey(key: EventKey<*>, hasFlow: Boolean): Boolean {
        if (hasFlow) bindChannel(key) else requireKnownKeyTypeMatches(key)
        return hasFlow
    }

    private fun requireKnownKeyTypeMatches(key: EventKey<*>) {
        val expectedType = key.valueType ?: return
        val existingType = channelBindings[key.name]?.valueType ?: return
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

    private fun getNormalEventFlow(
        eventName: String,
        channelConfig: ResolvedFlowBusChannelConfig
    ): MutableSharedFlow<Any> {
        // ConcurrentMap.getOrPut 用 putIfAbsent 保证最终只返回获胜的实例，兼容 Android API 21。
        return normalEventFlows.getOrPut(eventName) {
            MutableSharedFlow(
                replay = 0,
                extraBufferCapacity = channelConfig.normalBufferCapacity,
                onBufferOverflow = channelConfig.normalOverflowPolicy
            )
        }
    }

    private fun getStickyEventFlow(
        eventName: String,
        channelConfig: ResolvedFlowBusChannelConfig
    ): StickyEventFlow {
        return stickyEventFlows.getOrPut(eventName) {
            StickyEventFlow(
                replay = channelConfig.stickyReplay,
                extraBufferCapacity = channelConfig.stickyExtraBufferCapacity,
                onBufferOverflow = channelConfig.stickyOverflowPolicy
            )
        }
    }

    private fun resolvePostOutcome(
        accepted: Boolean,
        subscriptionCount: Int,
        overflowPolicy: BufferOverflow
    ): FlowBusPostOutcome {
        if (!accepted) return FlowBusPostOutcome.RejectedByBuffer
        if (subscriptionCount == 0) return FlowBusPostOutcome.AcceptedWithoutActiveSubscriber
        if (overflowPolicy == BufferOverflow.DROP_OLDEST) {
            return FlowBusPostOutcome.AcceptedWithDropOldestPolicy
        }
        if (overflowPolicy == BufferOverflow.DROP_LATEST) {
            return FlowBusPostOutcome.AcceptedWithDropLatestPolicy
        }
        return FlowBusPostOutcome.Accepted
    }

    private class ChannelBinding(
        val valueType: KClass<out Any>?,
        val config: ResolvedFlowBusChannelConfig
    )

    private data class EventFlowIdentity(
        val eventName: String,
        val isSticky: Boolean
    )

    private class StickyValue(
        val generation: Long,
        val value: Any
    ) {
        private val committed = AtomicBoolean(false)
        var order: Long = -1L

        fun commit(): Boolean = committed.compareAndSet(false, true)

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
        private val replayValues = TreeMap<Long, StickyValue>()
        // 默认 replay=1 只需比较序号，避免每次发送分配树节点和装箱键。
        private var singleReplayValue: StickyValue? = null
        private val commitLock = Any()
        private var nextOrder = 0L
        private var publicationDepth = 0
        private var deferredResumptions: MutableList<() -> Unit>? = null
        @Volatile
        private var generation = 0L

        val replayCache: List<Any>
            get() = synchronized(commitLock) {
                if (replayCapacity == 1) singleReplayValue?.let { listOf(it.value) } ?: emptyList()
                else replayValues.values.map { it.value }
            }

        val replaySize: Int
            get() = synchronized(commitLock) {
                if (replayCapacity == 1) if (singleReplayValue == null) 0 else 1
                else replayValues.size
            }

        val subscriptionCount: Int
            get() = backing.subscriptionCount.value

        suspend fun emit(stickyValue: StickyValue) {
            suspendCoroutineUninterceptedOrReturn<Unit> { caller ->
                withPublicationLock {
                    stickyValue.order = nextOrder++
                    // 在锁内启动到底层首次挂起，序号与 SharedFlow 入队一致；不跨挂起持锁。
                    val result = (suspend { backing.emit(StickyDelivery.live(stickyValue)) })
                        .startCoroutineUninterceptedOrReturn(object : Continuation<Unit> {
                            override val context = caller.context

                            override fun resumeWith(result: Result<Unit>) {
                                withPublicationLock {
                                    if (result.isSuccess) commitIfCurrent(stickyValue)
                                    deferResumption { caller.resumeWith(result) }
                                }
                            }
                        })
                    if (result !== COROUTINE_SUSPENDED) commitIfCurrent(stickyValue)
                    result
                }
            }
        }

        fun tryEmit(stickyValue: StickyValue): Boolean = withPublicationLock {
            stickyValue.order = nextOrder++
            backing.tryEmit(StickyDelivery.live(stickyValue)).also { accepted ->
                if (accepted) commitIfCurrent(stickyValue)
            }
        }

        fun resetReplayCache() {
            synchronized(commitLock) {
                generation += 1
                singleReplayValue = null
                replayValues.clear()
            }
        }

        fun consumeLatestAndReset(): Any? {
            return synchronized(commitLock) {
                val latest = if (replayCapacity == 1) singleReplayValue?.value
                    else replayValues.lastEntry()?.value?.value
                if (latest == null) return@synchronized null
                generation += 1
                singleReplayValue = null
                replayValues.clear()
                latest
            }
        }

        fun asFlow(onSubscribed: (() -> Unit)?): Flow<Any> = flow {
            // 每次 collect 的 slot 登记和历史快照同属一个临界区，live 不会重复进入本次 replay。
            suspendCoroutineUninterceptedOrReturn<Unit> { caller ->
                withPublicationLock {
                    (suspend {
                        backing.onSubscription {
                            val replaySnapshot = replaySnapshot()
                            awaitPublicationUnlock()
                            currentCoroutineContext().ensureActive()
                            if (onSubscribed != null) {
                                onSubscribed()
                            }
                            replaySnapshot.values.forEach { stickyValue ->
                                if (replaySnapshot.generation == generation) {
                                    emit(StickyDelivery.replay(stickyValue, replaySnapshot.generation))
                                }
                            }
                        }.transform { delivery ->
                            awaitPublicationUnlock()
                            currentCoroutineContext().ensureActive()
                            val stickyValue = delivery.stickyValue
                            val replayGeneration = delivery.replayGeneration
                            val shouldEmit = if (replayGeneration == null) {
                                stickyValue.generation == generation || stickyValue.isCommitted()
                            } else {
                                replayGeneration == generation &&
                                    stickyValue.generation == generation &&
                                    stickyValue.isCommitted()
                            }
                            if (shouldEmit) emit(stickyValue.value)
                        }.collect { emit(it) }
                    }).startCoroutineUninterceptedOrReturn(object : Continuation<Unit> {
                        override val context = caller.context

                        override fun resumeWith(result: Result<Unit>) {
                            withPublicationLock { deferResumption { caller.resumeWith(result) } }
                        }
                    })
                }
            }
        }

        fun prepare(value: Any): StickyValue {
            return synchronized(commitLock) {
                StickyValue(generation = generation, value = value)
            }
        }

        private fun replaySnapshot(): StickyReplaySnapshot {
            return synchronized(commitLock) {
                StickyReplaySnapshot(
                    generation = generation,
                    values = if (replayCapacity == 1) listOfNotNull(singleReplayValue)
                        else replayValues.values.toList()
                )
            }
        }

        private fun commitIfCurrent(stickyValue: StickyValue) {
            synchronized(commitLock) {
                if (stickyValue.generation == generation && stickyValue.commit()) {
                    if (replayCapacity == 1) {
                        if (singleReplayValue == null || stickyValue.order > singleReplayValue!!.order) {
                            singleReplayValue = stickyValue
                        }
                    } else if (replayCapacity > 1) {
                        replayValues[stickyValue.order] = stickyValue
                        while (replayValues.size > replayCapacity) {
                            replayValues.pollFirstEntry()
                        }
                    }
                }
            }
        }

        private suspend fun awaitPublicationUnlock() {
            synchronized(commitLock) {
                if (publicationDepth == 0) return
            }
            // 延后到当前临界区退出，但保留 UNDISPATCHED 建立订阅时的同步通知语义。
            suspendCoroutineUninterceptedOrReturn<Unit> { continuation ->
                deferResumption {
                    val result = runCatching { continuation.context.ensureActive() }
                    continuation.resumeWith(result)
                }
                COROUTINE_SUSPENDED
            }
        }

        private fun deferResumption(resume: () -> Unit) {
            val pending = deferredResumptions ?: mutableListOf<() -> Unit>().also { deferredResumptions = it }
            pending.add(resume)
        }

        private inline fun <T> withPublicationLock(block: () -> T): T {
            var resumptions: List<() -> Unit>? = null
            var failure: Throwable? = null
            return try {
                synchronized(commitLock) {
                    publicationDepth++
                    try {
                        block()
                    } finally {
                        publicationDepth--
                        if (publicationDepth == 0) {
                            resumptions = deferredResumptions
                            deferredResumptions = null
                        }
                    }
                }
            } catch (error: Throwable) {
                failure = error
                throw error
            } finally {
                // SharedFlow 可以立即恢复 Unconfined 协程；用户回调与发送调用者都在解锁后继续。
                var resumeFailure: Throwable? = null
                resumptions?.forEach { resume ->
                    try {
                        resume()
                    } catch (error: Throwable) {
                        if (resumeFailure == null) resumeFailure = error
                        else if (resumeFailure !== error) resumeFailure!!.addSuppressed(error)
                    }
                }
                resumeFailure?.let { error ->
                    if (failure == null) throw error
                    if (failure !== error) failure.addSuppressed(error)
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
