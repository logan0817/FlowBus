package com.logan.flowbusapp.login.event

import com.logan.flowbus.onEvent
import com.logan.flowbus.postScopedEvent
import com.logan.flowbusapp.R
import com.logan.flowbusapp.login.LoginActivity

class LoginComponent {

    /**
     * 演示局部 owner 事件：登录请求只在当前 LoginActivity 对应的总线里流动。
     */
    fun login(activity: LoginActivity, userName: String) = with(activity) {
        showLoading()
        printLog(getString(R.string.login_requesting))
        // 示例只传递非敏感的完成通知；真实密码、token 等敏感值不要进入事件总线 payload。
        postScopedEvent(LoginEvent(userName), delayMillis = 1200)
    }

    /**
     * 演示注册成功后，再通过同一局部总线继续触发登录事件。
     */
    fun registerAndLogin(activity: LoginActivity, userName: String) = with(activity) {
        showLoading()
        printLog(getString(R.string.register_requesting))
        postScopedEvent(RegisterEvent(userName), delayMillis = 1200)
    }

    fun subscribe(activity: LoginActivity) = with(activity) {
        onEvent<LoginEvent>(from = this) {
            printLog("${getString(R.string.login_successful)}: ${it.userName}")
            hideLoading()
        }

        onEvent<RegisterEvent>(from = this) {
            printLog(getString(R.string.register_successful))
            // 注册成功后，再继续触发登录事件，演示同一 owner 作用域内的事件串联。
            login(this, it.userName)
        }
    }
}
