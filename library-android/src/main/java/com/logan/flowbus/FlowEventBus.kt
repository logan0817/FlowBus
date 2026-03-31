@file:OptIn(ExperimentalCoroutinesApi::class)

package com.logan.flowbus


import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * FlowEventBus: An Event Bus implemented using Kotlin Flow, inheriting from ViewModel.
 *
 * Inheriting ViewModel ensures the FlowEventBus has the same lifecycle as the ViewModel (usually
 * an Activity/Fragment), allows the use of viewModelScope for lifecycle binding, and automatically
 * cancels all internal coroutine tasks when the host is destroyed.
 *
 * FlowEventBus：基于 Kotlin Flow 实现的事件总线，继承自 ViewModel。
 *
 * 继承 ViewModel 确保了 FlowEventBus 的生命周期与 ViewModel 相同（通常是 Activity/Fragment），
 * 并且可以使用 viewModelScope 进行生命周期绑定，在宿主销毁时自动取消所有内部协程任务。
 *
 * @author logan
 * @email notwalnut@163.com
 * @date 2025/12/11
 */

class FlowEventBus : ViewModel() {
    private val TAG = FlowEventBus::class.java.simpleName
    private val eventFlowStore = EventFlowStore()

    /**
     * Subscribes to an event flow.
     * 订阅事件流。
     *
     * This method uses LifecycleOwner and repeatOnLifecycle to ensure collection starts when the host
     * is in the specified state and automatically pauses collection when the host enters the STOPPED
     * state, preventing memory leaks and unnecessary resource consumption.
     * 该方法基于 LifecycleOwner 和 repeatOnLifecycle 实现，确保在宿主处于指定状态时开始收集，
     * 并在宿主进入 STOPPED 状态时自动暂停收集，避免内存泄漏和不必要的资源消耗。
     *
     * @param lifecycleOwner The host lifecycle object (e.g., Activity/Fragment). 宿主生命周期对象。
     * @param eventName The event name. 事件名。
     * @param startState The minimum Lifecycle.State to trigger collection, defaults to STARTED. 触发收集的最小生命周期状态。
     * @param dispatcher The CoroutineDispatcher used for processing the received event. 用于处理接收到事件的协程调度器。
     * @param isSticky Whether to subscribe to the sticky event. 是否订阅粘性事件。
     * @param onReceived The event reception callback, generic T is the type of data carried by the event. 事件接收回调。
     * @return Returns the Job instance, which can be used to manually cancel the subscription. 返回 Job 实例，可用于手动取消订阅。
     */
    fun <T : Any> subscribeEvent(
        lifecycleOwner: LifecycleOwner,
        eventName: String,
        startState: Lifecycle.State = Lifecycle.State.STARTED,
        dispatcher: CoroutineDispatcher,
        isSticky: Boolean,
        onReceived: (T) -> Unit
    ): Job {
        Log.w(TAG, "subscribe:$eventName")
        return lifecycleOwner.lifecycleScope.launch {
            // Repeat the coroutine block when the host is in the specified lifecycle state.
            // 在指定生命周期状态下重复执行块内的协程。
            lifecycleOwner.repeatOnLifecycle(startState) {
                collectEventFlowSequentially(
                    flow = eventFlowStore.getEventFlow(eventName, isSticky),
                    dispatcher = dispatcher,
                    onReceived = onReceived
                )
            }
        }
    }

    /**
     * Subscribes to the event flow within the current coroutine scope.
     * 在当前协程作用域内订阅事件流。
     *
     * Suitable for ViewModel or other coroutine environments that do not require LifecycleOwner binding.
     * Note: The caller needs to manage the lifecycle of this coroutine itself.
     * 适用于 ViewModel 或其他无需绑定 LifecycleOwner 的协程环境。注意：调用者需要自行管理该协程的生命周期。
     *
     * @param eventName The event name. 事件名。
     * @param isSticky Whether to subscribe to the sticky event. 是否订阅粘性事件。
     * @param onReceived The event reception callback, generic T is the type of data carried by the event. 事件接收回调。
     */
    suspend fun <T : Any> subscribeEvent(
        eventName: String,
        isSticky: Boolean,
        onReceived: (T) -> Unit
    ) {
        // Blocking collection until the outer coroutine is cancelled.
        // 阻塞式收集，直到外部协程取消。
        collectEventFlowSequentially(
            flow = eventFlowStore.getEventFlow(eventName, isSticky),
            onReceived = onReceived
        )
    }

    /**
     * Posts an event.
     * 发布事件。
     *
     * @param eventName The event name. 事件名。
     * @param value The data carried by the event. 事件携带的数据。
     * @param isSticky Whether the event should be emitted to the sticky flow. 是否发往粘性事件流。
     * @param delayMillis The delay time for posting (in milliseconds). 延迟发布的时间（毫秒）。
     */
    fun post(eventName: String, value: Any, isSticky: Boolean = false, delayMillis: Long = 0) {
        Log.w(TAG, "post:$eventName isSticky:$isSticky")
        if (delayMillis > 0) {
            viewModelScope.launch {
                delay(delayMillis)
                eventFlowStore.post(eventName, value, isSticky)
            }
        } else {
            eventFlowStore.post(eventName, value, isSticky)
        }
    }

    /**
     * Removes the specified sticky event flow.
     * 移除指定的粘性事件流。
     *
     * Completely deletes the Flow from the stickyEventFlows Map, meaning future subscribers
     * will no longer be able to get or subscribe to this event.
     * 会从 stickyEventFlows Map 中彻底删除该 Flow，后续的订阅者将无法再获取或订阅该事件。
     *
     * @param eventName The name of the sticky event to remove. 要移除的粘性事件名。
     */
    fun removeStickEvent(eventName: String) {
        eventFlowStore.removeStickyEvent(eventName)
    }

    /**
     * Clears the replay cache of the specified sticky event.
     * 清除指定粘性事件的重放缓存。
     *
     * If the Flow exists, calling resetReplayCache() clears the last cached value of the sticky event,
     * so subsequent new subscribers will no longer receive the old data upon subscription.
     * 如果 Flow 存在，调用 resetReplayCache() 会清除粘性事件缓存的最后一个值，
     * 使得后续新的订阅者在订阅时不会再收到旧数据。
     *
     * @param eventName The name of the sticky event whose cache is to be cleared. 要清除缓存的粘性事件名。
     */
    fun clearStickEvent(eventName: String) {
        eventFlowStore.clearStickyEvent(eventName)
    }
}
