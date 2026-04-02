package com.logan.flowbus

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.core.defaultEventName

/**
 * 返回全局总线中事件类型 [T] 对应的 [kotlinx.coroutines.flow.Flow]。
 *
 * 该 Flow 本身不绑定生命周期，适合交给 `LifecycleOwner.collectEvent(...)`、
 * `lifecycleScope.launch { ... }` 或任意 `CoroutineScope` 来收集。
 * 如果你要读取粘性事件，推荐直接使用 [stickyEventFlow]，可读性更好。
 */
inline fun <reified T : Any> eventFlow(
    isSticky: Boolean = false,
    eventName: String = defaultEventName<T>()
) = GlobalViewModelStore.get(FlowEventBus::class.java)
    .eventFlow(eventName = eventName, valueType = T::class, isSticky = isSticky)

/**
 * 返回全局总线中事件类型 [T] 对应的粘性 [kotlinx.coroutines.flow.Flow]。
 *
 * 这是 [eventFlow] 的显式粘性版本，等价于 `eventFlow<T>(isSticky = true)`，
 * 但更适合公开 API 的可读性和发现性。
 */
inline fun <reified T : Any> stickyEventFlow(
    eventName: String = defaultEventName<T>()
) = eventFlow<T>(isSticky = true, eventName = eventName)

/**
 * 返回指定 [owner] 总线中事件类型 [T] 对应的 [kotlinx.coroutines.flow.Flow]。
 *
 * 这里的 [owner] 用来选择“从哪个总线读数据”，它可以是 Activity、Fragment、
 * NavBackStackEntry，或者任意自定义 `ViewModelStoreOwner`。
 * 如果你要读取粘性事件，推荐直接使用 [stickyEventFlowFrom]。
 */
inline fun <reified T : Any> eventFlowFrom(
    owner: ViewModelStoreOwner,
    isSticky: Boolean = false,
    eventName: String = defaultEventName<T>()
) = ViewModelProvider(owner).get(FlowEventBus::class.java)
    .eventFlow(eventName = eventName, valueType = T::class, isSticky = isSticky)

/**
 * 返回指定 [owner] 总线中事件类型 [T] 对应的粘性 [kotlinx.coroutines.flow.Flow]。
 *
 * 这是 [eventFlowFrom] 的显式粘性版本，等价于
 * `eventFlowFrom<T>(owner = owner, isSticky = true)`。
 */
inline fun <reified T : Any> stickyEventFlowFrom(
    owner: ViewModelStoreOwner,
    eventName: String = defaultEventName<T>()
) = eventFlowFrom<T>(owner = owner, isSticky = true, eventName = eventName)
