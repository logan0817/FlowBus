package com.logan.flowbus.composeconsumer

import android.app.Application
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.repeatOnLifecycle
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.eventKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update

private val toastKey = eventKey<String>("consumer.toast")
private val stickyKey = eventKey<String>("consumer.latest")

/** 应用组装入口；基础库不依赖任何 DI 框架。 */
class ConsumerApplication : Application() {
    val rootBus = FlowBus()
}

/** 构造注入同一根实例和独立会话实例；可恢复业务状态由 StateFlow 表达。 */
class ConsumerScreenModel(
    val rootBus: FlowBus,
    val sessionBus: FlowBus
) : ViewModel() {
    private val mutableCount = MutableStateFlow(0)
    val count: StateFlow<Int> = mutableCount.asStateFlow()

    fun incrementState() {
        mutableCount.update { it + 1 }
    }

    fun postRootToast() {
        rootBus.post(toastKey, "根事件")
    }

    fun postSessionToast() {
        sessionBus.post(toastKey, "会话事件")
    }

    fun postLatest() {
        sessionBus.postSticky(stickyKey, "最近结果 ${count.value}")
    }
}

class ConsumerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val rootBus = (application as ConsumerApplication).rootBus
        val model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == ConsumerScreenModel::class.java)
                return modelClass.cast(ConsumerScreenModel(rootBus, FlowBus()))!!
            }
        })[ConsumerScreenModel::class.java]
        setContent {
            if (intent.getBooleanExtra("flowbus.compose.verifyPolicies", false)) {
                ComposePolicyVerification(intent.getStringExtra("flowbus.consumer.runId") ?: "manual")
            } else if (intent.getBooleanExtra("flowbus.compose.verifyEffects", false)) {
                ComposeEffectVerification(intent.getStringExtra("flowbus.consumer.runId") ?: "manual")
            } else {
                ConsumerScreen(model = model, owner = this@ConsumerActivity)
            }
        }
    }
}

@Composable
private fun ConsumerScreen(model: ConsumerScreenModel, owner: LifecycleOwner) {
    val context = LocalContext.current
    var rootMessage by remember { mutableStateOf("未收到根事件") }
    var sessionMessage by remember { mutableStateOf("未收到会话事件") }
    var latestMessage by remember { mutableStateOf("暂无 sticky 结果") }
    var callbackRevision by remember { mutableStateOf(1) }
    var showSticky by remember { mutableStateOf(true) }
    var visibleCount by remember { mutableStateOf(model.count.value) }
    // 捕获本次组合的普通值，以验证回调更新不依赖重建事件订阅。
    val callbackTag = "回调版本 $callbackRevision"

    FlowBusEffect(from = model.rootBus, key = toastKey, owner = owner) { event ->
        delay(100)
        rootMessage = "$event / $callbackTag"
        Toast.makeText(context, rootMessage, Toast.LENGTH_SHORT).show()
    }
    FlowBusEffect(from = model.sessionBus, key = toastKey, owner = owner) { event ->
        delay(100)
        sessionMessage = "$event / $callbackTag"
        Toast.makeText(context, sessionMessage, Toast.LENGTH_SHORT).show()
    }
    if (showSticky) {
        FlowBusEffect(from = model.sessionBus, key = stickyKey, owner = owner, isSticky = true) {
            latestMessage = it
        }
    }

    // 业务状态恢复通过 StateFlow；普通通知不承担离线补收或进程死亡恢复。
    LaunchedEffect(model, owner) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            model.count.collect { visibleCount = it }
        }
    }

    AndroidView(
        factory = { viewContext ->
            LinearLayout(viewContext).apply {
                orientation = LinearLayout.VERTICAL
                val inset = (16 * resources.displayMetrics.density).toInt()
                setPadding(inset, inset, inset, inset)
                addView(TextView(viewContext))
                repeat(6) { addView(Button(viewContext)) }
            }
        },
        update = { layout ->
            (layout.getChildAt(0) as TextView).text =
                buildString {
                    appendLine("FlowBus Compose consumer")
                    appendLine("业务状态: $visibleCount")
                    appendLine(rootMessage)
                    appendLine(sessionMessage)
                    appendLine(latestMessage)
                    append(callbackTag)
                }
            (layout.getChildAt(1) as Button).apply {
                text = "发送普通根 Toast"
                setOnClickListener { model.postRootToast() }
            }
            (layout.getChildAt(2) as Button).apply {
                text = "发送普通会话 Toast"
                setOnClickListener { model.postSessionToast() }
            }
            (layout.getChildAt(3) as Button).apply {
                text = "更新回调版本（不重建订阅）"
                setOnClickListener { callbackRevision += 1 }
            }
            (layout.getChildAt(4) as Button).apply {
                text = "更新 StateFlow 业务状态"
                setOnClickListener { model.incrementState() }
            }
            (layout.getChildAt(5) as Button).apply {
                text = "发送 sticky 最近结果"
                setOnClickListener { model.postLatest() }
            }
            (layout.getChildAt(6) as Button).apply {
                text = if (showSticky) "移除 sticky effect" else "恢复 sticky effect"
                setOnClickListener { showSticky = !showSticky }
            }
        }
    )
}
