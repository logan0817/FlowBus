package com.logan.flowbusapp

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import com.logan.flowbus.postEvent
import com.logan.flowbus.postEventTo
import com.logan.flowbus.subscribeEvent
import com.logan.flowbus.subscribeEventFrom
import com.logan.flowbusapp.databinding.ActivityMainBinding
import com.logan.flowbusapp.event.ActivityEvent
import com.logan.flowbusapp.event.GlobalEvent
import com.logan.flowbusapp.login.LoginActivity
import kotlinx.coroutines.Dispatchers
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        val TAG = "MainActivityTAG"
    }

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        setupInsets()
        setListeners()
        subscribeGlobalEvents()
        subscribeScopeEvents()

    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.toolbar) { view, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = insets.top)
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = MainActivity::class.java.simpleName
    }

    @SuppressLint("SetTextI18n")
    private fun setListeners() {
        binding.btnSendGlobalEvent.setOnClickListener {
            postEvent(GlobalEvent("Refresh app"))
            postEvent("Raw String event (demo only)")
        }
        binding.btnSendActivityEvent.setOnClickListener {
            postEventTo(owner = this, event = ActivityEvent("Refresh activity widgets"))
        }
        binding.btnJumpNextPage.setOnClickListener {
            startActivity(Intent(this, TestActivity::class.java))
        }
        binding.btnJumpNextFragmentPage.setOnClickListener {
            startActivity(Intent(this, TestFragmentActivity::class.java))
        }
        binding.btnJumpLoginActivity.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun subscribeGlobalEvents() {
        // Global bus examples.
        subscribeEvent<GlobalEvent> {
            Log.d(TAG, "onReceived0-1:${it.message}")
            binding.tvGlobalEvent01.text = "${getCurrentTime()}-onReceived0-1:${it.message} "
        }
        lifecycleScope.subscribeEvent<GlobalEvent> {
            Log.d(TAG, "onReceived0-3:${it}")
            binding.tvGlobalEvent03.text = "${getCurrentTime()}-onReceived0-3:${it.message}"
        }
        subscribeEvent<String> {
            Log.d(TAG, "onReceived0-2:${it}")
            binding.tvGlobalEvent02.text = "${getCurrentTime()}-onReceived0-2:${it}"
        }
    }

    @SuppressLint("SetTextI18n")
    private fun subscribeScopeEvents() {
        subscribeEvent<ActivityEvent>(owner = this) {
            Log.d(TAG, "onReceived1:${it.message}")
            binding.tvActivityEvent1.text = "${getCurrentTime()}-onReceived1:${it.message}"
        }
        // Activity-scoped bus + dispatcher + lifecycle state.
        subscribeEvent<ActivityEvent>(owner = this, dispatcher = Dispatchers.Main, minLifecycleState = Lifecycle.State.RESUMED) {
            Log.d(TAG, "onReceived2:${it.message} ${Thread.currentThread().name}")
            binding.tvActivityEvent2.text = "${getCurrentTime()}-onReceived2:${it.message} ${Thread.currentThread().name}  RESUMED time"
        }
        // Subscribe using a lifecycle-managed CoroutineScope instead of creating an unmanaged scope.
        lifecycleScope.subscribeEventFrom<ActivityEvent>(owner = this) {
            Log.d(TAG, "onReceived3:${it.message} ${Thread.currentThread().name}")
            binding.tvActivityEvent3.text = "${getCurrentTime()}-onReceived3:${it.message} ${Thread.currentThread().name}"
        }
    }

    fun getCurrentTime() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Calendar.getInstance().time)

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
