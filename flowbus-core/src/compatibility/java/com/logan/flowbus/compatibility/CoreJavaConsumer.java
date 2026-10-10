package com.logan.flowbus.compatibility;

import com.logan.flowbus.core.EventKey;
import com.logan.flowbus.core.EventKeyKt;
import com.logan.flowbus.core.FlowBus;
import com.logan.flowbus.core.FlowBusScope;

/** Java 调用方直接使用公开工厂和实例 API，不访问 Kotlin internal 构造器。 */
public final class CoreJavaConsumer {
    private CoreJavaConsumer() {}

    public static void verify() {
        FlowBus bus = new FlowBus();
        EventKey<String> key = EventKeyKt.eventKey("consumer.java", String.class);
        require(bus.postSticky(key, "root"), "Java sticky publish rejected");
        require("root".equals(bus.consumeStickyLatest(key)), "Java sticky value mismatch");
        require(bus.consumeStickyLatest(key) == null, "Java sticky replay not consumed");
        FlowBusScope owned = bus.openScope("consumer.java.scope");
        try {
            require(owned.postSticky(key, "scoped"), "Java scope publish rejected");
            require("scoped".equals(owned.consumeStickyLatest(key)), "Java scope value mismatch");
            require(owned.postSticky(key, "leftover"), "Java scope replay publish rejected");
        } finally {
            owned.close();
        }
        require(owned.isClosed(), "Java scope close failed");
        FlowBusScope reopened = bus.openScope("consumer.java.scope");
        try {
            require(reopened.consumeStickyLatest(key) == null, "Java reopened scope retained old replay");
        } finally {
            reopened.close();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
