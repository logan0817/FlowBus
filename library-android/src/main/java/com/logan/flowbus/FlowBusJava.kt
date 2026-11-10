package com.logan.flowbus

import androidx.annotation.MainThread
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.core.FlowBusChannelSnapshot
import com.logan.flowbus.core.FlowBusPostResult
import kotlinx.coroutines.Job

/**
 * Java 友好的 Android 入口，复用现有 channel、全局来源和 owner 来源。
 * 普通与 sticky 仍独立分流；发送结果只描述总线接收情况，不代表业务处理完成。
 */
object FlowBusJava {
    /** Java 事件回调；同一订阅按顺序执行，错误策略由来源总线决定。 */
    fun interface EventListener<T : Any> {
        fun onEvent(event: T)
    }

    /**
     * 当前订阅的取消句柄，不持有来源的关闭权。
     * [cancel] 幂等地请求取消；[isCompleted] 为 true 才表示在途回调已经结束。
     * 生命周期非活跃期间，句柄可能尚未取消或完成，但此时不收集事件。
     */
    class Subscription internal constructor(private val job: Job) {
        fun cancel() {
            job.cancel()
        }

        val isCancelled: Boolean get() = job.isCancelled
        val isCompleted: Boolean get() = job.isCompleted
    }

    /** 使用稳定名称和显式 Java 类型创建 channel，不获取或创建总线。 */
    @JvmStatic
    fun <T : Any> channel(name: String, valueType: Class<T>): EventChannel<T> {
        return eventChannel(name, valueType)
    }

    @JvmStatic
    fun <T : Any> post(channel: EventChannel<T>, value: T): Boolean = postResult(channel, value).accepted

    @JvmStatic
    fun <T : Any> postResult(channel: EventChannel<T>, value: T): FlowBusPostResult {
        return GlobalViewModelStore.get(FlowEventBus::class.java).postResult(channel.name, value, channel.valueType)
    }

    @JvmStatic
    fun <T : Any> postSticky(channel: EventChannel<T>, value: T): Boolean = postStickyResult(channel, value).accepted

    @JvmStatic
    fun <T : Any> postStickyResult(channel: EventChannel<T>, value: T): FlowBusPostResult {
        return GlobalViewModelStore.get(FlowEventBus::class.java)
            .postResult(channel.name, value, channel.valueType, isSticky = true)
    }

    @MainThread
    @JvmStatic
    fun <T : Any> postTo(owner: ViewModelStoreOwner, channel: EventChannel<T>, value: T): Boolean =
        postResultTo(owner, channel, value).accepted

    @MainThread
    @JvmStatic
    fun <T : Any> postResultTo(owner: ViewModelStoreOwner, channel: EventChannel<T>, value: T): FlowBusPostResult {
        return flowEventBusFor(owner).postResult(channel.name, value, channel.valueType)
    }

    @MainThread
    @JvmStatic
    fun <T : Any> postStickyTo(owner: ViewModelStoreOwner, channel: EventChannel<T>, value: T): Boolean =
        postStickyResultTo(owner, channel, value).accepted

    @MainThread
    @JvmStatic
    fun <T : Any> postStickyResultTo(owner: ViewModelStoreOwner, channel: EventChannel<T>, value: T): FlowBusPostResult {
        return flowEventBusFor(owner).postResult(channel.name, value, channel.valueType, isSticky = true)
    }

    /**
     * 查询全局来源的已绑定通道；首次查询会按 Android 全局入口规则获取或创建来源。
     * 未知通道返回 null，不为查询绑定或创建通道。
     */
    @JvmStatic
    fun <T : Any> inspectChannel(channel: EventChannel<T>): FlowBusChannelSnapshot? {
        return GlobalViewModelStore.get(FlowEventBus::class.java).inspectChannel(channel.asEventKey())
    }

