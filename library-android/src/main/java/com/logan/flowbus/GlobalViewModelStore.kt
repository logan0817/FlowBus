package com.logan.flowbus

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

/**
 * GlobalViewModelStore
 *
 * @author logan
 * @email notwalnut@163.com
 * @date 2025/12/11
 */
object GlobalViewModelStore : ViewModelStoreOwner {
    private val appViewModelStore by lazy { ViewModelStore() }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore

    fun <T : ViewModel> get(modelClass: Class<T>): T {
        return ViewModelProvider(this, ViewModelProvider.NewInstanceFactory())[modelClass]
    }
}