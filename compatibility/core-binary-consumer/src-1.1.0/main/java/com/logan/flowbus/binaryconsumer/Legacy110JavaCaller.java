package com.logan.flowbus.binaryconsumer;

import com.logan.flowbus.core.FlowBus;
import com.logan.flowbus.core.FlowBusConfig;

/** 固定旧数据类构造器、component 和 copy 的直接 JVM 调用。 */
public final class Legacy110JavaCaller {
    private Legacy110JavaCaller() {}

    public static void verify() {
        FlowBusConfig original = new FlowBusConfig();
        FlowBusConfig copy = original.copy(original.component1(), original.component2(),
            original.component3(), original.component4(), original.component5(), original.component6());
        if (!original.equals(copy) || new FlowBus(copy).getConfig().getNormalBufferCapacity() != 64) {
            throw new IllegalStateException("Legacy config descriptors changed");
        }
    }
}
