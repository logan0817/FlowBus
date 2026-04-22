package com.logan.flowbusapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.logan.flowbus.collectEvent
import com.logan.flowbus.eventFlow
import com.logan.flowbus.postEvent
import com.logan.flowbusapp.databinding.ActivityGlobalCaseBinding
import com.logan.flowbusapp.event.GlobalEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class GlobalCaseActivity : AppCompatActivity() {

    private data class GlobalCaseUiState(
        val resultState: String,
        val latestSource: String,
        val sendTrace: String,
        val receiveTrace: String
    )

    private var _binding: ActivityGlobalCaseBinding? = null
    private val binding get() = _binding!!

    private lateinit var uiState: GlobalCaseUiState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityGlobalCaseBinding.inflate(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        uiState = createInitialState()
        setupInsets()
        render(uiState)
        setListeners()
        subscribeGlobalEvents()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.updatePadding(top = insets.top)
            binding.scrollGlobalCaseContent.updatePadding(bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.global_case_title)
    }

    private fun setListeners() {
        binding.btnSendGlobalRefresh.setOnClickListener {
            runGlobalCase()
        }
    }

    private fun subscribeGlobalEvents() {
        collectEvent(eventFlow<GlobalEvent>()) { event ->
            if (event.message != globalTriggerSource()) {
                return@collectEvent
            }
            uiState = uiState.copy(
                resultState = getString(R.string.global_case_result_done),
                latestSource = getString(R.string.global_case_source_value, event.message),
                receiveTrace = withTimestamp(getString(R.string.global_case_receive_trace_done))
            )
            render(uiState)
        }
    }

    private fun runGlobalCase() {
        val source = globalTriggerSource()
        uiState = uiState.copy(
            resultState = getString(R.string.global_case_result_sending),
            latestSource = getString(R.string.global_case_source_value, source),
            sendTrace = withTimestamp(getString(R.string.global_case_send_trace_done, source)),
            receiveTrace = getString(R.string.global_case_receive_trace_waiting)
        )
        render(uiState)
        postEvent(GlobalEvent(source))
    }

    private fun createInitialState(): GlobalCaseUiState {
        return GlobalCaseUiState(
            resultState = getString(R.string.global_case_result_waiting),
            latestSource = getString(R.string.global_case_source_waiting),
            sendTrace = getString(R.string.global_case_send_trace_waiting),
            receiveTrace = getString(R.string.global_case_receive_trace_waiting)
        )
    }

    private fun render(state: GlobalCaseUiState) {
        binding.tvGlobalCaseResult.text = state.resultState
        binding.tvGlobalCaseSource.text = state.latestSource
        binding.tvGlobalCaseSendTrace.text = state.sendTrace
        binding.tvGlobalCaseReceiveTrace.text = state.receiveTrace
    }

    private fun globalTriggerSource(): String {
        return getString(R.string.global_case_trigger_source)
    }

    private fun withTimestamp(message: String): String {
        return "${getCurrentTime()} $message"
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Calendar.getInstance().time)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
