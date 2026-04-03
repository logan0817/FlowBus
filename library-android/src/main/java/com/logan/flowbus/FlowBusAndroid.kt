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
 * 注意：配置只会影响之后新创建的 [FlowEventBus]，不会回溯修改已经存在的实例。
 */
object FlowBusAndroid {
    @Volatile
    private var flowBusConfigFactory: () -> FlowBusConfig = ::builtInFlowBusConfig

    /**
     * 使用固定配置替换 Android 层默认的 [FlowBusConfig]。
     */
    fun configure(config: FlowBusConfig) {
        flowBusConfigFactory = { config }
    }

    /**
     * 使用工厂替换 Android 层默认配置生成逻辑。
     *
     * 适合需要在不同场景下动态构建配置的情况。
     */
    fun configure(configFactory: () -> FlowBusConfig) {
        flowBusConfigFactory = configFactory
    }

    internal fun createFlowBusConfig(): FlowBusConfig = flowBusConfigFactory.invoke()

    internal fun resetForTests() {
        flowBusConfigFactory = ::builtInFlowBusConfig
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
        android.util.Log.w(tag, message, throwable)
    }
}
