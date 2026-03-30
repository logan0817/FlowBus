package com.logan.flowbusapp

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.logan.flowbus.postStickyEvent
import com.logan.flowbus.removeStickyEvent
import com.logan.flowbus.subscribeEvent
import com.logan.flowbusapp.databinding.ActivityTestBinding
import com.logan.flowbusapp.event.GlobalEvent
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
        supportActionBar?.title  = TestActivity::class.java.simpleName
    }


    @SuppressLint("SetTextI18n")
    private fun subscribeStickyEvents() {
        subscribeEvent<GlobalEvent>(scope = this, isSticky = true) {
            Log.d(TAG, "onReceived:${it.name}")
            val string = "${binding.tvEventText.text} \r\n"
            binding.tvEventText.text = "${string}${getCurrentTime()}-onReceived:${it.name}"
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setListeners() {
        binding.btnSendCustomEvent.setOnClickListener {
            postStickyEvent(scope = this, event = GlobalEvent("Test CustomEvent"))
        }
        binding.btnSendDelayCustomEvent.setOnClickListener {
            postStickyEvent(scope = this, event = GlobalEvent("Test DelayCustomEvent"), timeMillis = 1000)
        }
        binding.btnSendManyEvent.setOnClickListener {
            (1..200).forEach { index ->
                postStickyEvent(scope = this, event = GlobalEvent(name = "Test ManyEvent-$index"))
            }
        }
    }

    fun getCurrentTime() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Calendar.getInstance().time)

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
        removeStickyEvent<GlobalEvent>(scope = this)
    }
}
