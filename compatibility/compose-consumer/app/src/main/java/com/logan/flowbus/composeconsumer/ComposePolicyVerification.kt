package com.logan.flowbus.composeconsumer

import android.os.Build
import android.util.Log
import android.widget.TextView
import androidx.compose.runtime.AbstractApplier
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Composition
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.logan.flowbus.core.EventKey
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.FlowBusErrorContext
import com.logan.flowbus.core.FlowBusErrorHandler
import com.logan.flowbus.core.FlowBusErrorPhase
import com.logan.flowbus.core.FlowBusLogger
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.concurrent.CountDownLatch
import kotlin.coroutines.CoroutineContext

private class PolicyOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    init { registry.currentState = Lifecycle.State.RESUMED }
}

private class PolicyProbe(val failure: Exception? = null) {
    val entered = CompletableDeferred<Unit>()
    val finished = CompletableDeferred<Unit>()
}

private class PolicyLogger : FlowBusLogger {
    val warnings = mutableListOf<Throwable?>()
    override fun warn(tag: String, message: String, throwable: Throwable?) { warnings.add(throwable) }
}

/** 此组合只包含 effect，没有 UI 节点；结果由外层真实 Activity 的组合呈现。 */
private class EffectOnlyApplier : AbstractApplier<Unit>(Unit) {
    override fun insertTopDown(index: Int, instance: Unit) = error("Unexpected UI node")
    override fun insertBottomUp(index: Int, instance: Unit) = error("Unexpected UI node")
    override fun remove(index: Int, count: Int) = Unit
    override fun move(from: Int, to: Int, count: Int) = Unit
    override fun onClear() = Unit
}

/**
 * 测试专用独立 Recomposer：记录预期 effect 异常及 runner 终止原因，隔离外层结果组合。
 * 不修改 FlowBusEffect 或其错误策略；普通接入的 Rethrow 仍传播到调用方的组合上下文。
 */
private class PolicyComposition(
    context: CoroutineContext,
    source: FlowBus,
    key: EventKey<PolicyProbe>,
    val owner: PolicyOwner = PolicyOwner(),
    sticky: Boolean = false,
    callback: suspend (PolicyProbe) -> Unit = { it.failure?.let { failure -> throw failure } }
) {
    val failure = CompletableDeferred<Throwable>()
    private val supervisor = SupervisorJob(context[Job])
    private val scope = CoroutineScope(context + supervisor + CoroutineExceptionHandler { _, error ->
        failure.complete(error)
    })
    private val recomposer = Recomposer(scope.coroutineContext)
    private val runner = scope.launch { recomposer.runRecomposeAndApplyChanges() }.also { job ->
        job.invokeOnCompletion { cause ->
            // Recomposer 会把 effect 失败包装为 runner 的取消原因，不保证交给异常处理器。
            var error = cause
            while (error is CancellationException) error = error.cause
            if (error != null) failure.complete(error)
        }
    }
    private val composition = Composition(EffectOnlyApplier(), recomposer)

    init {
        composition.setContent {
            FlowBusEffect(source, key, owner, isSticky = sticky) { probe ->
                probe.entered.complete(Unit)
                try { callback(probe) } finally { probe.finished.complete(Unit) }
            }
        }
    }

    suspend fun close() {
        owner.registry.currentState = Lifecycle.State.DESTROYED
        composition.dispose()
        recomposer.cancel()
        runner.cancelAndJoin()
        supervisor.cancelAndJoin()
    }

    suspend fun awaitFailure(): Throwable {
        val error = failure.await()
        recomposer.join()
        check(runner.isCancelled)
        return error
    }
}

