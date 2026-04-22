package com.logan.flowbusapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.logan.flowbusapp.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    private val binding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityMainBinding.inflate(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        setupInsets()
        setListeners()
    }

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, windowInsets ->
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.toolbar.updatePadding(top = insets.top)
            binding.scrollHomeContent.updatePadding(bottom = insets.bottom)
            WindowInsetsCompat.CONSUMED
        }
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = getString(R.string.home_screen_title)
    }

    private fun setListeners() {
        binding.btnOpenGlobalCase.setOnClickListener {
            startActivity(Intent(this, GlobalCaseActivity::class.java))
        }
        binding.btnOpenScopeCase.setOnClickListener {
            startActivity(Intent(this, TestFragmentActivity::class.java))
        }
        binding.btnOpenStickyCase.setOnClickListener {
            startActivity(Intent(this, TestActivity::class.java))
        }
        binding.btnOpenChannelCase.setOnClickListener {
            startActivity(Intent(this, ChannelCaseActivity::class.java))
        }
        binding.btnOpenNonUiCase.setOnClickListener {
            startActivity(Intent(this, NonUiCaseActivity::class.java))
        }
        binding.btnOpenDecisionGuide.setOnClickListener {
            startActivity(Intent(this, DecisionGuideActivity::class.java))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
