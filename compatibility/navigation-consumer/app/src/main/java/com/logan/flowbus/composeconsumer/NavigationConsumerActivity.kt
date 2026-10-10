package com.logan.flowbus.composeconsumer

import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelStore
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavGraph
import androidx.navigation.NavGraphNavigator
import androidx.navigation.NavHostController
import androidx.navigation.Navigator
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.EventKey
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withTimeout

private const val HOME = 710001
private const val DETAIL = 710002

/** 使用 Navigation 自带的 push/pop 管理真实返回栈，不替换或模拟 entry 生命周期。 */
@Navigator.Name("flowbus-verification")
private class VerificationNavigator : Navigator<NavDestination>() {
    override fun createDestination(): NavDestination = NavDestination(this)
}

private class NavigationProbe(val hold: Boolean = false) {
    val entered = CompletableDeferred<NavBackStackEntry>()
    val cancelled = CompletableDeferred<Unit>()
}

private fun normalKey(destination: Int) = eventKey<NavigationProbe>("nav.normal.$destination")
private fun stickyKey(destination: Int) = eventKey<NavigationProbe>("nav.sticky.$destination")

// 旧 Navigation 的私有 lifecycle 字段遮蔽 Kotlin 属性访问，统一经公开 owner 契约读取。
private fun NavBackStackEntry.ownerState() = (this as LifecycleOwner).lifecycle.currentState

class NavigationConsumerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val runId = intent.getStringExtra("flowbus.consumer.runId") ?: "manual"
        setContent { NavigationVerification(this, runId) }
    }
}

