package com.logan.flowbus

import androidx.annotation.MainThread
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 以 `LifecycleOwner` 感知的方式收集任意 [Flow]。
 *
 * 这是一个通用收集工具，不只用于 FlowBus；只要你有 `Flow<T>`，都可以通过它把
 * 收集过程绑定到 [LifecycleOwner]。
 *
 * @param flow 要收集的 Flow。
 * @param dispatcher 回调执行所在的协程调度器。
 * @param minLifecycleState 开始收集的最小生命周期状态。
 * @param onReceived 收到数据后的回调。
 */
@MainThread
inline fun <T> LifecycleOwner.collectEvent(
    flow: Flow<T>,
    dispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    minLifecycleState: Lifecycle.State = Lifecycle.State.STARTED,
    crossinline onReceived: (T) -> Unit
): Job {
    return lifecycleScope.launch {
        repeatOnLifecycle(minLifecycleState) {
            flow.collect { value ->
                withContext(dispatcher) {
                    onReceived(value)
                }
            }
        }
    }
}
