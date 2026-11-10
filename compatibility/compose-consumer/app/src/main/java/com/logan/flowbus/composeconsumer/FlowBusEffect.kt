package com.logan.flowbus.composeconsumer

import androidx.compose.runtime.Composable
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.logan.flowbus.core.EventKey
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.compose.FlowBusEffect as PublishedFlowBusEffect

/**
 * 消费者直接验证候选 flowbus-compose 的公开入口，不复制 effect 实现。
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
    PublishedFlowBusEffect(from, key, owner, minState, isSticky, onSubscribed, onReceived)
}
