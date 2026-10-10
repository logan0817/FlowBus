package com.logan.flowbus.composeconsumer

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.logan.flowbus.core.EventKey
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withTimeout

private const val HOME_ROUTE = "home"
private const val DETAIL_ROUTE = "detail"

private class HostProbe(val hold: Boolean = false) {
    val receivers = mutableListOf<NavBackStackEntry>()
    val entered = CompletableDeferred<NavBackStackEntry>()
    val cancelled = CompletableDeferred<Unit>()

    fun receive(entry: NavBackStackEntry) {
        receivers.add(entry)
        entered.complete(entry)
    }
}

private fun startedKey(route: String) = eventKey<HostProbe>("navhost.started.$route")
private fun resumedKey(route: String) = eventKey<HostProbe>("navhost.resumed.$route")
private fun latestKey(route: String) = eventKey<HostProbe>("navhost.latest.$route")
private fun NavBackStackEntry.ownerState() = (this as LifecycleOwner).lifecycle.currentState

/** 仅测试：跨 Activity 重建保留来源与证据，页面/返回栈仍由真实 NavHost 保存和恢复。 */
private class HostVerificationModel : ViewModel() {
    val source = FlowBus()
    val phases = mutableListOf<String>()
    var restoring = false
    var animatedWindow = false
    val composedEntries = mutableSetOf<NavBackStackEntry>()
    var oldDetail: NavBackStackEntry? = null
    var oldPending: HostProbe? = null
    var latestDetail: HostProbe? = null
    var latestHome: HostProbe? = null

    fun clearOldReferences() {
        oldDetail = null
        oldPending = null
        latestDetail = null
        latestHome = null
    }
}

class NavHostConsumerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == HostVerificationModel::class.java)
                return modelClass.cast(HostVerificationModel())!!
            }
        })[HostVerificationModel::class.java]
        val runId = intent.getStringExtra("flowbus.consumer.runId") ?: "manual"
        setContent { NavHostVerification(this, model, runId) }
    }
}

@Composable
private fun HostPage(model: HostVerificationModel, route: String, entry: NavBackStackEntry, active: Boolean) {
    DisposableEffect(entry) {
        model.composedEntries.add(entry)
        onDispose { model.composedEntries.remove(entry) }
    }
    if (active) {
        FlowBusEffect(model.source, startedKey(route), entry) { it.receive(entry) }
        FlowBusEffect(model.source, resumedKey(route), entry, minState = Lifecycle.State.RESUMED) { probe ->
            probe.receive(entry)
            if (probe.hold) {
                try { awaitCancellation() } finally { probe.cancelled.complete(Unit) }
            }
        }
        FlowBusEffect(model.source, latestKey(route), entry, isSticky = true) { it.receive(entry) }
    }
    AndroidView(factory = { TextView(it) }, update = { it.text = "NavHost page=$route" })
}

