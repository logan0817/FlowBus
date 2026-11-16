package com.logan.flowbus.consumer;

import androidx.lifecycle.ViewModelStoreOwner;
import com.logan.flowbus.FlowBusJava;
import com.logan.flowbus.core.FlowBusDiagnosticObserver;
import com.logan.flowbus.core.FlowBusDiagnosticRegistration;

/** 当前 Java API 由真实 Java 编译并进入 minified APK，不修改冻结的旧版 fixture。 */
public final class DiagnosticApiCalls {
    private DiagnosticApiCalls() {}

    public static FlowBusDiagnosticRegistration observe(
            ViewModelStoreOwner owner, FlowBusDiagnosticObserver observer) {
        return owner == null
                ? FlowBusJava.observeDiagnostics(observer)
                : FlowBusJava.observeDiagnostics(owner, observer);
    }
}