/** 真实 Compose runtime 的错误策略及受控非协作取消验收，不强制终止回调。 */
@Composable
internal fun ComposePolicyVerification(runId: String) {
    var status by remember { mutableStateOf("RUNNING runId=$runId") }
    val phases = remember { mutableListOf<String>() }
    LaunchedEffect(runId) {
        val key = eventKey<PolicyProbe>("consumer.policy")
        val sessions = mutableListOf<PolicyComposition>()
        val suspendGate = CompletableDeferred<Unit>()
        val workerGate = CountDownLatch(1)
        var step = "default-subscription"

        fun passed(name: String) {
            phases.add(name)
            status = "RUNNING runId=$runId phases=${phases.joinToString(",")}"
            step = "$name/next"
        }

        suspend fun subscribers(source: FlowBus, expected: Int) {
            while (source.inspect().root.events.firstOrNull { it.eventName == key.name }
                    ?.subscriptionCount != expected) withFrameNanos { }
        }

        suspend fun receive(source: FlowBus, failure: Exception? = null): PolicyProbe {
            subscribers(source, 1)
            val probe = PolicyProbe(failure)
            check(source.post(key, probe))
            step += "/entered"
            probe.entered.await()
            step += "/finished"
            probe.finished.await()
            return probe
        }

        try {
            withTimeout(30_000) {
                suspend fun session(source: FlowBus, sticky: Boolean = false,
                                    callback: suspend (PolicyProbe) -> Unit = {
                                        it.failure?.let { failure -> throw failure }
                                    }): PolicyComposition = PolicyComposition(
                    currentCoroutineContext(), source, key, sticky = sticky, callback = callback
                ).also { sessions.add(it) }

                // 不覆盖默认 errorHandler；最外层测试 Recomposer 观察传播后的同一异常。
                val defaultLogger = PolicyLogger()
                val defaultSource = FlowBus(FlowBusConfig(logger = defaultLogger))
                val defaultSession = session(defaultSource)
                val callbackFailure = IllegalStateException("expected default failure")
                receive(defaultSource, callbackFailure)
                step = "default-error-propagation"
                check(defaultSession.awaitFailure() === callbackFailure)
                step = "default-unsubscribe"
                subscribers(defaultSource, 0)
                val missed = PolicyProbe()
                check(defaultSource.post(key, missed))
                check(!missed.entered.isCompleted)
                check(defaultLogger.warnings.single() === callbackFailure)
                defaultSession.close()
                step = "default-replacement"
                val replacement = session(defaultSource)
                receive(defaultSource)
                replacement.close()
                passed("default-rethrow-terminates-source-retained")

                val ignoreLogger = PolicyLogger()
                val ignoreSource = FlowBus(FlowBusConfig(logger = ignoreLogger,
                    errorHandler = FlowBusErrorHandler.Ignore))
                val ignoreSession = session(ignoreSource)
                receive(ignoreSource, callbackFailure)
                receive(ignoreSource)
                check(ignoreLogger.warnings.single() === callbackFailure)
                check(!ignoreSession.failure.isCompleted)
                ignoreSession.close()
                passed("ignore-logs-and-continues")

                val contexts = mutableListOf<FlowBusErrorContext>()
                val handled = mutableListOf<Throwable>()
                val customSource = FlowBus(FlowBusConfig(errorHandler = FlowBusErrorHandler { context, error ->
                    contexts.add(context)
                    handled.add(error)
                }))
                val customSession = session(customSource)
                receive(customSource, callbackFailure)
                receive(customSource)
                val context = contexts.single()
                check(handled.single() === callbackFailure)
                check(context.phase == FlowBusErrorPhase.SubscriberCallback && context.eventName == key.name)
                check(context.expectedValueType == PolicyProbe::class && context.actualValueType == PolicyProbe::class)
                check(context.scopeName == null && !context.isSticky && context.dispatcher == null)
                check(!customSession.failure.isCompleted)
                customSession.close()
                passed("custom-handler-context-and-continue")

                val legacyErrors = mutableListOf<Throwable>()
                val legacySource = FlowBus(FlowBusConfig(errorHandler =
                    FlowBusErrorHandler.fromThrowableHandler { legacyErrors.add(it) }))
                val legacySession = session(legacySource)
                receive(legacySource, callbackFailure)
                receive(legacySource)
                check(legacyErrors.single() === callbackFailure && !legacySession.failure.isCompleted)
                legacySession.close()
                passed("legacy-handler-continues")

                val handlerFailure = IllegalArgumentException("expected handler failure")
                val throwingErrors = mutableListOf<Throwable>()
                val throwingSource = FlowBus(FlowBusConfig(errorHandler = FlowBusErrorHandler { _, error ->
                    throwingErrors.add(error)
                    throw handlerFailure
                }))
                val throwingSession = session(throwingSource)
                receive(throwingSource, callbackFailure)
                check(throwingSession.awaitFailure() === handlerFailure)
                check(throwingErrors.single() === callbackFailure)
                subscribers(throwingSource, 0)
                throwingSession.close()
                passed("handler-failure-propagates")

                val cancellationLogger = PolicyLogger()
                val cancellationErrors = mutableListOf<Throwable>()
                val cancellationSource = FlowBus(FlowBusConfig(logger = cancellationLogger,
                    errorHandler = FlowBusErrorHandler { _, error -> cancellationErrors.add(error) }))
                val cancellationSession = session(cancellationSource)
                receive(cancellationSource, CancellationException("expected callback cancellation"))
                subscribers(cancellationSource, 0)
                check(cancellationLogger.warnings.isEmpty() && cancellationErrors.isEmpty())
                check(!cancellationSession.failure.isCompleted)
                cancellationSession.owner.registry.currentState = Lifecycle.State.CREATED
                cancellationSession.owner.registry.currentState = Lifecycle.State.RESUMED
                receive(cancellationSource)
                cancellationSession.close()
                passed("cancellation-bypasses-error-handler")

                suspend fun nonCooperative(blockingWorker: Boolean) {
                    val logger = PolicyLogger()
                    val errors = mutableListOf<Throwable>()
                    val source = FlowBus(FlowBusConfig(logger = logger,
                        errorHandler = FlowBusErrorHandler { _, error -> errors.add(error) }))
                    val holding = PolicyProbe()
                    val originalJob = CompletableDeferred<Job>()
                    val workFinished = CompletableDeferred<Unit>()
                    val next = PolicyProbe()
                    var activeCallbacks = 0
                    val composition = session(source, sticky = true) { probe ->
                        check(activeCallbacks == 0) { "Overlapping lifecycle callbacks" }
                        activeCallbacks++
                        try {
                            if (probe === holding) {
                                val job = checkNotNull(currentCoroutineContext()[Job])
                                if (blockingWorker) {
                                    withContext(Dispatchers.Default) {
                                        originalJob.complete(job)
                                        workerGate.await()
                                        workFinished.complete(Unit)
                                    }
                                } else {
                                    withContext(NonCancellable) {
                                        originalJob.complete(job)
                                        suspendGate.await()
                                        workFinished.complete(Unit)
                                    }
                                }
                            } else {
                                // 新一次生命周期收集必须等旧回调结束，不与其并发。
                                check(probe === next && holding.finished.isCompleted)
                            }
                        } finally {
                            activeCallbacks--
                        }
                    }
                    subscribers(source, 1)
                    check(source.postSticky(key, holding))
                    val job = originalJob.await()
                    composition.owner.registry.currentState = Lifecycle.State.CREATED
                    while (!job.isCancelled) withFrameNanos { }
                    composition.owner.registry.currentState = Lifecycle.State.RESUMED
                    check(source.inspect().root.events.single { it.eventName == key.name }.subscriptionCount == 1)
                    check(source.postSticky(key, next))
                    check(!job.isCompleted && !holding.finished.isCompleted && !next.entered.isCompleted)
                    check(!workFinished.isCompleted)
                    // 取消请求已到达，但工作只在显式释放后才能结束。
                    if (blockingWorker) workerGate.countDown() else suspendGate.complete(Unit)
                    workFinished.await()
                    holding.finished.await()
                    next.entered.await()
                    next.finished.await()
                    check(logger.warnings.isEmpty() && errors.isEmpty() && !composition.failure.isCompleted)
                    composition.close()
                }
                nonCooperative(blockingWorker = false)
                passed("noncancellable-restart-waits-for-callback")
                nonCooperative(blockingWorker = true)
                passed("blocking-worker-restart-waits-for-callback")
                status = "PASS API=${Build.VERSION.SDK_INT} checks=${phases.size} runId=$runId"
                Log.i("FlowBusComposePolicy", status)
            }
        } catch (error: CancellationException) {
            if (error !is TimeoutCancellationException) throw error
            status = "FAIL runId=$runId phase=${phases.lastOrNull()} step=$step"
            Log.e("FlowBusComposePolicy", status, error)
        } catch (error: Exception) {
            status = "FAIL runId=$runId phase=${phases.lastOrNull()} step=$step"
            Log.e("FlowBusComposePolicy", status, error)
        } finally {
            suspendGate.complete(Unit)
            workerGate.countDown()
            withContext(NonCancellable) { sessions.forEach { it.close() } }
        }
    }
    AndroidView(factory = { TextView(it) },
        update = { it.text = status + System.lineSeparator() + phases.joinToString(",") })
}