@Composable
private fun NavHostVerification(activity: ComponentActivity, model: HostVerificationModel, runId: String) {
    val controller = rememberNavController()
    var status by remember { mutableStateOf("RUNNING runId=$runId") }
    var finished by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        NavHost(controller, startDestination = HOME_ROUTE, modifier = Modifier.weight(1f)) {
            composable(HOME_ROUTE) { HostPage(model, HOME_ROUTE, it, !finished) }
            composable(DETAIL_ROUTE) { HostPage(model, DETAIL_ROUTE, it, !finished) }
        }
        AndroidView(factory = { TextView(it) }, update = {
            it.text = status + System.lineSeparator() + model.phases.joinToString(",") +
                System.lineSeparator() + "animatedWindow=${model.animatedWindow} restored=${model.restoring}"
        })
    }

    LaunchedEffect(controller, runId) {
        val source = model.source
        var recreating = false

        fun passed(name: String) {
            model.phases.add(name)
            status = "RUNNING runId=$runId phases=${model.phases.joinToString(",")}"
        }

        fun count(key: EventKey<HostProbe>) = source.inspect().root.events
            .firstOrNull { it.eventName == key.name }?.subscriptionCount ?: 0

        suspend fun ready(entry: NavBackStackEntry, route: String) {
            while (entry.ownerState() != Lifecycle.State.RESUMED ||
                count(startedKey(route)) != 1 || count(resumedKey(route)) != 1 || count(latestKey(route)) != 1) {
                withFrameNanos { }
            }
        }

        suspend fun stopped(entry: NavBackStackEntry, route: String, state: Lifecycle.State) {
            while (entry.ownerState() != state || count(startedKey(route)) != 0 ||
                count(resumedKey(route)) != 0 || count(latestKey(route)) != 0) withFrameNanos { }
        }

        suspend fun delivered(probe: HostProbe, entry: NavBackStackEntry) {
            while (probe.receivers.none { it === entry }) withFrameNanos { }
        }

        suspend fun receive(entry: NavBackStackEntry, route: String, hold: Boolean = false): HostProbe {
            ready(entry, route)
            val probe = HostProbe(hold)
            check(source.post(resumedKey(route), probe))
            check(probe.entered.await() === entry)
            return probe
        }

        suspend fun current(route: String): NavBackStackEntry {
            while (controller.currentBackStackEntry?.destination?.route != route) withFrameNanos { }
            return checkNotNull(controller.currentBackStackEntry)
        }

        try {
            withTimeout(30_000) {
                if (!model.restoring) {
                    val home = current(HOME_ROUTE)
                    receive(home, HOME_ROUTE)
                    passed("home-active")

                    val homePending = receive(home, HOME_ROUTE, hold = true)
                    controller.navigate(DETAIL_ROUTE)
                    val detail = current(DETAIL_ROUTE)
                    homePending.cancelled.await()
                    if (NAV_HOST_EXPECT_ANIMATED_TRANSITION) {
                        // 旧页虽仍在退出动画中，其 owner 已 CREATED；新页先 STARTED 再 RESUMED。
                        while (!model.composedEntries.containsAll(listOf(home, detail)) ||
                            detail.ownerState() != Lifecycle.State.STARTED ||
                            count(startedKey(DETAIL_ROUTE)) != 1 || count(startedKey(HOME_ROUTE)) != 0) {
                            check(detail.ownerState() != Lifecycle.State.RESUMED) { "Animation window was not observed" }
                            withFrameNanos { }
                        }
                        check(home.ownerState() == Lifecycle.State.CREATED)
                        check(count(resumedKey(HOME_ROUTE)) == 0 && count(resumedKey(DETAIL_ROUTE)) == 0)
                        check(count(latestKey(HOME_ROUTE)) == 0)
                        val homeTransition = HostProbe()
                        val detailTransition = HostProbe()
                        check(source.post(startedKey(HOME_ROUTE), homeTransition))
                        check(source.post(startedKey(DETAIL_ROUTE), detailTransition))
                        check(detailTransition.entered.await() === detail)
                        check(!homeTransition.entered.isCompleted)
                        model.animatedWindow = true
                    }
                    ready(detail, DETAIL_ROUTE)
                    stopped(home, HOME_ROUTE, Lifecycle.State.CREATED)
                    passed("transition-owner-thresholds")

                    val missed = HostProbe()
                    val latest = HostProbe()
                    check(source.post(resumedKey(HOME_ROUTE), missed))
                    check(source.postSticky(latestKey(HOME_ROUTE), latest))
                    receive(detail, DETAIL_ROUTE)
                    check(!missed.entered.isCompleted && !latest.entered.isCompleted)
                    passed("covered-home-does-not-receive")

                    val detailPending = receive(detail, DETAIL_ROUTE, hold = true)
                    check(controller.popBackStack())
                    detailPending.cancelled.await()
                    ready(home, HOME_ROUTE)
                    stopped(detail, DETAIL_ROUTE, Lifecycle.State.DESTROYED)
                    delivered(latest, home)
                    check(!missed.entered.isCompleted && controller.currentBackStackEntry === home)
                    passed("pop-restores-sticky-without-normal-replay")

                    val destroyedNormal = HostProbe()
                    val destroyedSticky = HostProbe()
                    check(source.post(resumedKey(DETAIL_ROUTE), destroyedNormal))
                    check(source.postSticky(latestKey(DETAIL_ROUTE), destroyedSticky))
                    receive(home, HOME_ROUTE)
                    check(!destroyedNormal.entered.isCompleted && !destroyedSticky.entered.isCompleted)
                    passed("popped-owner-does-not-receive")

                    controller.navigate(DETAIL_ROUTE)
                    val next = current(DETAIL_ROUTE)
                    receive(next, DETAIL_ROUTE)
                    delivered(destroyedSticky, next)
                    check(next !== detail && detail.ownerState() == Lifecycle.State.DESTROYED)
                    check(!destroyedNormal.entered.isCompleted)
                    passed("new-detail-owner-receives-retained-source")

                    model.oldDetail = next
                    model.oldPending = receive(next, DETAIL_ROUTE, hold = true)
                    model.latestDetail = HostProbe().also { check(source.postSticky(latestKey(DETAIL_ROUTE), it)) }
                    delivered(checkNotNull(model.latestDetail), next)
                    model.latestHome = latest
                    model.restoring = true
                    recreating = true
                    activity.recreate()
                    awaitCancellation()
                } else {
                    val restored = current(DETAIL_ROUTE)
                    ready(restored, DETAIL_ROUTE)
                    checkNotNull(model.oldPending).cancelled.await()
                    val oldDetail = checkNotNull(model.oldDetail)
                    check(restored !== oldDetail && oldDetail.ownerState() == Lifecycle.State.DESTROYED)
                    check(controller.previousBackStackEntry?.destination?.route == HOME_ROUTE)
                    delivered(checkNotNull(model.latestDetail), restored)
                    receive(restored, DETAIL_ROUTE)
                    passed("activity-recreation-restores-detail-owner")

                    val pending = receive(restored, DETAIL_ROUTE, hold = true)
                    activity.onBackPressedDispatcher.onBackPressed()
                    pending.cancelled.await()
                    val restoredHome = current(HOME_ROUTE)
                    ready(restoredHome, HOME_ROUTE)
                    stopped(restored, DETAIL_ROUTE, Lifecycle.State.DESTROYED)
                    delivered(checkNotNull(model.latestHome), restoredHome)
                    receive(restoredHome, HOME_ROUTE)
                    check(model.phases.size == 7)
                    passed("restored-back-stack-returns-home")
                    status = "PASS API=${Build.VERSION.SDK_INT} checks=${model.phases.size} runId=$runId"
                    Log.i("FlowBusNavHost", status)
                }
            }
        } catch (error: CancellationException) {
            if (error !is TimeoutCancellationException) throw error
            status = "FAIL runId=$runId phase=${model.phases.lastOrNull()}"
            Log.e("FlowBusNavHost", status, error)
        } catch (error: Exception) {
            status = "FAIL runId=$runId phase=${model.phases.lastOrNull()}"
            Log.e("FlowBusNavHost", status, error)
        } finally {
            if (!recreating) {
                finished = true
                model.clearOldReferences()
            }
        }
    }
}
