package com.logan.flowbus

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
internal class EventFlowStore(
    private val normalExtraBufferCapacity: Int = DEFAULT_NORMAL_EXTRA_BUFFER_CAPACITY,
    private val stickyExtraBufferCapacity: Int = DEFAULT_STICKY_EXTRA_BUFFER_CAPACITY
) {
    init {
        require(normalExtraBufferCapacity >= 0) { "normalExtraBufferCapacity must be >= 0" }
        require(stickyExtraBufferCapacity >= 0) { "stickyExtraBufferCapacity must be >= 0" }
    }

    private val normalEventFlows: MutableMap<String, MutableSharedFlow<Any>> = ConcurrentHashMap()
    private val stickyEventFlows: MutableMap<String, MutableSharedFlow<Any>> = ConcurrentHashMap()

    fun getEventFlow(eventName: String, isSticky: Boolean): MutableSharedFlow<Any> {
        val targetMap = if (isSticky) stickyEventFlows else normalEventFlows
        return targetMap.getOrPut(eventName) {
            val replay = if (isSticky) 1 else 0
            val extraBufferCapacity = if (isSticky) stickyExtraBufferCapacity else normalExtraBufferCapacity
            MutableSharedFlow(
                replay = replay,
                // Keep the bus bounded. FlowBus is an event-dispatch helper, not an unbounded queue.
                extraBufferCapacity = extraBufferCapacity,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }
    }

    fun post(eventName: String, value: Any, isSticky: Boolean): Boolean {
        return getEventFlow(eventName, isSticky).tryEmit(value)
    }

    fun removeStickyEvent(eventName: String) {
        stickyEventFlows.remove(eventName)
    }

    fun clearStickyEvent(eventName: String) {
        stickyEventFlows[eventName]?.resetReplayCache()
    }

    internal fun hasEventFlow(eventName: String, isSticky: Boolean): Boolean {
        val targetMap = if (isSticky) stickyEventFlows else normalEventFlows
        return targetMap.containsKey(eventName)
    }

    internal fun stickyReplayCache(eventName: String): List<Any> {
        return stickyEventFlows[eventName]?.replayCache.orEmpty()
    }

    companion object {
        internal const val DEFAULT_NORMAL_EXTRA_BUFFER_CAPACITY = 64
        internal const val DEFAULT_STICKY_EXTRA_BUFFER_CAPACITY = 0
    }
}
