package com.logan.flowbus;

import androidx.lifecycle.ViewModelStoreOwner;
import com.logan.flowbus.core.EventChannel;
import com.logan.flowbus.core.FlowBusDiagnosticEvent;
import com.logan.flowbus.core.FlowBusDiagnosticRegistration;
import java.util.ArrayList;
import java.util.List;
import org.junit.Assert;

/** 真实 Java 调用方：静态重载、SAM 和 AutoCloseable 均由 Java 编译器验证。 */
public final class FlowBusJavaDiagnosticsCaller {
    private FlowBusJavaDiagnosticsCaller() {}

    public static void verify(ViewModelStoreOwner owner) {
        EventChannel<String> channel = FlowBusJava.channel("java.diagnostics", String.class);
        List<FlowBusDiagnosticEvent> global = new ArrayList<>();
        List<FlowBusDiagnosticEvent> owned = new ArrayList<>();
        FlowBusDiagnosticRegistration globalRegistration = FlowBusJava.observeDiagnostics(global::add);
        FlowBusDiagnosticRegistration ownerRegistration = FlowBusJava.observeDiagnostics(owner, owned::add);
        try {
            FlowBusJava.post(channel, "global");
            FlowBusJava.postTo(owner, channel, "owner");
            Assert.assertEquals(1, global.size());
            Assert.assertEquals(1, owned.size());
            FlowBusDiagnosticEvent.Published first = (FlowBusDiagnosticEvent.Published) global.get(0);
            FlowBusDiagnosticEvent.Published second = (FlowBusDiagnosticEvent.Published) owned.get(0);
            Assert.assertNotEquals(first.getChannel().getBusId(), second.getChannel().getBusId());
            Assert.assertEquals(channel.getName(), second.getChannel().getEventName());
            Assert.assertTrue(second.getResult().getAccepted());
            ownerRegistration.close();
            ownerRegistration.close();
            FlowBusJava.postTo(owner, channel, "after-close");
            Assert.assertEquals(1, owned.size());
        } finally {
            ownerRegistration.close();
            globalRegistration.close();
        }
    }
}
