package com.logan.flowbus.composeconsumer

import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner
import com.logan.flowbus.compose.FlowBusEffect
import com.logan.flowbus.core.EventKey
import com.logan.flowbus.core.FlowBus

/** 固定原版编译的 Composable 调用；保留原版默认参数 mask 和 Composer 入口。 */
@Composable
fun <T : Any> Legacy130FlowBusEffect(
    source: FlowBus,
    key: EventKey<T>,
    owner: LifecycleOwner,
    onSubscribed: () -> Unit,
    onReceived: suspend (T) -> Unit
) {
    FlowBusEffect(from = source, key = key, owner = owner,
        onSubscribed = onSubscribed, onReceived = onReceived)
}
