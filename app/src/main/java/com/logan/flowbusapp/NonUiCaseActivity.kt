package com.logan.flowbusapp

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.logan.flowbus.core.DefaultFlowBus
import com.logan.flowbusapp.databinding.ActivityNonUiCaseBinding
import com.logan.flowbusapp.nonui.MainDemoViewModel
import com.logan.flowbusapp.nonui.RepositoryDemoLog
import com.logan.flowbusapp.nonui.ViewModelDemoLog
import com.logan.flowbusapp.nonui.WorkerDemoLog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class NonUiCaseActivity : AppCompatActivity() {

    private val viewModel by viewModels<MainDemoViewModel>()

    private var _binding: ActivityNonUiCaseBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityNonUiCaseBinding.inflate(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        setupInsets()
        renderInitialState()
        setListeners()
        subscribeNonUiLogs()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.updatePadding(top = insets.top)
            binding.scrollNonUiContent.updatePadding(bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.non_ui_case_title)
    }

    private fun renderInitialState() {
        binding.tvUiResult.text = getString(R.string.non_ui_case_ui_result_waiting)
        binding.tvViewModelLog.text = getString(R.string.non_ui_case_view_model_waiting)
        binding.tvRepositoryLog.text = getString(R.string.non_ui_case_repository_waiting)
        binding.tvWorkerLog.text = getString(R.string.non_ui_case_worker_waiting)
    }

    private fun setListeners() {
        binding.btnStartSyncFlow.setOnClickListener {
            binding.tvUiResult.text = withTimestamp(getString(R.string.non_ui_case_ui_result_running))
            viewModel.runNonUiDemo(trigger = triggerSource())
        }
    }

    private fun subscribeNonUiLogs() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    DefaultFlowBus.flow<ViewModelDemoLog>().collect { event ->
                        if (!event.message.contains(currentScopeName())) {
                            return@collect
                        }
                        binding.tvViewModelLog.text =
                            withTimestamp("${getString(R.string.non_ui_case_view_model_result)} ${event.message}")
                    }
                }
                launch {
                    DefaultFlowBus.flow<RepositoryDemoLog>().collect { event ->
                        if (!event.message.contains(currentScopeName())) {
                            return@collect
                        }
                        binding.tvRepositoryLog.text =
                            withTimestamp("${getString(R.string.non_ui_case_repository_result)} ${event.message}")
                    }
                }
                launch {
                    DefaultFlowBus.flow<WorkerDemoLog>().collect { event ->
                        if (!event.message.contains(currentScopeName())) {
                            return@collect
                        }
                        binding.tvWorkerLog.text =
                            withTimestamp("${getString(R.string.non_ui_case_worker_result)} ${event.message}")
                        binding.tvUiResult.text =
                            withTimestamp(getString(R.string.non_ui_case_ui_result_done))
                    }
                }
            }
        }
    }

    private fun withTimestamp(message: String): String {
        return "${getCurrentTime()} $message"
    }

    private fun triggerSource(): String {
        return getString(R.string.non_ui_case_trigger_source)
    }

    private fun currentScopeName(): String {
        return viewModel.currentScopeName()
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Calendar.getInstance().time)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
