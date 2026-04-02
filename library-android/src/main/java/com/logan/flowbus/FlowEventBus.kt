package com.logan.flowbus

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewModelScope
import com.logan.flowbus.core.EventKey
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.collectFlowBusSequentially
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.reflect.KClass

/**
 * Android 层对 [FlowBus] 的 `ViewModel` 适配。
 *
 * 每个 [FlowEventBus] 实例都绑定在某个 `ViewModelStoreOwner` 上，因此不同 owner
 * 会天然得到不同的总线实例；[GlobalViewModelStore] 则提供全局总线。
 *
 * Android 层创建总线时所用的 [com.logan.flowbus.core.FlowBusConfig] 由 [FlowBusAndroid]
 * 提供。如果你需要自定义 logger、错误处理或缓冲策略，请在首次获取总线之前调用
 * [FlowBusAndroid.configure]。
 */
class FlowEventBus : ViewModel() {
    private val bus = FlowBus(config = FlowBusAndroid.createFlowBusConfig())

    /**
     * 以 `LifecycleOwner` 感知的方式订阅当前总线中的事件。
     *
     * - 当前 [FlowEventBus] 决定从哪个总线实例收事件
     * - [lifecycleOwner] 决定订阅生命周期
     *
     * @param lifecycleOwner 负责托管订阅生命周期的宿主。
     * @param eventName 事件名。
     * @param valueType 事件值类型。
     * @param startState 开始收集的最小生命周期状态。
     * @param dispatcher 回调执行所在的协程调度器。
     * @param isSticky 是否订阅粘性事件。
     * @param onReceived 收到事件后的回调。
     */
    fun <T : Any> subscribeEvent(
        lifecycleOwner: LifecycleOwner,
        eventName: String,
        valueType: KClass<T>,
        startState: Lifecycle.State = Lifecycle.State.STARTED,
        dispatcher: CoroutineDispatcher,
        isSticky: Boolean,
        onReceived: (T) -> Unit
    ): Job {
        return lifecycleOwner.lifecycleScope.launch {
            lifecycleOwner.repeatOnLifecycle(startState) {
                val eventKey = eventKey(name = eventName, valueType = valueType)
                collectFlowBusSequentially(
                    flow = if (isSticky) {
                        bus.stickyFlow(eventKey)
                    } else {
                        bus.flow(eventKey)
                    },
                    eventKey = eventKey,
                    isSticky = isSticky,
                    dispatcher = dispatcher,
                    logger = bus.config.logger,
                    errorHandler = bus.config.errorHandler,
                    onReceived = onReceived
                )
            }
        }
    }

    /**
     * 返回当前总线里指定事件对应的 [Flow]。
     *
     * 该方法只负责暴露 Flow，本身不处理生命周期；你可以在任意 `LifecycleOwner`、
     * `CoroutineScope` 或其他协程环境中自行收集。
     *
     * @param eventName 事件名。
     * @param valueType 事件值类型。
     * @param isSticky 是否读取粘性事件流。
     */
    fun <T : Any> eventFlow(
        eventName: String,
        valueType: KClass<T>,
        isSticky: Boolean = false
    ): Flow<T> {
        val eventKey = eventKey(name = eventName, valueType = valueType)
        return if (isSticky) {
            bus.stickyFlow(eventKey)
        } else {
            bus.flow(eventKey)
        }
    }

    /**
     * 在调用方管理的协程中持续订阅当前总线中的事件。
     *
     * 适用于 `ViewModel`、Repository、Worker 或任意非 UI 协程环境。
     * 该挂起函数会一直收集，直到外部协程被取消。
     *
     * @param eventName 事件名。
     * @param valueType 事件值类型。
     * @param isSticky 是否订阅粘性事件。
     * @param onReceived 收到事件后的回调。
     */
    suspend fun <T : Any> subscribeEvent(
        eventName: String,
        valueType: KClass<T>,
        isSticky: Boolean,
        onReceived: (T) -> Unit
    ) {
        val eventKey = eventKey(name = eventName, valueType = valueType)
        collectFlowBusSequentially(
            flow = if (isSticky) {
                bus.stickyFlow(eventKey)
            } else {
                bus.flow(eventKey)
            },
            eventKey = eventKey,
            isSticky = isSticky,
            logger = bus.config.logger,
            errorHandler = bus.config.errorHandler,
            onReceived = onReceived
        )
    }

    /**
     * 向当前总线发送事件。
     *
     * 这是 best-effort 发送：如果底层缓冲无法立即接收，当前调用不会挂起等待，
     * 而是记录一条 warning。需要严格遵循背压时请改用 [emit]。
     *
     * @param eventName 事件名。
     * @param value 事件数据。
     * @param valueType 事件值类型。
     * @param isSticky 是否发送为粘性事件。
     * @param delayMillis 延迟发送时间，单位毫秒。
     */
    fun <T : Any> post(
        eventName: String,
        value: T,
        valueType: KClass<T>,
        isSticky: Boolean = false,
        delayMillis: Long = 0
    ) {
        val eventKey = eventKey(name = eventName, valueType = valueType)
        if (delayMillis > 0) {
            viewModelScope.launch {
                delay(delayMillis)
                postOrWarn(eventKey = eventKey, value = value, isSticky = isSticky)
            }
            return
        }

        postOrWarn(eventKey = eventKey, value = value, isSticky = isSticky)
    }

    /**
     * 挂起直到事件成功发送到当前总线。
     *
     * 与 [post] 不同，该方法会遵循底层缓冲与背压策略，不会因为 `tryEmit` 失败而静默丢失。
     */
    suspend fun <T : Any> emit(
        eventName: String,
        value: T,
        valueType: KClass<T>,
        isSticky: Boolean = false,
        delayMillis: Long = 0
    ) {
        val eventKey = eventKey(name = eventName, valueType = valueType)
        if (delayMillis > 0) {
            delay(delayMillis)
        }

        if (isSticky) {
            bus.emitSticky(eventKey, value)
        } else {
            bus.emit(eventKey, value)
        }
    }

    /**
     * 从当前总线中彻底移除指定粘性事件，包括其 Flow 实例和重放缓存。
     */
    fun <T : Any> removeStickyEvent(eventName: String, valueType: KClass<T>) {
        bus.removeSticky(eventKey(name = eventName, valueType = valueType))
    }

    /**
     * 清空指定粘性事件的重放缓存，但保留 Flow 实例本身。
     */
    fun <T : Any> clearStickyEvent(eventName: String, valueType: KClass<T>) {
        bus.clearSticky(eventKey(name = eventName, valueType = valueType))
    }

    private fun <T : Any> postOrWarn(eventKey: EventKey<T>, value: T, isSticky: Boolean) {
        val accepted = if (isSticky) {
            bus.postSticky(eventKey, value)
        } else {
            bus.post(eventKey, value)
        }

        if (!accepted) {
            bus.config.logger.warn(
                tag = "FlowBus",
                message = "Dropped event '${eventKey.name}' because the buffer is full. Use emitEvent(...) for guaranteed delivery."
            )
        }
    }
}
