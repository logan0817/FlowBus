package com.logan.flowbus.binaryconsumer;

import com.logan.flowbus.core.EventKey;
import com.logan.flowbus.core.EventKeyKt;
import com.logan.flowbus.core.FlowBus;
import com.logan.flowbus.core.FlowBusChannelSnapshot;
import kotlin.Unit;

/** 固定 1.3.0 Java 工厂、通知重载与诊断 getter 的 JVM 调用。 */
public final class Legacy130JavaCaller {
    private Legacy130JavaCaller() {}

    public static void verify() {
        FlowBus bus = new FlowBus();
        EventKey<String> key = EventKeyKt.eventKey("binary.1.3.0.java", String.class);
        if (bus.inspectChannel(key) != null) throw new IllegalStateException("Inspection registered a channel");
        bus.flow(key, () -> Unit.INSTANCE);
        FlowBusChannelSnapshot snapshot = bus.inspectChannel(key);
        if (snapshot == null || !snapshot.getHasNormalFlow() || snapshot.getNormalSubscriptionCount() != 0) {
            throw new IllegalStateException("Legacy Java channel inspection changed");
        }
    }
}