@Composable
private fun NavigationVerification(activity: ComponentActivity, runId: String) {
    val source = remember { FlowBus() }
    val store = remember { ViewModelStore() }
    val controller = remember {
        NavHostController(activity).apply {
            setLifecycleOwner(activity)
            setViewModelStore(store)
            setOnBackPressedDispatcher(activity.onBackPressedDispatcher)
            enableOnBackPressed(true)
            val navigator = VerificationNavigator()
            navigatorProvider.addNavigator(navigator)
            graph = NavGraph(navigatorProvider.getNavigator<NavGraphNavigator>("navigation")).apply {
                id = 710000
                addDestination(navigator.createDestination().apply { id = HOME })
                addDestination(navigator.createDestination().apply { id = DETAIL })
                setStartDestination(HOME)
            }
        }
    }
    // 保留被覆盖及已弹出的页面组合，确保停止来自真实 entry 生命周期，而非仅靠移除组合。
    val composedEntries = remember { mutableStateListOf<NavBackStackEntry>() }
    var current by remember { mutableStateOf(controller.currentBackStackEntry) }
    var status by remember { mutableStateOf("RUNNING runId=$runId") }
    var finished by remember { mutableStateOf(false) }
    val phases = remember { mutableListOf<String>() }

    LaunchedEffect(controller) {
        controller.currentBackStackEntryFlow.collect { entry ->
            current = entry
            if (!composedEntries.contains(entry)) composedEntries.add(entry)
        }
    }
    if (!finished) {
        composedEntries.forEach { entry ->
            key(entry) {
                val destination = entry.destination.id
                FlowBusEffect(source, normalKey(destination), entry) { probe ->
                    probe.entered.complete(entry)
                    if (probe.hold) {
                        try {
                            awaitCancellation()
                        } finally {
                            probe.cancelled.complete(Unit)
                        }
                    }
                }
                FlowBusEffect(source, stickyKey(destination), entry, isSticky = true) { probe ->
                    probe.entered.complete(entry)
                }
            }
        }
    }
    DisposableEffect(store) { onDispose { store.clear() } }

    LaunchedEffect(controller, runId) {
        fun passed(name: String) {
            phases.add(name)
            status = "RUNNING runId=$runId phases=${phases.joinToString(",")}"
        }

        // 仅测试用就绪条件；诊断快照不用于正式业务控制，也不代表业务处理完成。
        suspend fun subscribers(key: EventKey<NavigationProbe>, expected: Int) {
            while (source.inspect().root.events.firstOrNull { it.eventName == key.name }
                    ?.subscriptionCount != expected) {
                withFrameNanos { }
            }
        }

        suspend fun receive(entry: NavBackStackEntry, hold: Boolean = false): NavigationProbe {
            val key = normalKey(entry.destination.id)
            subscribers(key, 1)
            subscribers(stickyKey(entry.destination.id), 1)
            val probe = NavigationProbe(hold)
            check(source.post(key, probe))
            check(probe.entered.await() === entry)
            return probe
        }

        try {
            withTimeout(30_000) {
                val home = checkNotNull(controller.currentBackStackEntry)
                receive(home)
                check(home.ownerState().isAtLeast(Lifecycle.State.STARTED))
                passed("home-active")

                val homePending = receive(home, hold = true)
                controller.navigate(DETAIL)
                val detail = checkNotNull(controller.currentBackStackEntry)
                homePending.cancelled.await()
                subscribers(normalKey(HOME), 0)
                subscribers(stickyKey(HOME), 0)
                receive(detail)
                check(home.ownerState() == Lifecycle.State.CREATED)
                passed("navigate-stops-covered-home")

                val missed = NavigationProbe()
                val latest = NavigationProbe()
                check(source.post(normalKey(HOME), missed))
                check(source.postSticky(stickyKey(HOME), latest))
                check(!missed.entered.isCompleted && !latest.entered.isCompleted)
                receive(detail)
                passed("covered-home-does-not-receive")

                val detailPending = receive(detail, hold = true)
                check(controller.popBackStack())
                detailPending.cancelled.await()
                check(latest.entered.await() === home)
                receive(home)
                check(!missed.entered.isCompleted)
                check(controller.currentBackStackEntry === home)
                check(detail.ownerState() == Lifecycle.State.DESTROYED)
                passed("pop-restores-sticky-without-normal-replay")

                subscribers(normalKey(DETAIL), 0)
                subscribers(stickyKey(DETAIL), 0)
                val destroyedNormal = NavigationProbe()
                val destroyedSticky = NavigationProbe()
                check(source.post(normalKey(DETAIL), destroyedNormal))
                check(source.postSticky(stickyKey(DETAIL), destroyedSticky))
                receive(home)
                check(!destroyedNormal.entered.isCompleted && !destroyedSticky.entered.isCompleted)
                passed("popped-owner-does-not-receive")

                controller.navigate(DETAIL)
                val recreated = checkNotNull(controller.currentBackStackEntry)
                check(recreated !== detail)
                check(destroyedSticky.entered.await() === recreated)
                receive(recreated)
                check(!destroyedNormal.entered.isCompleted)
                check(detail.ownerState() == Lifecycle.State.DESTROYED)
                passed("new-detail-owner-receives-retained-source")

                val recreatedPending = receive(recreated, hold = true)
                activity.onBackPressedDispatcher.onBackPressed()
                recreatedPending.cancelled.await()
                receive(home)
                check(controller.currentBackStackEntry === home)
                check(recreated.ownerState() == Lifecycle.State.DESTROYED)
                passed("back-dispatcher-returns-home")
                status = "PASS API=${Build.VERSION.SDK_INT} checks=${phases.size} runId=$runId"
                Log.i("FlowBusNavigation", status)
            }
        } catch (error: CancellationException) {
            if (error !is TimeoutCancellationException) throw error
            status = "FAIL runId=$runId phase=${phases.lastOrNull()}"
            Log.e("FlowBusNavigation", status, error)
        } catch (error: Exception) {
            status = "FAIL runId=$runId phase=${phases.lastOrNull()}"
            Log.e("FlowBusNavigation", status, error)
        } finally {
            finished = true
        }
    }

    AndroidView(
        factory = { TextView(it) },
        update = { it.text = status + System.lineSeparator() + phases.joinToString(",") +
            System.lineSeparator() + "destination=${current?.destination?.id}" }
    )
}
