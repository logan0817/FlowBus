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
import com.logan.flowbus.postEvent
import com.logan.flowbus.subscribeEvent
import com.logan.flowbusapp.databinding.ActivityMainBinding
import com.logan.flowbusapp.event.ActivityEvent
import com.logan.flowbusapp.event.GlobalEvent
import com.logan.flowbusapp.login.LoginActivity
import kotlinx.coroutines.CoroutineScope
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
            postEvent(GlobalEvent("Main GlobalEvent"))
            postEvent("MainGlobalEvent")
        }
        binding.btnSendActivityEvent.setOnClickListener {
            postEvent(scope = this, ActivityEvent("Main ActivityEvent"))
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
        //监听
        subscribeEvent<GlobalEvent> {
            Log.d(TAG, "onReceived0-1:${it.name}")
            binding.tvGlobalEvent01.text = "${getCurrentTime()}-onReceived0-1:${it.name} "
        }
        CoroutineScope(Dispatchers.Main).subscribeEvent<GlobalEvent> {
            Log.d(TAG, "onReceived0-3:${it}")
            binding.tvGlobalEvent03.text = "${getCurrentTime()}-onReceived0-3:${it.name}"
        }
        subscribeEvent<String> {
            Log.d(TAG, "onReceived0-2:${it}")
            binding.tvGlobalEvent02.text = "${getCurrentTime()}-onReceived0-2:${it}"
        }
    }

    @SuppressLint("SetTextI18n")
    private fun subscribeScopeEvents() {
        subscribeEvent<ActivityEvent>(scope = this) {
            Log.d(TAG, "onReceived1:${it.name}")
            binding.tvActivityEvent1.text = "${getCurrentTime()}-onReceived1:${it.name}"
        }
        //Control Thread + Specify lifecycleState
        subscribeEvent<ActivityEvent>(scope = this, dispatcher = Dispatchers.Main, minLifecycleState = Lifecycle.State.RESUMED) {
            Log.d(TAG, "onReceived2:${it.name} ${Thread.currentThread().name}")
            binding.tvActivityEvent2.text = "${getCurrentTime()}-onReceived2:${it.name} ${Thread.currentThread().name}  RESUMED time"
        }
        //Subscribe to events in the ViewModelStoreOwner scope within a separate **CoroutineScope**.
        CoroutineScope(Dispatchers.Main).subscribeEvent<ActivityEvent>(scope = this) {
            Log.d(TAG, "onReceived3:${it.name} ${Thread.currentThread().name}")
            binding.tvActivityEvent3.text = "${getCurrentTime()}-onReceived3:${it.name} ${Thread.currentThread().name}"
        }
    }

    fun getCurrentTime() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Calendar.getInstance().time)
}
