package com.logan.flowbusapp.nonui

import com.logan.flowbus.core.DefaultFlowBus
import com.logan.flowbus.core.FlowBusOwner
import com.logan.flowbus.core.emit
import com.logan.flowbus.core.flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SyncRepository(
    owner: FlowBusOwner = NON_UI_CASE_OWNER,
    private val onReady: (String) -> Unit = {}
) {
    private val scopedBus = DefaultFlowBus.scoped(owner)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun start() {
        scope.launch {
            val commandFlow = scopedBus.flow<NonUiSyncCommand>()
            launch {
                (commandFlow as? MutableSharedFlow<NonUiSyncCommand>)
                    ?.subscriptionCount
                    ?.first { it > 0 }
                onReady(NON_UI_REPOSITORY_COMPONENT)
            }
            commandFlow.collect { command ->
                delay(200)
                DefaultFlowBus.emit(
                    RepositorySyncLog(
                        scopeName = scopedBus.scopeName,
                        requestId = command.requestId,
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
