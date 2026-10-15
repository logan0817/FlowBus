package com.logan.flowbusapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.logan.flowbus.collectEvent
import com.logan.flowbus.configureEventChannel
import com.logan.flowbus.eventChannel
import com.logan.flowbus.emitTo
import com.logan.flowbus.flowFrom
import com.logan.flowbus.postTo
import com.logan.flowbus.core.FlowBusChannelConfig
import com.logan.flowbusapp.databinding.ActivityChannelCaseBinding
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ChannelCaseActivity : AppCompatActivity() {

    private val toastChannel = eventChannel<String>("ui.toast")
    private val navigationChannel = eventChannel<String>("ui.navigation")
    private val snackbarChannel = eventChannel<String>("ui.snackbar")

    private var _binding: ActivityChannelCaseBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityChannelCaseBinding.inflate(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        setupInsets()
        renderInitialState()
        configureChannels()
        setListeners()
        subscribeChannels()
    }

    private fun configureChannels() {
        // 配置绑定到 Activity 的 ViewModelStore；配置重建重复绑定同值，不延长到进程全局。
        configureEventChannel(toastChannel, FlowBusChannelConfig(normalBufferCapacity = 1), owner = this)
        configureEventChannel(
            navigationChannel,
            FlowBusChannelConfig(normalBufferCapacity = 16, normalOverflowPolicy = BufferOverflow.SUSPEND),
            owner = this
        )
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.updatePadding(top = insets.top)
            binding.scrollChannelContent.updatePadding(bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.channel_case_title)
    }

    private fun renderInitialState() {
        binding.tvToastChannelLog.text = getString(R.string.channel_case_toast_waiting)
        binding.tvNavigationChannelLog.text = getString(R.string.channel_case_navigation_waiting)
        binding.tvSnackbarChannelLog.text = getString(R.string.channel_case_snackbar_waiting)
    }

    private fun setListeners() {
        binding.btnSendToastCommand.setOnClickListener {
            toastChannel.postTo(this, getString(R.string.channel_case_toast_payload))
        }
        binding.btnSendNavigationCommand.setOnClickListener {
            lifecycleScope.launch {
                navigationChannel.emitTo(this@ChannelCaseActivity, getString(R.string.channel_case_navigation_payload))
            }
        }
        binding.btnSendSnackbarCommand.setOnClickListener {
            snackbarChannel.postTo(this, getString(R.string.channel_case_snackbar_payload))
        }
    }

    private fun subscribeChannels() {
        collectEvent(toastChannel.flowFrom(this)) {
            binding.tvToastChannelLog.text = withTimestamp(getString(R.string.channel_case_toast_result))
        }
        collectEvent(navigationChannel.flowFrom(this)) {
            binding.tvNavigationChannelLog.text = withTimestamp(getString(R.string.channel_case_navigation_result))
        }
        collectEvent(snackbarChannel.flowFrom(this)) {
            binding.tvSnackbarChannelLog.text = withTimestamp(getString(R.string.channel_case_snackbar_result))
        }
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
