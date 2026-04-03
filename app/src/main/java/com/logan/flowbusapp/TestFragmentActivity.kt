package com.logan.flowbusapp

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.logan.flowbus.eventChannel
import com.logan.flowbus.onEvent
import com.logan.flowbus.tryPostEventTo
import com.logan.flowbus.tryPostTo
import com.logan.flowbusapp.databinding.ActivityTestFragmentBinding
import com.logan.flowbusapp.event.ActivityEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

val activityToastChannel = eventChannel<String>("demo.activity.toast")

class TestFragmentActivity : AppCompatActivity() {

    private var _binding: ActivityTestFragmentBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityTestFragmentBinding.inflate(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        setupInsets()
        initView()
        setListeners()
        subscribeScopeEvents()
    }

    private fun initView() {
        if (supportFragmentManager.findFragmentById(R.id.content) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.content, TestFragment())
                .commit()
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top)
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = TestFragmentActivity::class.java.simpleName
    }

    @SuppressLint("SetTextI18n")
    private fun subscribeScopeEvents() {
        onEvent<ActivityEvent> {
            binding.tvHostLog.text = "${getCurrentTime()}-host activity event: ${it.message}"
        }
        onEvent(from = this, channel = activityToastChannel) { message ->
            binding.tvHostLog.text = "${getCurrentTime()}-host channel: $message"
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setListeners() {
        binding.btnSendActivityEvent.setOnClickListener {
            tryPostEventTo(owner = this, event = ActivityEvent(getString(R.string.activity_scope_message)))
        }
        binding.btnSendChannelEvent.setOnClickListener {
            activityToastChannel.tryPostTo(this, getString(R.string.activity_channel_message))
        }
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Calendar.getInstance().time)
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
