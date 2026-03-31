package com.logan.flowbusapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.logan.flowbus.postStickyEventTo
import com.logan.flowbus.removeStickyEvent
import com.logan.flowbus.subscribeEvent
import com.logan.flowbusapp.databinding.ActivityTestBinding
import com.logan.flowbusapp.event.ActivityEvent
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TestActivity : AppCompatActivity() {
    companion object {
        val TAG = "TestActivityTAG"
    }

    private var _binding: ActivityTestBinding? = null
    private val binding get() = _binding!!


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityTestBinding.inflate(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        setupInsets()
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
        supportActionBar?.title = TestActivity::class.java.simpleName
    }


    @SuppressLint("SetTextI18n")
    private fun subscribeStickyEvents() {
        subscribeEvent<ActivityEvent>(owner = this, isSticky = true) {
            Log.d(TAG, "onReceived:${it.message}")
            val string = "${binding.tvEventText.text} \r\n"
            binding.tvEventText.text = "${string}${getCurrentTime()}-onReceived:${it.message}"
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setListeners() {
        binding.btnSendCustomEvent.setOnClickListener {
            postStickyEventTo(owner = this, event = ActivityEvent("Latest activity state"))
        }
        binding.btnSendDelayCustomEvent.setOnClickListener {
            postStickyEventTo(owner = this, event = ActivityEvent("Latest activity state after delay"), delayMillis = 1000)
        }
        binding.btnSendManyEvent.setOnClickListener {
            (1..200).forEach { index ->
                postStickyEventTo(owner = this, event = ActivityEvent(message = "Latest activity state #$index"))
            }
        }
    }

    fun getCurrentTime() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Calendar.getInstance().time)

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        removeStickyEvent<ActivityEvent>(owner = this)
    }
}
