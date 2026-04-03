package com.logan.flowbusapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.logan.flowbus.collectEvent
import com.logan.flowbus.eventFlow
import com.logan.flowbus.eventFlowFrom
import com.logan.flowbus.onEvent
import com.logan.flowbus.postEvent
import com.logan.flowbus.postEventTo
import com.logan.flowbus.core.DefaultFlowBus
import com.logan.flowbusapp.databinding.ActivityMainBinding
import com.logan.flowbusapp.event.ActivityEvent
import com.logan.flowbusapp.event.GlobalEvent
import com.logan.flowbusapp.login.LoginActivity
import com.logan.flowbusapp.nonui.MainDemoViewModel
import com.logan.flowbusapp.nonui.RepositoryDemoLog
import com.logan.flowbusapp.nonui.ViewModelDemoLog
import com.logan.flowbusapp.nonui.WorkerDemoLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivityTAG"
    }

    private val demoViewModel by viewModels<MainDemoViewModel>()

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        setupInsets()
        setListeners()
        subscribeGlobalEvents()
        subscribeScopeEvents()
        subscribeNonUiEvents()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top)
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = MainActivity::class.java.simpleName
    }

    @SuppressLint("SetTextI18n")
    private fun setListeners() {
        binding.btnSendGlobalEvent.setOnClickListener {
            // 首页优先展示推荐路径：类型化事件 + onEvent/collectEvent。
            postEvent(GlobalEvent("Refresh app"))
        }
        binding.btnSendActivityEvent.setOnClickListener {
            postEventTo(owner = this, event = ActivityEvent("Refresh activity widgets"))
        }
        binding.btnRunNonUiDemo.setOnClickListener {
            demoViewModel.runNonUiDemo()
        }
        binding.btnJumpNextPage.setOnClickListener {
            startActivity(Intent(this, TestActivity::class.java))
        }
        binding.btnJumpNextFragmentPage.setOnClickListener {
            startActivity(Intent(this, TestFragmentActivity::class.java))
        }
        binding.btnJumpLoginActivity.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun subscribeGlobalEvents() {
        onEvent<GlobalEvent> {
            Log.d(TAG, "onEvent global-1:${it.message}")
            binding.tvGlobalEvent01.text = "${getCurrentTime()}-onEvent:${it.message}"
        }
        collectEvent(eventFlow<GlobalEvent>()) {
            Log.d(TAG, "collectEvent global-2:${it.message}")
            binding.tvGlobalEvent02.text = "${getCurrentTime()}-collectEvent:${it.message}"
        }
        collectEvent(eventFlow<GlobalEvent>(), minLifecycleState = Lifecycle.State.RESUMED) {
            Log.d(TAG, "collectEvent global-3 resumed:${it.message}")
            binding.tvGlobalEvent03.text = "${getCurrentTime()}-RESUMED:${it.message}"
        }
    }

    @SuppressLint("SetTextI18n")
    private fun subscribeScopeEvents() {
        collectEvent(eventFlowFrom<ActivityEvent>(owner = this)) {
            Log.d(TAG, "onReceived1:${it.message}")
            binding.tvActivityEvent1.text = "${getCurrentTime()}-onReceived1:${it.message}"
        }
        collectEvent(
            flow = eventFlowFrom<ActivityEvent>(owner = this),
            dispatcher = Dispatchers.Main,
            minLifecycleState = Lifecycle.State.RESUMED
        ) {
            Log.d(TAG, "onReceived2:${it.message} ${Thread.currentThread().name}")
            binding.tvActivityEvent2.text = "${getCurrentTime()}-onReceived2:${it.message} ${Thread.currentThread().name}  RESUMED time"
        }
        lifecycleScope.launch {
            eventFlowFrom<ActivityEvent>(owner = this@MainActivity).collect {
                Log.d(TAG, "onReceived3:${it.message} ${Thread.currentThread().name}")
                binding.tvActivityEvent3.text = "${getCurrentTime()}-onReceived3:${it.message} ${Thread.currentThread().name}"
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun subscribeNonUiEvents() {
        lifecycleScope.launch {
            DefaultFlowBus.flow<ViewModelDemoLog>().collect {
                binding.tvViewModelEvent.text = "${getCurrentTime()}-${it.message}"
            }
        }
        lifecycleScope.launch {
            DefaultFlowBus.flow<RepositoryDemoLog>().collect {
                binding.tvRepositoryEvent.text = "${getCurrentTime()}-${it.message}"
            }
        }
        lifecycleScope.launch {
            DefaultFlowBus.flow<WorkerDemoLog>().collect {
                binding.tvWorkerEvent.text = "${getCurrentTime()}-${it.message}"
            }
        }
    }

    fun getCurrentTime() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Calendar.getInstance().time)

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
