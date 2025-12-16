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
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

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
    private val normalEventFlows: MutableMap<String, MutableSharedFlow<Any>> = ConcurrentHashMap()
    private val stickyEventFlows: MutableMap<String, MutableSharedFlow<Any>> = ConcurrentHashMap()

    /**
     * Gets or creates a MutableSharedFlow for the specified event name.
     * 获取或创建指定事件名的 MutableSharedFlow。
     *
     * @param eventName The unique identifier for the event. 事件的唯一标识符。
     * @param isSticky Whether the event is sticky. 是否是粘性事件。
     * @return The corresponding MutableSharedFlow instance. 对应的 MutableSharedFlow 实例。
     */
    private fun getEventFlow(eventName: String, isSticky: Boolean): MutableSharedFlow<Any> {
        val targetMap = if (isSticky) stickyEventFlows else normalEventFlows
        return targetMap.getOrPut(eventName) {
            MutableSharedFlow(
                replay = if (isSticky) 1 else 0,
                extraBufferCapacity = Int.MAX_VALUE,
                onBufferOverflow = BufferOverflow.DROP_OLDEST
            )
        }
    }

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
                getEventFlow(eventName, isSticky).collect { value ->
                    // Launch a new coroutine on the specified Dispatcher to handle the received event,
                    // avoiding blocking the Flow's collecting coroutine.
                    // 在指定的 Dispatcher 上启动新的协程来处理接收到的事件，避免阻塞 Flow 的收集协程。
                    this.launch(dispatcher) {
                        handleReceivedEvent(value, onReceived)
                    }
                }
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
        getEventFlow(eventName, isSticky).collect { value ->
            handleReceivedEvent(value, onReceived)
        }
    }

    /**
     * Internal handler for received events to execute the callback.
     * 内部处理接收到的事件并执行回调。
     *
     * Uses a try-catch block to handle ClassCastException, preventing application crashes
     * due to type mismatch.
     * 使用 try-catch 块捕获 ClassCastException，防止因事件类型不匹配导致应用崩溃。
     */
    @Suppress("UNCHECKED_CAST")
    private fun <T : Any> handleReceivedEvent(value: Any, onReceived: (T) -> Unit) {
        try {
            // Attempt to cast the Any type to the expected T type.
            // 尝试将 Any 类型转换为预期的 T 类型。
            onReceived(value as T)
        } catch (e: ClassCastException) {
            Log.w(TAG, "handleReceivedEvent ClassCastException:$e")
        } catch (e: Exception) {
            Log.w(TAG, "handleReceivedEvent Exception:$e")
        }
    }

    /**
     * Posts an event.
     * 发布事件。
     *
     * Note: Since getEventFlow no longer enforces uniqueness, this method attempts to post to
     * both the normal event flow and the sticky event flow. If a same-named event exists, both will receive it.
     * 注意：由于 getEventFlow 不再限制唯一性，这里同时尝试获取并发布给普通事件流和粘性事件流。
     * 如果存在同名事件，两者都会收到。
     *
     * @param eventName The event name. 事件名。
     * @param value The data carried by the event. 事件携带的数据。
     * @param isSticky This parameter is unused in the current post method; it posts to both Flows. 当前版本中 post 方法未使用此参数，它会尝试 post 到两个 Flows。
     * @param timeMillis The delay time for posting (in milliseconds). 延迟发布的时间（毫秒）。
     */
    fun post(eventName: String, value: Any, timeMillis: Long = 0) {
        Log.w(TAG, "post:$eventName")
        // Attempts to get both normal and sticky event flows (if they exist) and forms a list.
        // getEventFlow ensures the corresponding Flow exists, creating it if it doesn't.
        // 尝试获取普通事件流和粘性事件流（如果存在），并组成列表。
        // getEventFlow 会确保对应的 Flow 存在，若不存在则创建。
        listOfNotNull(
            getEventFlow(eventName, false), getEventFlow(eventName, true)
        ).forEach { flow ->
            if (timeMillis > 0) {
                viewModelScope.launch {
                    delay(timeMillis)
                    flow.tryEmit(value)
                }
            } else {
                flow.tryEmit(value)
            }
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
        stickyEventFlows.remove(eventName)
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
        stickyEventFlows[eventName]?.resetReplayCache()
    }
}