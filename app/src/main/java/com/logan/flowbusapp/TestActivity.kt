package com.logan.flowbusapp

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.logan.flowbus.collectEvent
import com.logan.flowbus.postScopedStickyEvent
import com.logan.flowbus.removeStickyEvent
import com.logan.flowbus.scopedStickyEventFlow
import com.logan.flowbusapp.databinding.ActivityTestBinding
import com.logan.flowbusapp.event.ActivityEvent

class TestActivity : AppCompatActivity() {

    private enum class StickyEventSource {
        LIVE,
        REPLAYED
    }

    private var _binding: ActivityTestBinding? = null
    private val binding get() = _binding!!

    private var awaitingLiveSticky = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityTestBinding.inflate(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        setupInsets()
        renderInitialState()
        setListeners()
        subscribeStickyEvents()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top)
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.sticky_case_title)
    }

    private fun renderInitialState() {
        binding.tvStickyLatestState.text = getString(R.string.sticky_case_state_waiting)
        binding.tvStickyReplayHint.text = getString(R.string.sticky_case_hint_waiting)
        binding.tvStickyCaseLog.text = getString(R.string.sticky_case_log_waiting)
    }

    private fun subscribeStickyEvents() {
        collectEvent(scopedStickyEventFlow<ActivityEvent>()) { event ->
            val eventSource = if (awaitingLiveSticky) {
                StickyEventSource.LIVE
            } else {
                StickyEventSource.REPLAYED
            }
            awaitingLiveSticky = false
            renderStickyState(message = event.message, eventSource = eventSource)
        }
    }

    private fun setListeners() {
        binding.btnRememberLatestToolbarState.setOnClickListener {
            awaitingLiveSticky = true
            postScopedStickyEvent(ActivityEvent(getString(R.string.sticky_case_toolbar_ready_message)))
        }
        binding.btnRecreateStickyCase.setOnClickListener {
            recreate()
        }
        binding.btnClearLatestToolbarState.setOnClickListener {
            awaitingLiveSticky = false
            removeStickyEvent<ActivityEvent>(owner = this)
            renderInitialState()
        }
    }

    private fun renderStickyState(message: String, eventSource: StickyEventSource) {
        binding.tvStickyLatestState.text = describeToolbarState(message)
        when (eventSource) {
            StickyEventSource.LIVE -> {
                binding.tvStickyReplayHint.text = getString(R.string.sticky_case_hint_live)
                binding.tvStickyCaseLog.text = getString(R.string.sticky_case_log_live)
            }

            StickyEventSource.REPLAYED -> {
                binding.tvStickyReplayHint.text = getString(R.string.sticky_case_hint_replayed)
                binding.tvStickyCaseLog.text = getString(R.string.sticky_case_log_replayed)
            }
        }
    }

    private fun describeToolbarState(message: String): String {
        return if (message == getString(R.string.sticky_case_toolbar_ready_message)) {
            getString(R.string.sticky_case_state_toolbar_ready)
        } else {
            getString(R.string.sticky_case_state_other, message)
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            removeStickyEvent<ActivityEvent>(owner = this)
        }
        _binding = null
        super.onDestroy()
    }
}
