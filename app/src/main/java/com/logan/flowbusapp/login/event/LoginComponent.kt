package com.logan.flowbusapp.login.event

import com.logan.flowbus.onEvent
import com.logan.flowbus.postEventTo
import com.logan.flowbusapp.R
import com.logan.flowbusapp.login.LoginActivity

class LoginComponent {

    /**
     * 演示局部 owner 事件：登录请求只在当前 LoginActivity 对应的总线里流动。
     */
    fun login(activity: LoginActivity, userName: String, password: String) = with(activity) {
        showLoading()
        printLog(getString(R.string.login_requesting))
        // 这里只是演示事件驱动解耦，不是推荐把真实登录请求直接建模成事件总线请求-响应。
        postEventTo(owner = this, event = LoginEvent(userName, password), delayMillis = 1200)
    }

    /**
     * 演示注册成功后，再通过同一局部总线继续触发登录事件。
     */
    fun registerAndLogin(activity: LoginActivity, userName: String, password: String) = with(activity) {
        showLoading()
        printLog(getString(R.string.register_requesting))
        postEventTo(owner = this, event = RegisterEvent(userName, password), delayMillis = 1200)
    }

    fun subscribe(activity: LoginActivity) = with(activity) {
        onEvent<LoginEvent>(from = this) {
            printLog("${getString(R.string.login_successful)}: ${it.userName}")
            hideLoading()
        }

        onEvent<RegisterEvent>(from = this) {
            printLog(getString(R.string.register_successful))
            // 注册成功后，再继续触发登录事件，演示同一 owner 作用域内的事件串联。
            login(this, it.userName, it.password)
        }
    }
}