    /** 查询明确 owner 来源的已绑定通道；首次获取可创建来源，须在主线程调用。 */
    @MainThread
    @JvmStatic
    fun <T : Any> inspectChannelFrom(owner: ViewModelStoreOwner, channel: EventChannel<T>): FlowBusChannelSnapshot? {
        return flowEventBusFor(owner).inspectChannel(channel.asEventKey())
    }

    @MainThread
    @JvmStatic
    fun <T : Any> subscribe(receiver: LifecycleOwner, channel: EventChannel<T>, listener: EventListener<T>): Subscription =
        subscribe(receiver, channel, Runnable {}, listener)

    /**
     * 默认 STARTED / Main.immediate；每次活跃阶段建立真实订阅后执行通知，再处理事件。
     * 通知不代表后续始终活跃；回调不应阻塞，通知异常直接终止订阅。
     */
    @MainThread
    @JvmStatic
    fun <T : Any> subscribe(
        receiver: LifecycleOwner,
        channel: EventChannel<T>,
        onSubscribed: Runnable,
        listener: EventListener<T>
    ): Subscription = subscribeFrom(receiver, GlobalViewModelStore, channel, onSubscribed, listener)

    @MainThread
    @JvmStatic
    fun <T : Any> subscribeFrom(
        receiver: LifecycleOwner,
        owner: ViewModelStoreOwner,
        channel: EventChannel<T>,
        listener: EventListener<T>
    ): Subscription = subscribeFrom(receiver, owner, channel, Runnable {}, listener)

    /** owner 决定来源，receiver 决定收集生命周期；订阅结束不会关闭 owner 来源。 */
    @MainThread
    @JvmStatic
    fun <T : Any> subscribeFrom(
        receiver: LifecycleOwner,
        owner: ViewModelStoreOwner,
        channel: EventChannel<T>,
        onSubscribed: Runnable,
        listener: EventListener<T>
    ): Subscription = subscribeToSource(receiver, owner, channel, false, onSubscribed, listener)

    @MainThread
    @JvmStatic
    fun <T : Any> subscribeSticky(
        receiver: LifecycleOwner,
        channel: EventChannel<T>,
        listener: EventListener<T>
    ): Subscription = subscribeSticky(receiver, channel, Runnable {}, listener)

    /** sticky 在每次活跃重新订阅时沿用来源 replay 语义，通知本身不清除 replay。 */
    @MainThread
    @JvmStatic
    fun <T : Any> subscribeSticky(
        receiver: LifecycleOwner,
        channel: EventChannel<T>,
        onSubscribed: Runnable,
        listener: EventListener<T>
    ): Subscription = subscribeStickyFrom(receiver, GlobalViewModelStore, channel, onSubscribed, listener)

    @MainThread
    @JvmStatic
    fun <T : Any> subscribeStickyFrom(
        receiver: LifecycleOwner,
        owner: ViewModelStoreOwner,
        channel: EventChannel<T>,
        listener: EventListener<T>
    ): Subscription = subscribeStickyFrom(receiver, owner, channel, Runnable {}, listener)

    @MainThread
    @JvmStatic
    fun <T : Any> subscribeStickyFrom(
        receiver: LifecycleOwner,
        owner: ViewModelStoreOwner,
        channel: EventChannel<T>,
        onSubscribed: Runnable,
        listener: EventListener<T>
    ): Subscription = subscribeToSource(receiver, owner, channel, true, onSubscribed, listener)

    @MainThread
    private fun <T : Any> subscribeToSource(
        receiver: LifecycleOwner,
        owner: ViewModelStoreOwner,
        channel: EventChannel<T>,
        isSticky: Boolean,
        onSubscribed: Runnable,
        listener: EventListener<T>
    ): Subscription {
        val job = flowEventBusFor(owner).subscribeEventSuspending(
            lifecycleOwner = receiver,
            key = channel.asEventKey(),
            onSubscribed = { onSubscribed.run() },
            isSticky = isSticky,
            onReceived = { listener.onEvent(it) }
        )
        return Subscription(job)
    }
}
