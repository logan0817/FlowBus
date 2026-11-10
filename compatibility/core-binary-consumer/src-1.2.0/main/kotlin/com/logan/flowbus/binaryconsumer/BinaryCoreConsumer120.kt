package com.logan.flowbus.binaryconsumer

import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusChannelConfig
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.channels.BufferOverflow

/** 使用正式 1.2.0 编译后，在候选上原样运行通道配置入口。 */
object BinaryCoreConsumer120 {
    fun verify() {
        val bus = FlowBus()
        val key = eventKey<String>("binary.1.2.0.config")
        val config = FlowBusChannelConfig(
            normalBufferCapacity = 1,
            normalOverflowPolicy = BufferOverflow.SUSPEND,
            stickyReplay = 2
        )
        bus.configureChannel(key, config)
        bus.configureChannel(key, config.copy())
        check(bus.tryPostResult(key, "normal").overflowPolicy == BufferOverflow.SUSPEND)
        check(bus.postSticky(key, "sticky"))
        check(bus.consumeStickyLatest(key) == "sticky")
        val shared = bus.scoped("shared.1.2.0")
        shared.configureChannel(key, config)
        check(shared.postSticky(key, "shared"))
        check(shared.consumeStickyLatest(key) == "shared")
        val owned = bus.openScope("owned.1.2.0")
        try {
            owned.configureChannel(key, config)
            check(owned.postSticky(key, "owned"))
            check(owned.consumeStickyLatest(key) == "owned")
        } finally {
            owned.close()
        }
    }
}
