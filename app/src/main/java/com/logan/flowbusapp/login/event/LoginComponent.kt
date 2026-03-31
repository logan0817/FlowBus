package com.logan.flowbusapp.login.event

import com.logan.flowbus.postEventTo
import com.logan.flowbus.subscribeEvent
import com.logan.flowbusapp.R
import com.logan.flowbusapp.login.LoginActivity

class LoginComponent {

    fun login(activity: LoginActivity, userName: String?, password: String?) = with(activity) {
        showLoading()
        printLog(getString(R.string.login_requesting))
        //Simulated login request
        postEventTo(owner = this, event = LoginEvent(userName!!, password!!), delayMillis = 2000)
    }

    fun registerAndLogin(activity: LoginActivity, userName: String?, password: String?) = with(activity) {
        showLoading()
        printLog(getString(R.string.register_requesting))
        //Simulated register request
        postEventTo(owner = this, event = RegisterEvent(userName!!, password!!), delayMillis = 2000)

    }

    fun subscribe(activity: LoginActivity) = with(activity) {
        subscribeEvent<LoginEvent>(owner = this) {
            val result = if (it.userName.isNullOrBlank() || it.password.isNullOrBlank()) {
                getString(R.string.login_failed)
            } else {
                getString(R.string.login_successful)
            }
            printLog("$result:${it.userName} - ${it.password}")
            hideLoading()
        }

        subscribeEvent<RegisterEvent>(owner = this) {
            if (it.userName.isNullOrBlank() || it.password.isNullOrBlank()) {
                printLog(getString(R.string.register_failed))
                hideLoading()
            } else {
                printLog(getString(R.string.register_successful))
                //Registration successful. Please log in.
                login(this, it.userName, it.password)
            }
        }
    }
}
