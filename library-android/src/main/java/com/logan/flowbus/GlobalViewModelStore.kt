package com.logan.flowbus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Android 层的全局 `ViewModelStoreOwner`。
 *
 * 顶层 API 在不传 `owner` 时，会通过这里拿到全局 [FlowEventBus]，因此它并不依赖
 * `Activity` 或 `Fragment` 才能工作。
 */
object GlobalViewModelStore : ViewModelStoreOwner {
    private val appViewModelStore by lazy { ViewModelStore() }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore

    /**
     * 返回全局共享的 [ViewModel] 实例。
     */
    fun <T : ViewModel> get(modelClass: Class<T>): T {
        return ViewModelProvider(this, ViewModelProvider.NewInstanceFactory())[modelClass]
    }
}
