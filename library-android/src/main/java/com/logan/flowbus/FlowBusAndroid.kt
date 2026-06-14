package com.logan.flowbus

import com.logan.flowbus.core.FlowBusConfig
import com.logan.flowbus.core.FlowBusErrorHandler
import com.logan.flowbus.core.FlowBusLogger

/**
 * Android 层的 FlowBus 默认配置入口。
 *
 * `flowbus` 在创建新的 [FlowEventBus] 时，会从这里读取默认的 [FlowBusConfig]。
 * 如果你需要自定义 logger、错误处理策略或缓冲参数，请在首次调用
 * `postEvent`、`eventFlow`、`subscribeEvent` 之前完成配置。
 *
 * 配置只会影响之后新创建的 [FlowEventBus]，不会回溯修改已经存在的实例。为了避免静默使用
 * 混合配置，只要已经创建过任意 [FlowEventBus]，后续 [configure] 就会被拒绝。
 */
object FlowBusAndroid {
    @Volatile
    private var flowBusConfigFactory: () -> FlowBusConfig = ::builtInFlowBusConfig

    @Volatile
    private var hasCreatedFlowEventBus: Boolean = false

    /**
     * 使用固定配置替换 Android 层默认的 [FlowBusConfig]。
     */
    fun configure(config: FlowBusConfig) {
        configure { config }
    }

    /**
     * 使用工厂替换 Android 层默认配置生成逻辑。
     *
     * 适合需要在不同场景下动态构建配置的情况。
     */
    fun configure(configFactory: () -> FlowBusConfig) {
        synchronized(this) {
            check(!hasCreatedFlowEventBus) {
                "FlowBusAndroid.configure(...) must be called before first global bus use or FlowEventBus creation."
            }
            flowBusConfigFactory = configFactory
        }
    }

    internal fun createFlowBusConfig(): FlowBusConfig {
        synchronized(this) {
            val config = flowBusConfigFactory.invoke()
            hasCreatedFlowEventBus = true
            return config
        }
    }

    internal fun resetForTests() {
        synchronized(this) {
            flowBusConfigFactory = ::builtInFlowBusConfig
            hasCreatedFlowEventBus = false
        }
        GlobalViewModelStore.clearForTests()
    }

    private fun builtInFlowBusConfig(): FlowBusConfig {
        return FlowBusConfig(
            logger = AndroidFlowBusLogger,
            errorHandler = FlowBusErrorHandler.Rethrow
        )
    }
}

private object AndroidFlowBusLogger : FlowBusLogger {
    override fun warn(tag: String, message: String, throwable: Throwable?) {
        android.util.Log.w(tag, sanitizedWarning(message, throwable))
    }

    private fun sanitizedWarning(message: String, throwable: Throwable?): String {
        return if (throwable == null) {
            message
        } else {
            "$message (${throwable::class.java.name})"
        }
    }
}
