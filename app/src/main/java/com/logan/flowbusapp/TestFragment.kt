package com.logan.flowbusapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.logan.flowbus.collectEvent
import com.logan.flowbus.eventFlowFrom
import com.logan.flowbus.postEventTo
import com.logan.flowbus.postScopedEvent
import com.logan.flowbus.scopedEventFlow
import com.logan.flowbusapp.databinding.FragmentTestBinding
import com.logan.flowbusapp.event.ActivityEvent
import com.logan.flowbusapp.event.FragmentEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TestFragment : Fragment() {

    companion object {
        private const val EVENT_FRAGMENT_PANEL_REFRESH = "fragment-panel-refresh"
    }

    private var _binding: FragmentTestBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        renderInitialState()
        setListeners()
        subscribeScopeEvents()
    }

    private fun renderInitialState() {
        binding.tvFragmentPanelState.text = getString(R.string.scope_fragment_state_waiting)
        binding.tvFragmentLog.text = getString(R.string.scope_fragment_log_waiting)
        binding.tvFragmentScopeHint.text = getString(R.string.scope_fragment_hint_waiting)
    }

    private fun setListeners() {
        binding.btnRefreshCurrentPanel.setOnClickListener {
            postScopedEvent(FragmentEvent(EVENT_FRAGMENT_PANEL_REFRESH))
        }
        binding.btnSendLocalMessageToHost.setOnClickListener {
            binding.tvFragmentPanelState.text = withTimestamp(getString(R.string.scope_fragment_state_message_sent))
            binding.tvFragmentLog.text = withTimestamp(getString(R.string.scope_fragment_log_message_sent))
            binding.tvFragmentScopeHint.text = withTimestamp(getString(R.string.scope_fragment_hint_fragment_to_host))
            postEventTo(owner = requireActivity(), event = ActivityEvent(TestFragmentActivity.EVENT_FRAGMENT_TO_HOST))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun subscribeScopeEvents() {
        viewLifecycleOwner.collectEvent(eventFlowFrom<ActivityEvent>(owner = requireActivity())) { event ->
            when (event.message) {
                TestFragmentActivity.EVENT_ACTIVITY_TOOLBAR_REFRESH -> {
                    binding.tvFragmentScopeHint.text =
                        withTimestamp(getString(R.string.scope_fragment_hint_activity_event))
                }

                TestFragmentActivity.EVENT_FRAGMENT_TO_HOST -> {
                    binding.tvFragmentScopeHint.text =
                        withTimestamp(getString(R.string.scope_fragment_hint_fragment_to_host))
                }
            }
        }

        viewLifecycleOwner.collectEvent(scopedEventFlow<FragmentEvent>()) { event ->
            if (event.message != EVENT_FRAGMENT_PANEL_REFRESH) {
                return@collectEvent
            }
            binding.tvFragmentPanelState.text = withTimestamp(getString(R.string.scope_fragment_state_refreshed))
            binding.tvFragmentLog.text = withTimestamp(getString(R.string.scope_fragment_log_refreshed))
            binding.tvFragmentScopeHint.text = withTimestamp(getString(R.string.scope_fragment_hint_fragment_event))
        }
    }

    private fun withTimestamp(message: String): String {
        return "${getCurrentTime()} $message"
    }

    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Calendar.getInstance().time)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
