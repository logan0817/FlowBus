package com.logan.flowbusapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import com.logan.flowbus.postEvent
import com.logan.flowbus.postStickyEvent
import com.logan.flowbus.subscribeEvent
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

    private fun setListeners() {
        binding.btnSendGlobalEvent.setOnClickListener {
            postEvent(GlobalEvent("Test GlobalEvent"))
            postStickyEvent(GlobalEvent("Test GlobalEvent"))
        }
        binding.btnSendActivityEvent.setOnClickListener {
            postEvent(scope = requireActivity(), event = ActivityEvent("Test ActivityEvent"))
        }
        binding.btnSendFragmentEvent.setOnClickListener {
            postEvent(scope = this@TestFragment, event = FragmentEvent("Test FragmentEvent"))
        }
    }

    fun getCurrentTime() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Calendar.getInstance().time)

    @SuppressLint("SetTextI18n")
    private fun subscribeGlobalEvents() {
        viewLifecycleOwner.subscribeEvent<GlobalEvent> {
            Log.d(TAG, "TestFragment received GlobalEvent 1:${it.name}")
            binding.tvGlobalEvent01.text = "${getCurrentTime()}-onReceived0-1:${it.name} "
        }
        viewLifecycleOwner.subscribeEvent<GlobalEvent>(isSticky = true) {
            Log.d(TAG, "TestFragment received GlobalEvent 1:${it.name}")
            binding.tvGlobalEvent02.text = "${getCurrentTime()}-onReceived0-2:${it.name} "
        }
        viewLifecycleOwner.subscribeEvent<GlobalEvent>(dispatcher = Dispatchers.Main) {
            Log.d(TAG, "TestFragment received GlobalEvent 1:${it.name}")
            binding.tvGlobalEvent03.text = "${getCurrentTime()}-onReceived0-3:${it.name} "
        }

    }

    @SuppressLint("SetTextI18n")
    private fun subscribeScopeEvents() {
        // Activity-scoped event bus, but collection is tied to the Fragment view lifecycle.
        requireActivity().subscribeEvent<ActivityEvent>(scope = viewLifecycleOwner) {
            Log.d(TAG, "received GlobalEvent1:${it.name}")
            binding.tvActivityEvent1.text = "${getCurrentTime()}-onReceived1:${it.name} "
        }
        requireActivity().subscribeEvent<ActivityEvent>(
            scope = viewLifecycleOwner,
            minLifecycleState = Lifecycle.State.RESUMED
        ) {
            Log.d(TAG, "received GlobalEvent2:${it.name}")
            binding.tvActivityEvent2.text = "${getCurrentTime()}-onReceived2:${it.name} "
        }
        requireActivity().subscribeEvent<ActivityEvent>(
            scope = viewLifecycleOwner,
            dispatcher = Dispatchers.Main,
            minLifecycleState = Lifecycle.State.STARTED
        ) {
            Log.d(TAG, "received ActivityEvent3:${it.name}")
            binding.tvActivityEvent3.text = "${getCurrentTime()}-onReceived3:${it.name} "
        }

        // Fragment-scoped event bus, also collected with the view lifecycle to avoid touching a dead binding.
        this@TestFragment.subscribeEvent<FragmentEvent>(scope = viewLifecycleOwner) {
            Log.d(TAG, "received FragmentEvent1:${it.name}")
            binding.tvFragmentEvent1.text = "${getCurrentTime()}-onReceived1:${it.name} "
        }
        this@TestFragment.subscribeEvent<FragmentEvent>(
            scope = viewLifecycleOwner,
            minLifecycleState = Lifecycle.State.RESUMED
        ) {
            Log.d(TAG, "received FragmentEvent2:${it.name}")
            binding.tvFragmentEvent2.text = "${getCurrentTime()}-onReceived2:${it.name} "
        }
        this@TestFragment.subscribeEvent<FragmentEvent>(
            scope = viewLifecycleOwner,
            dispatcher = Dispatchers.Main,
            minLifecycleState = Lifecycle.State.STARTED
        ) {
            Log.d(TAG, "received FragmentEvent3:${it.name}")
            binding.tvFragmentEvent3.text = "${getCurrentTime()}-onReceived3:${it.name} "
        }
    }
}
