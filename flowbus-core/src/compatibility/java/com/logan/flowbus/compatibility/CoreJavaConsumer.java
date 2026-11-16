package com.logan.flowbus.compatibility;

import com.logan.flowbus.core.EventKey;
import com.logan.flowbus.core.EventKeyKt;
import com.logan.flowbus.core.FlowBus;
import com.logan.flowbus.core.FlowBusDiagnosticEvent;
import com.logan.flowbus.core.FlowBusDiagnosticPublishMode;
import com.logan.flowbus.core.FlowBusDiagnosticRegistration;
import com.logan.flowbus.core.FlowBusPostOutcome;
import com.logan.flowbus.core.FlowBusScope;
import java.util.ArrayList;
import java.util.List;

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
        verifyDiagnostics();
    }

    private static void verifyDiagnostics() {
        FlowBus bus = new FlowBus();
        EventKey<String> key = EventKeyKt.eventKey("consumer.java.diagnostics", String.class);
        List<FlowBusDiagnosticEvent.Published> publications = new ArrayList<>();
        try (FlowBusDiagnosticRegistration registration = bus.observeDiagnostics(event -> {
            if (event instanceof FlowBusDiagnosticEvent.Published) {
                publications.add((FlowBusDiagnosticEvent.Published) event);
            }
        })) {
            require(bus.post(key, "ordinary"), "Java observed ordinary publish rejected");
            require(bus.postSticky(key, "sticky"), "Java observed sticky publish rejected");
            require(publications.size() == 2, "Java observer SAM did not receive both publications");
            FlowBusDiagnosticEvent.Published ordinary = publications.get(0);
            FlowBusDiagnosticEvent.Published sticky = publications.get(1);
            require(ordinary.getMode() == FlowBusDiagnosticPublishMode.TryPost, "Java publish mode mismatch");
            require(ordinary.getResult().getAccepted(), "Java observed result was not accepted");
            require(ordinary.getResult().getOutcome() == FlowBusPostOutcome.AcceptedWithoutActiveSubscriber,
                    "Java observed outcome mismatch");
            require(ordinary.getResult().getSubscriptionCount() == 0, "Java subscriber count mismatch");
            require(key.getName().equals(ordinary.getChannel().getEventName()), "Java diagnostic event name mismatch");
            require(ordinary.getChannel().getScopeName() == null, "Java root diagnostic unexpectedly scoped");
            require(ordinary.getChannel().getBusId() > 0 && ordinary.getChannel().getStoreId() > 0,
                    "Java diagnostic source identity missing");
            require(!ordinary.getChannel().isSticky() && sticky.getChannel().isSticky(), "Java sticky identity mismatch");
            require(ordinary.getChannel().getFlowId() != sticky.getChannel().getFlowId(), "Java stream identities collided");
            registration.close();
            registration.close();
            require(bus.post(key, "after-close"), "Java business send stopped after observer close");
            require(publications.size() == 2, "Java closed observer received another publication");
            require("sticky".equals(bus.consumeStickyLatest(key)), "Java observer close cleared sticky replay");
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
