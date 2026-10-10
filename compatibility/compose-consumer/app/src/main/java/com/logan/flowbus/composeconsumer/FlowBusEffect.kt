package com.logan.flowbus.composeconsumer

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.logan.flowbus.core.EventKey
import com.logan.flowbus.core.FlowBus

/**
 * 仅用于消费者兼容验证的接入示例，不是已发布的 Compose 模块。
 *
 * owner 由调用方提供，导航页面可以传入自己的 NavBackStackEntry。
 * 来源、通道、owner 或生命周期策略变化会重建订阅；只更新回调不会重建订阅。
 * 离开组合或非活跃阶段会请求取消当前处理，且不会关闭来源 bus。
 * 业务需遵循协作取消；阻塞或 NonCancellable 工作不能被强制停止。
 */
@Composable
fun <T : Any> FlowBusEffect(
    from: FlowBus,
    key: EventKey<T>,
    owner: LifecycleOwner,
    minState: Lifecycle.State = Lifecycle.State.STARTED,
    isSticky: Boolean = false,
    onReceived: suspend (T) -> Unit
) {
    require(minState != Lifecycle.State.INITIALIZED) {
        "Lifecycle.State.INITIALIZED is not supported by repeatOnLifecycle."
    }
    val currentCallback = rememberUpdatedState(onReceived)
    LaunchedEffect(from, key.name, key.valueType, owner, minState, isSticky) {
        owner.repeatOnLifecycle(minState) {
            if (isSticky) {
                from.collectStickySuspending(key) { value -> currentCallback.value(value) }
            } else {
                from.collectSuspending(key) { value -> currentCallback.value(value) }
            }
        }
    }
}
