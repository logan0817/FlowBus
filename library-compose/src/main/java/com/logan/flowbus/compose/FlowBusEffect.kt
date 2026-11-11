package com.logan.flowbus.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.logan.flowbus.core.EventKey
import com.logan.flowbus.core.FlowBus

/**
 * 在 [owner] 的活跃生命周期内，按顺序收集显式 [from] 来源的事件。
 *
 * 来源、通道、owner、最低生命周期状态或 sticky 选择变化时重建订阅；仅更新
 * [onReceived] / [onSubscribed] 不重建订阅，后续通知使用最新回调。离开组合或低于
 * [minState] 时请求取消，不关闭传入的总线。阻塞或 NonCancellable 回调仍须自行结束。
 * [minState] 只允许 CREATED、STARTED、RESUMED，无效值在组合时同步抛出异常。
 *
 * [onSubscribed] 在每次底层订阅建立后执行，不表示 replay 或业务处理已完成，也不
 * 保证未来仍然活跃。它应只发出短通知，由独立生产协程发送；不要在其中阻塞等待。
 * 事件处理沿用来源的错误策略；建立通知自身抛出的异常直接使本次收集失败。
 */
@Composable
fun <T : Any> FlowBusEffect(
    from: FlowBus,
    key: EventKey<T>,
    owner: LifecycleOwner,
    minState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onSubscribed: (() -> Unit)? = null,
    onReceived: suspend (T) -> Unit
) {
    require(minState.isAtLeast(Lifecycle.State.CREATED)) {
        "Invalid lifecycle threshold $minState. Use CREATED, STARTED, or RESUMED."
    }
    val currentCallback = rememberUpdatedState(onReceived)
    val currentOnSubscribed = rememberUpdatedState(onSubscribed)
    LaunchedEffect(from, key.name, key.valueType, owner, minState, isSticky) {
        owner.repeatOnLifecycle(minState) {
            if (isSticky) {
                if (currentOnSubscribed.value == null) {
                    from.collectStickySuspending(key) { value -> currentCallback.value(value) }
                } else {
                    from.collectStickySuspending(
                        key = key,
                        onSubscribed = { currentOnSubscribed.value?.invoke() }
                    ) { value -> currentCallback.value(value) }
                }
            } else {
                if (currentOnSubscribed.value == null) {
                    from.collectSuspending(key) { value -> currentCallback.value(value) }
                } else {
                    from.collectSuspending(
                        key = key,
                        onSubscribed = { currentOnSubscribed.value?.invoke() }
                    ) { value -> currentCallback.value(value) }
                }
            }
        }
    }
}
