package com.logan.flowbus.binaryconsumer;

import com.logan.flowbus.core.EventKey;
import com.logan.flowbus.core.EventKeyKt;
import com.logan.flowbus.core.FlowBus;
import com.logan.flowbus.core.FlowBusScope;

/** 原始产物编译出的直接 Java 调用，用于核对构造器、Class 工厂及方法描述符。 */
public final class LegacyJavaCaller {
    private LegacyJavaCaller() {}

    public static void verify() {
        FlowBus bus = new FlowBus();
        EventKey<String> key = EventKeyKt.eventKey("binary.java", String.class);
        require(bus.postSticky(key, "java"), "Java publish rejected");
        require("java".equals(bus.consumeStickyLatest(key)), "Java sticky mismatch");
        FlowBusScope scope = bus.openScope("binary.java.scope");
        try {
            require(scope.postSticky(key, "scope"), "Java scoped publish rejected");
            require("scope".equals(scope.consumeStickyLatest(key)), "Java scope mismatch");
        } finally { scope.close(); }
        require(scope.isClosed(), "Java close did not invalidate handle");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
