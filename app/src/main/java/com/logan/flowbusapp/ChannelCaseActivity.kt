package com.logan.flowbusapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.logan.flowbus.collectEvent
import com.logan.flowbus.eventChannel
import com.logan.flowbus.flow
import com.logan.flowbus.post
import com.logan.flowbusapp.databinding.ActivityChannelCaseBinding
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
        setListeners()
        subscribeChannels()
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
            toastChannel.post(getString(R.string.channel_case_toast_payload))
        }
        binding.btnSendNavigationCommand.setOnClickListener {
            navigationChannel.post(getString(R.string.channel_case_navigation_payload))
        }
        binding.btnSendSnackbarCommand.setOnClickListener {
            snackbarChannel.post(getString(R.string.channel_case_snackbar_payload))
        }
    }

    private fun subscribeChannels() {
        collectEvent(toastChannel.flow()) {
            binding.tvToastChannelLog.text = withTimestamp(getString(R.string.channel_case_toast_result))
        }
        collectEvent(navigationChannel.flow()) {
            binding.tvNavigationChannelLog.text = withTimestamp(getString(R.string.channel_case_navigation_result))
        }
        collectEvent(snackbarChannel.flow()) {
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
