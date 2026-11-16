package com.logan.flowbus.core

/**
 * 一条实际事件流的进程内身份。相同名称在不同实例、scope 或清理重建后具有不同身份。
 *
 * ID 仅用于本进程排障，不是业务消息 ID，不跨进程或重启保持稳定。
 */
data class FlowBusDiagnosticChannel(
    val busId: Long,
    val storeId: Long,
    val flowId: Long,
    val eventName: String,
    val scopeName: String?,
    val isSticky: Boolean
)

/** 发送操作的调用方式，不表示业务回调已经执行完成。 */
enum class FlowBusDiagnosticPublishMode { TryPost, Emit }

/** 收集真正退出的原因；取消请求发出时尚未退出的回调不会提前生成结束通知。 */
enum class FlowBusDiagnosticEndReason { Completed, Cancelled, Failed }

/** FlowBus 能准确识别的辅助通知或业务错误阶段。 */
enum class FlowBusDiagnosticFailurePhase { ValueCast, SubscriberCallback, SubscriptionCallback }

/**
 * 可选排障记录，只包含元数据，不持有事件值、Throwable、owner、Job 或业务回调。
 *
 * [Published] 的 accepted/订阅数/overflow 保持 [FlowBusPostResult] 的原有含义，
 * 不能据此推断精确丢弃、送达或业务处理成功。并发通知没有全局顺序保证。
 */
sealed class FlowBusDiagnosticEvent {
    abstract val channel: FlowBusDiagnosticChannel

    class Published(
        override val channel: FlowBusDiagnosticChannel,
        val result: FlowBusPostResult,
        val mode: FlowBusDiagnosticPublishMode
    ) : FlowBusDiagnosticEvent()

    /** 挂起发送被取消；不计入既有 rejectedPostCount。 */
    class EmitCancelled(override val channel: FlowBusDiagnosticChannel) : FlowBusDiagnosticEvent()

    class EmitFailed(
        override val channel: FlowBusDiagnosticChannel,
        val exceptionType: String
    ) : FlowBusDiagnosticEvent()

    /** 实际注册完成、replay 和用户 onSubscribed 之前；不是新的业务就绪屏障。 */
    class SubscriptionStarted(
        override val channel: FlowBusDiagnosticChannel,
        val subscriptionId: Long
    ) : FlowBusDiagnosticEvent()

    /**
     * collect 真正退出后发出。failureType 也可能来自下游 Flow 操作符，
     * 不能把所有 Failed 都解释成业务 callback 失败。
     */
    class SubscriptionEnded(
        override val channel: FlowBusDiagnosticChannel,
        val subscriptionId: Long,
        val reason: FlowBusDiagnosticEndReason,
        val failureType: String?
    ) : FlowBusDiagnosticEvent()

    class Failure(
        override val channel: FlowBusDiagnosticChannel,
        val subscriptionId: Long,
        val phase: FlowBusDiagnosticFailurePhase,
        val exceptionType: String
    ) : FlowBusDiagnosticEvent()
}

/**
 * 同步接收排障元数据，允许不同线程并发调用。应快速返回，避免阻塞业务线程。
 *
 * 普通异常（包括观察器自身抛出的取消异常）被隔离，JVM 致命错误不作恢复保证。
 * 观察器中同步重入 FlowBus 的业务操作仍执行，同一线程嵌套诊断被抑制。
 * eventName/scopeName 也可能敏感，向外导出前由应用脱敏。
 */
fun interface FlowBusDiagnosticObserver {
    fun onDiagnostic(event: FlowBusDiagnosticEvent)
}

/**
 * 幂等关闭，只移除对应观察器，不改变业务订阅或 sticky。
 * 关闭不等待已开始的通知，允许在观察器回调中自关闭。
 */
interface FlowBusDiagnosticRegistration : AutoCloseable {
    override fun close()
}
