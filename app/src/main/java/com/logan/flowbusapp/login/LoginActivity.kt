package com.logan.flowbusapp.login

import android.annotation.SuppressLint
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.logan.flowbusapp.R
import com.logan.flowbusapp.databinding.ActivityLoginBinding
import com.logan.flowbusapp.login.event.LoginComponent

class LoginActivity : AppCompatActivity() {

    private var _binding: ActivityLoginBinding? = null
    private val binding get() = _binding!!

    val loginComponent: LoginComponent by lazy { LoginComponent() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        _binding = ActivityLoginBinding.inflate(layoutInflater)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(binding.root)
        setListeners()

        loginComponent.subscribe(this)
    }


    @SuppressLint("SetTextI18n")
    private fun setListeners() {
        binding.loginSub.setOnClickListener {
            login()
        }
        binding.registerSub.setOnClickListener {
            registerAndLogin()
        }
    }

    fun login() {
        val userName = binding.userName.text.toString().trim()
        val password = binding.password.text.toString().trim()
        if (userName.isBlank() || password.isBlank()) {
            printLog(getString(R.string.login_failed))
            hideLoading()
            return
        }
        loginComponent.login(this, userName)
    }

    fun registerAndLogin() {
        val userName = binding.userName.text.toString().trim()
        val password = binding.password.text.toString().trim()
        if (userName.isBlank() || password.isBlank()) {
            printLog(getString(R.string.register_failed))
            hideLoading()
            return
        }
        loginComponent.registerAndLogin(this, userName)
    }

    fun printLog(value: String) {
        binding.tvLog.text = value
    }

    fun showLoading() {
        binding.progressBar.show()
    }

    fun hideLoading() {
        binding.progressBar.hide()
    }

    override fun onDestroy() {
        super.onDestroy()
        _binding = null
    }
}
