package com.logan.flowbusapp.nonui

import com.logan.flowbus.core.DefaultFlowBus
import com.logan.flowbus.core.FlowBusOwner
import com.logan.flowbus.core.flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DemoRepository(
    owner: FlowBusOwner = NON_UI_DEMO_OWNER
) {
    private val scopedBus = DefaultFlowBus.scoped(owner)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            scopedBus.flow<NonUiDemoCommand>().collect { command ->
                delay(200)
                DefaultFlowBus.post(
                    RepositoryDemoLog(
                        message = "Repository handled #${command.requestId} from ${command.trigger} via scope '${scopedBus.scopeName}' on ${Thread.currentThread().name}"
                    )
                )
            }
        }
    }

    fun close() {
        scope.cancel()
    }
}
