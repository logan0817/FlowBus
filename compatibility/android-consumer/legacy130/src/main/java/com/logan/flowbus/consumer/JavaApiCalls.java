package com.logan.flowbus.consumer;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.ViewModelStoreOwner;
import com.logan.flowbus.FlowBusJava;
import com.logan.flowbus.core.EventChannel;
import com.logan.flowbus.core.FlowBusChannelSnapshot;
import com.logan.flowbus.core.FlowBusPostResult;
import java.util.concurrent.atomic.AtomicReference;

/** 对正式 1.3.0 编译的 Java 调用，候选 APK 复用该 JAR 后执行完整互通自检。 */
public final class JavaApiCalls {
    private JavaApiCalls() {}

    public static EventChannel<String> channel(String name) {
        return FlowBusJava.channel(name, String.class);
    }

    public static FlowBusJava.Subscription subscribe(
            LifecycleOwner receiver, ViewModelStoreOwner source, EventChannel<String> channel,
            boolean sticky, Runnable onSubscribed, FlowBusJava.EventListener<String> listener) {
        if (source == null) {
            return sticky
                    ? FlowBusJava.subscribeSticky(receiver, channel, onSubscribed, listener)
                    : FlowBusJava.subscribe(receiver, channel, onSubscribed, listener);
        }
        return sticky
                ? FlowBusJava.subscribeStickyFrom(receiver, source, channel, onSubscribed, listener)
                : FlowBusJava.subscribeFrom(receiver, source, channel, onSubscribed, listener);
    }

    public static void post(
            ViewModelStoreOwner source, EventChannel<String> channel, boolean sticky, String value) {
        boolean accepted;
        if (source == null) {
            accepted = sticky ? FlowBusJava.postSticky(channel, value) : FlowBusJava.post(channel, value);
        } else {
            accepted = sticky
                    ? FlowBusJava.postStickyTo(source, channel, value)
                    : FlowBusJava.postTo(source, channel, value);
        }
        require(accepted, "Java event was rejected: " + value);
    }

    public static FlowBusPostResult postResult(
            ViewModelStoreOwner source, EventChannel<String> channel, boolean sticky, String value) {
        FlowBusPostResult result;
        if (source == null) {
            result = sticky
                    ? FlowBusJava.postStickyResult(channel, value)
                    : FlowBusJava.postResult(channel, value);
        } else {
            result = sticky
                    ? FlowBusJava.postStickyResultTo(source, channel, value)
                    : FlowBusJava.postResultTo(source, channel, value);
        }
        require(result.getAccepted(), "Java post result was rejected");
        require(result.isSticky() == sticky, "Java post result routed to the wrong stream");
        require(result.getEventName().equals(channel.getName()), "Java post result channel mismatch");
        return result;
    }

    public static FlowBusChannelSnapshot inspect(ViewModelStoreOwner source, EventChannel<String> channel) {
        return source == null
                ? FlowBusJava.inspectChannel(channel)
                : FlowBusJava.inspectChannelFrom(source, channel);
    }

    public static void cancel(FlowBusJava.Subscription subscription) {
        subscription.cancel();
        subscription.cancel();
        require(subscription.isCancelled(), "Java subscription did not record cancellation");
    }

    public static FlowBusJava.Subscription subscribeCancellingOnNext(
            LifecycleOwner receiver, ViewModelStoreOwner source, EventChannel<String> channel,
            Runnable onSubscribed, Runnable onCancelled) {
        AtomicReference<FlowBusJava.Subscription> current = new AtomicReference<>();
        FlowBusJava.Subscription subscription = FlowBusJava.subscribeFrom(
                receiver, source, channel, onSubscribed, value -> {
                    FlowBusJava.Subscription active = current.get();
                    require(active != null, "Java subscription handle was not returned before the event");
                    cancel(active);
                    require(!active.isCompleted(), "Cancellation completed before the in-flight callback returned");
                    onCancelled.run();
                });
        current.set(subscription);
        return subscription;
    }

    public static void verifyNullRejection(EventChannel<String> channel) {
        try {
            FlowBusJava.channel("consumer.java.invalid", (Class<String>) null);
            throw new IllegalStateException("Java null event type was accepted");
        } catch (NullPointerException expected) {
            // Java 调用方也必须经过公开入口的参数校验。
        }
        try {
            FlowBusJava.post(channel, null);
            throw new IllegalStateException("Java null event value was accepted");
        } catch (NullPointerException expected) {
            // 不放宽 Kotlin 非空事件契约。
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
