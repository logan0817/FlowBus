package com.logan.flowbusapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.logan.flowbus.collectEvent
import com.logan.flowbus.eventFlow
import com.logan.flowbus.eventFlowFrom
import com.logan.flowbus.postEvent
import com.logan.flowbus.postEventTo
import com.logan.flowbus.postStickyEvent
import com.logan.flowbus.postStickyEventTo
import com.logan.flowbus.removeStickyEvent
import com.logan.flowbus.stickyEventFlow
import com.logan.flowbus.stickyEventFlowFrom
import com.logan.flowbusapp.databinding.FragmentTestBinding
import com.logan.flowbusapp.event.ActivityEvent
import com.logan.flowbusapp.event.FragmentEvent
import com.logan.flowbusapp.event.GlobalEvent
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TestFragment : Fragment() {

    companion object {
        val TAG = "TestFragmentTAG"
    }

    private var _binding: FragmentTestBinding? = null

    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTestBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setListeners()
        subscribeGlobalEvents()
        subscribeScopeEvents()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
    }

    private fun setListeners() {
        binding.btnSendGlobalEvent.setOnClickListener {
            postEvent(GlobalEvent("Refresh app"))
            postStickyEvent(GlobalEvent("Latest global state"))
        }
        binding.btnSendActivityEvent.setOnClickListener {
            postEventTo(owner = requireActivity(), event = ActivityEvent("Refresh activity widgets"))
            postStickyEventTo(owner = requireActivity(), event = ActivityEvent("Latest activity widgets"))
        }
        binding.btnSendFragmentEvent.setOnClickListener {
            postEventTo(owner = this@TestFragment, event = FragmentEvent("Refresh fragment widgets"))
            postStickyEventTo(owner = this@TestFragment, event = FragmentEvent("Latest fragment state"))
        }
    }

    fun getCurrentTime() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Calendar.getInstance().time)

    @SuppressLint("SetTextI18n")
    private fun subscribeGlobalEvents() {
        viewLifecycleOwner.collectEvent(eventFlow<GlobalEvent>()) {
            Log.d(TAG, "TestFragment received GlobalEvent 1:${it.message}")
            binding.tvGlobalEvent01.text = "${getCurrentTime()}-onReceived0-1:${it.message} "
        }
        viewLifecycleOwner.collectEvent(
            flow = eventFlow<GlobalEvent>(),
            dispatcher = Dispatchers.Main,
            minLifecycleState = Lifecycle.State.RESUMED
        ) {
            Log.d(TAG, "TestFragment received GlobalEvent 2 RESUMED:${it.message}")
            binding.tvGlobalEvent02.text = "${getCurrentTime()}-onReceived0-2 RESUMED:${it.message} "
        }
        viewLifecycleOwner.collectEvent(stickyEventFlow<GlobalEvent>()) {
            Log.d(TAG, "TestFragment received GlobalEvent 3 isSticky:${it.message}")
            binding.tvGlobalEvent03.text = "${getCurrentTime()}-onReceived0-3 isSticky:${it.message} "
        }
    }

    @SuppressLint("SetTextI18n")
    private fun subscribeScopeEvents() {
        viewLifecycleOwner.collectEvent(eventFlowFrom<ActivityEvent>(owner = requireActivity())) {
            Log.d(TAG, "received ActivityEvent1:${it.message}")
            binding.tvActivityEvent1.text = "${getCurrentTime()}-onReceived1:${it.message} "
        }
        viewLifecycleOwner.collectEvent(
            flow = eventFlowFrom<ActivityEvent>(owner = requireActivity()),
            dispatcher = Dispatchers.Main,
            minLifecycleState = Lifecycle.State.RESUMED
        ) {
            Log.d(TAG, "received ActivityEvent2 RESUMED:${it.message}")
            binding.tvActivityEvent2.text = "${getCurrentTime()}-onReceived2 RESUMED:${it.message} "
        }
        viewLifecycleOwner.collectEvent(stickyEventFlowFrom<ActivityEvent>(owner = requireActivity())) {
            Log.d(TAG, "received ActivityEvent3 isSticky:${it.message}")
            binding.tvActivityEvent3.text = "${getCurrentTime()}-onReceived3 isSticky:${it.message} "
        }

        viewLifecycleOwner.collectEvent(eventFlowFrom<FragmentEvent>(owner = this@TestFragment)) {
            Log.d(TAG, "received FragmentEvent1:${it.message}")
            binding.tvFragmentEvent1.text = "${getCurrentTime()}-onReceived1:${it.message} "
        }
        viewLifecycleOwner.collectEvent(
            flow = eventFlowFrom<FragmentEvent>(owner = this@TestFragment),
            dispatcher = Dispatchers.Main,
            minLifecycleState = Lifecycle.State.RESUMED
        ) {
            Log.d(TAG, "received FragmentEvent2 RESUMED:${it.message}")
            binding.tvFragmentEvent2.text = "${getCurrentTime()}-onReceived2 RESUMED:${it.message} "
        }
        viewLifecycleOwner.collectEvent(stickyEventFlowFrom<FragmentEvent>(owner = this@TestFragment)) {
            Log.d(TAG, "received FragmentEvent3 isSticky:${it.message}")
            binding.tvFragmentEvent3.text = "${getCurrentTime()}-onReceived3 isSticky:${it.message} "
        }
    }
}
