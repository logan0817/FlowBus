package com.logan.flowbus

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.ConcurrentHashMap

@OptIn(ExperimentalCoroutinesApi::class)
internal class EventFlowStore {
    private val normalEventFlows: MutableMap<String, MutableSharedFlow<Any>> = ConcurrentHashMap()
    private val stickyEventFlows: MutableMap<String, MutableSharedFlow<Any>> = ConcurrentHashMap()

    fun getEventFlow(eventName: String, isSticky: Boolean): MutableSharedFlow<Any> {
        val targetMap = if (isSticky) stickyEventFlows else normalEventFlows
        return targetMap.getOrPut(eventName) {
            MutableSharedFlow(
                replay = if (isSticky) 1 else 0,
                extraBufferCapacity = Int.MAX_VALUE,
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
}
