package com.logan.flowbus.composeconsumer

import android.os.Build
import android.util.Log
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.FlowBusErrorHandler
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout

private class VerificationOwner : LifecycleOwner {
    val registry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = registry

    init {
        registry.currentState = Lifecycle.State.RESUMED
    }
}

private class EffectProbe(val hold: Boolean = false, val fail: Boolean = false) {
    val entered = CompletableDeferred<Unit>()
    val cancelled = CompletableDeferred<Unit>()
}

/** 仅消费者自检：通过真实组合重建与回调屏障验证，不用固定延时推断订阅就绪。 */
@Composable
internal fun ComposeEffectVerification(runId: String) {
    val errors = remember { mutableListOf<Throwable>() }
    val errorHandled = remember { CompletableDeferred<Unit>() }
    val sources = remember {
        List(2) {
            FlowBus(FlowBusConfig(errorHandler = FlowBusErrorHandler { _, error ->
                errors.add(error)
                errorHandled.complete(Unit)
            }))
        }
    }
    val keys = remember { List(2) { eventKey<EffectProbe>("consumer.effect.$it") } }
    val owners = remember { List(3) { VerificationOwner() } }
    var source by remember { mutableStateOf(sources[0]) }
    var key by remember { mutableStateOf(keys[0]) }
    var owner by remember { mutableStateOf(owners[0]) }
    var minState by remember { mutableStateOf(Lifecycle.State.STARTED) }
    var visible by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("RUNNING runId=$runId") }
    val phases = remember { mutableListOf<String>() }

    if (visible) {
        FlowBusEffect(source, key, owner, minState, isSticky = true) { probe ->
            probe.entered.complete(Unit)
            if (probe.fail) error("expected consumer callback failure")
            if (probe.hold) {
                try {
                    awaitCancellation()
                } finally {
                    probe.cancelled.complete(Unit)
                }
            }
        }
    }

    LaunchedEffect(runId) {
        fun passed(name: String) {
            phases.add(name)
            status = "RUNNING runId=$runId phases=${phases.joinToString(",")}"
        }

        suspend fun receive(hold: Boolean = false, fail: Boolean = false): EffectProbe {
            val probe = EffectProbe(hold, fail)
            check(source.postSticky(key, probe))
            probe.entered.await()
            return probe
        }

        try {
            withTimeout(30_000) {
                receive()
                passed("initial-replay")

                var pending = receive(hold = true)
                val nextSource = EffectProbe()
                check(sources[1].postSticky(keys[0], nextSource))
                source = sources[1]
                pending.cancelled.await()
                nextSource.entered.await()
                passed("source-replaced")

                pending = receive(hold = true)
                val nextKey = EffectProbe()
                check(source.postSticky(keys[1], nextKey))
                key = keys[1]
                pending.cancelled.await()
                nextKey.entered.await()
                passed("key-replaced")

                pending = receive(hold = true)
                val nextOwner = EffectProbe()
                check(source.postSticky(key, nextOwner))
                owner = owners[1]
                pending.cancelled.await()
                nextOwner.entered.await()
                owners[0].registry.currentState = Lifecycle.State.DESTROYED
                receive()
                passed("owner-replaced")

                pending = receive(hold = true)
                owner.registry.currentState = Lifecycle.State.STARTED
                minState = Lifecycle.State.RESUMED
                pending.cancelled.await()
                val resumeProbe = EffectProbe()
                check(source.postSticky(key, resumeProbe))
                check(!resumeProbe.entered.isCompleted)
                owner.registry.currentState = Lifecycle.State.RESUMED
                resumeProbe.entered.await()
                passed("min-state-replaced")

                pending = receive(hold = true)
                visible = false
                pending.cancelled.await()
                val restored = EffectProbe()
                check(source.postSticky(key, restored))
                check(!restored.entered.isCompleted)
                visible = true
                restored.entered.await()
                passed("composition-disposed-restored")

                pending = receive(hold = true)
                owner.registry.currentState = Lifecycle.State.CREATED
                pending.cancelled.await()
                val restarted = EffectProbe()
                check(source.postSticky(key, restarted))
                check(!restarted.entered.isCompleted)
                owner.registry.currentState = Lifecycle.State.RESUMED
                restarted.entered.await()
                passed("lifecycle-stopped-restarted")

                receive(fail = true)
                errorHandled.await()
                check(errors.size == 1 && errors.single() is IllegalStateException)
                receive()
                passed("callback-error-continued")

                pending = receive(hold = true)
                owner.registry.currentState = Lifecycle.State.DESTROYED
                pending.cancelled.await()
                val afterDestroy = EffectProbe()
                check(source.postSticky(key, afterDestroy))
                check(!afterDestroy.entered.isCompleted)
                owner = owners[2]
                afterDestroy.entered.await()
                check(errors.size == 1) // 协作式取消不能交给普通异常策略。
                passed("destroyed-source-retained")
                status = "PASS API=${Build.VERSION.SDK_INT} checks=${phases.size} runId=$runId"
                Log.i("FlowBusCompose", status)
            }
        } catch (error: CancellationException) {
            if (error !is TimeoutCancellationException) throw error
            status = "FAIL runId=$runId phase=${phases.lastOrNull()}"
            Log.e("FlowBusCompose", status, error)
        } catch (error: Exception) {
            status = "FAIL runId=$runId phase=${phases.lastOrNull()}"
            Log.e("FlowBusCompose", status, error)
        } finally {
            visible = false
            owners.forEach { it.registry.currentState = Lifecycle.State.DESTROYED }
        }
    }

    AndroidView(
        factory = { TextView(it) },
        update = { it.text = status + System.lineSeparator() + phases.joinToString(",") }
    )
}
