package com.logan.flowbus

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * Android 层的全局 `ViewModelStoreOwner`。
 *
 * 顶层 API 在不传 `owner` 时，会通过这里拿到全局 [FlowEventBus]，因此它并不依赖
 * `Activity` 或 `Fragment` 才能工作。
 *
 * 这个 store 跟随应用进程存在，不会随单个页面销毁而自动 [ViewModelStore.clear]。
 * 如果事件只属于某个页面或业务 owner，优先传入明确的 [ViewModelStoreOwner]，避免把生命周期放大到全局。
 */
object GlobalViewModelStore : ViewModelStoreOwner {
    private val appViewModelStore by lazy { ViewModelStore() }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore

    /**
     * 返回全局共享的 [ViewModel] 实例。
     */
    @MainThread
    fun <T : ViewModel> get(modelClass: Class<T>): T {
        return ViewModelProvider(this, ViewModelProvider.NewInstanceFactory())[modelClass]
    }

    internal fun clearForTests() {
        appViewModelStore.clear()
    }
}

@MainThread
internal fun flowEventBusFor(owner: ViewModelStoreOwner): FlowEventBus {
    return if (owner === GlobalViewModelStore) {
        GlobalViewModelStore.get(FlowEventBus::class.java)
    } else {
        ViewModelProvider(owner).get(FlowEventBus::class.java)
    }
}
