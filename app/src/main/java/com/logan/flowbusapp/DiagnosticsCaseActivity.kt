package com.logan.flowbusapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.logan.flowbus.core.FlowBus
import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.FlowBusDiagnosticEvent
import com.logan.flowbus.core.FlowBusDiagnosticRegistration
import com.logan.flowbus.core.FlowBusErrorHandler
import com.logan.flowbus.core.FlowBusLogger
import com.logan.flowbus.core.eventKey
import com.logan.flowbusapp.databinding.ActivityDiagnosticsCaseBinding
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import java.util.ArrayDeque

/** 用页面独占的两个来源复现排障场景，不修改应用其他示例的全局配置。 */
class DiagnosticsCaseActivity : AppCompatActivity() {
    private val config = FlowBusConfig(
        normalBufferCapacity = 0,
        overflowPolicy = BufferOverflow.SUSPEND,
        logger = FlowBusLogger.None,
        errorHandler = FlowBusErrorHandler.Ignore
    )
    private val source = FlowBus(config)
    private val otherSource = FlowBus(config)
    private val channel = eventKey<Unit>("diagnostics.command")
    private val offlineChannel = eventKey<Unit>("diagnostics.offline")
    private val records = ArrayDeque<String>()
    private var registrations = emptyList<FlowBusDiagnosticRegistration>()
    private var _binding: ActivityDiagnosticsCaseBinding? = null
    private val binding get() = requireNotNull(_binding)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityDiagnosticsCaseBinding.inflate(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.updatePadding(top = insets.top)
            binding.scrollDiagnosticsContent.updatePadding(bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.diagnostics_case_title)
        registrations = listOf(
            source.observeDiagnostics { event -> appendRecord("A", event) },
            otherSource.observeDiagnostics { event -> appendRecord("B", event) }
        )
        setReady(false)
        binding.btnDiagnosticsNoSubscriber.setOnClickListener { source.post(offlineChannel, Unit) }
        binding.btnDiagnosticsWrongSource.setOnClickListener { otherSource.post(channel, Unit) }
        binding.btnDiagnosticsRejected.setOnClickListener { source.post(channel, Unit) }
        binding.btnDiagnosticsCallback.setOnClickListener {
            lifecycleScope.launch { source.emit(channel, Unit) }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                try {
                    source.collectSuspending(channel, onSubscribed = { setReady(true) }) {
                        throw IllegalStateException()
                    }
                } finally {
                    setReady(false)
                }
            }
        }
    }

    private fun setReady(ready: Boolean) {
        val currentBinding = _binding ?: return
        currentBinding.btnDiagnosticsWrongSource.isEnabled = ready
        currentBinding.btnDiagnosticsRejected.isEnabled = ready
        currentBinding.btnDiagnosticsCallback.isEnabled = ready
        currentBinding.tvDiagnosticsState.setText(
            if (ready) R.string.diagnostics_case_ready else R.string.diagnostics_case_inactive
        )
    }

    private fun appendRecord(label: String, event: FlowBusDiagnosticEvent) {
        val channel = event.channel
        val details = when (event) {
            is FlowBusDiagnosticEvent.Published ->
                "${event.mode}: ${event.result.outcome}, subscribers=${event.result.subscriptionCount}"
            is FlowBusDiagnosticEvent.SubscriptionStarted -> "SubscriptionStarted #${event.subscriptionId}"
            is FlowBusDiagnosticEvent.SubscriptionEnded ->
                "SubscriptionEnded #${event.subscriptionId}: ${event.reason}"
            is FlowBusDiagnosticEvent.Failure ->
                "Failure #${event.subscriptionId}: ${event.phase}, ${event.exceptionType}"
            is FlowBusDiagnosticEvent.EmitCancelled -> "EmitCancelled"
            is FlowBusDiagnosticEvent.EmitFailed -> "EmitFailed: ${event.exceptionType}"
        }
        // 仅保存有界文本元数据，不保留事件、owner 或 Throwable；所有列表/UI 更新回到主线程。
        val line = "$label bus=${channel.busId}, flow=${channel.flowId}, ${channel.eventName}\n$details"
        runOnUiThread {
            val currentBinding = _binding ?: return@runOnUiThread
            if (records.size == 40) records.removeFirst()
            records.addLast(line)
            currentBinding.tvDiagnosticsLog.text = records.joinToString("\n\n")
        }
    }

    override fun onDestroy() {
        registrations.forEach { it.close() }
        registrations = emptyList()
        _binding = null
        records.clear()
        super.onDestroy()
    }
}
