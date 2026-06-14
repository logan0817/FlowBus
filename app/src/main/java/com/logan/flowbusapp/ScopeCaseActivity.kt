package com.logan.flowbusapp

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.logan.flowbus.collectEvent
import com.logan.flowbus.postScopedEvent
import com.logan.flowbus.scopedEventFlow
import com.logan.flowbusapp.databinding.ActivityScopeCaseBinding
import com.logan.flowbusapp.event.ActivityEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ScopeCaseActivity : AppCompatActivity() {

    companion object {
        const val EVENT_ACTIVITY_TOOLBAR_REFRESH = "activity-toolbar-refresh"
        const val EVENT_FRAGMENT_TO_HOST = "fragment-to-host"
    }

    private var _binding: ActivityScopeCaseBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityScopeCaseBinding.inflate(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        setupInsets()
        initView()
        renderInitialState()
        setListeners()
        subscribeScopeEvents()
    }

    private fun initView() {
        if (supportFragmentManager.findFragmentById(R.id.fragmentContainer) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, ScopeCaseFragment())
                .commitNow()
        }
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.updatePadding(top = insets.top)
            binding.scrollScopeContent.updatePadding(bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.scope_case_title)
    }

    private fun renderInitialState() {
        binding.tvHostToolbarState.text = getString(R.string.scope_host_state_waiting)
        binding.tvHostLog.text = getString(R.string.scope_host_log_waiting)
        binding.tvHostScopeHint.text = getString(R.string.scope_host_hint_waiting)
    }

    @SuppressLint("SetTextI18n")
    private fun subscribeScopeEvents() {
        collectEvent(scopedEventFlow<ActivityEvent>()) { event ->
            when (event.message) {
                EVENT_ACTIVITY_TOOLBAR_REFRESH -> renderHostActivityRefresh()
                EVENT_FRAGMENT_TO_HOST -> renderFragmentMessageArrived()
            }
        }
    }

    private fun setListeners() {
        binding.btnNotifyActivityToolbar.setOnClickListener {
            postScopedEvent(ActivityEvent(EVENT_ACTIVITY_TOOLBAR_REFRESH))
        }
    }

    private fun renderHostActivityRefresh() {
        binding.tvHostToolbarState.text = withTimestamp(getString(R.string.scope_host_state_toolbar_refreshed))
        binding.tvHostLog.text = withTimestamp(getString(R.string.scope_host_log_activity_refreshed))
        binding.tvHostScopeHint.text = withTimestamp(getString(R.string.scope_host_hint_activity_event))
    }

    private fun renderFragmentMessageArrived() {
        binding.tvHostToolbarState.text = withTimestamp(getString(R.string.scope_host_state_fragment_message))
        binding.tvHostLog.text = withTimestamp(getString(R.string.scope_host_log_fragment_message))
        binding.tvHostScopeHint.text = withTimestamp(getString(R.string.scope_host_hint_fragment_message))
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
