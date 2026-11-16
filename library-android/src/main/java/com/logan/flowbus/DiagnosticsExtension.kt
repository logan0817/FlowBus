package com.logan.flowbus

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModelStoreOwner
import com.logan.flowbus.core.FlowBusDiagnosticObserver
import com.logan.flowbus.core.FlowBusDiagnosticRegistration

/**
 * 观察 [owner] 来源的排障元数据，默认观察 Android 全局来源，首次获取可创建来源。
 *
 * 可在来源创建后安装；完整订阅记录仅覆盖安装后新取得的 Flow，不补历史。
 * 观察器须快速返回且允许并发调用；事件不含 payload 或异常实例，名称仍需由应用脱敏。
 * 调用方应在不再需要诊断时关闭返回句柄，关闭不取消业务订阅或清理 sticky。
 */
@MainThread
@JvmOverloads
fun observeEventDiagnostics(
    observer: FlowBusDiagnosticObserver,
    owner: ViewModelStoreOwner = GlobalViewModelStore
): FlowBusDiagnosticRegistration = flowEventBusFor(owner).observeDiagnostics(observer)
