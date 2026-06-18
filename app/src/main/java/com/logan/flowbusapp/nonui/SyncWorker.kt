package com.logan.flowbusapp.nonui

import com.logan.flowbus.core.DefaultFlowBus
import com.logan.flowbus.core.FlowBusOwner
import com.logan.flowbus.core.emit
import com.logan.flowbus.core.flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class SyncWorker(
    owner: FlowBusOwner = NON_UI_CASE_OWNER,
    private val onReady: (String) -> Unit = {}
) {
    private val scopedBus = DefaultFlowBus.scoped(owner)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun start() {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val commandFlow = scopedBus.flow<NonUiSyncCommand>()
            launch {
                (commandFlow as? MutableSharedFlow<NonUiSyncCommand>)
                    ?.subscriptionCount
                    ?.first { it > 0 }
                onReady(NON_UI_WORKER_COMPONENT)
            }
            commandFlow.collect { command ->
                delay(350)
                DefaultFlowBus.emit(
                    WorkerSyncLog(
                        scopeName = scopedBus.scopeName,
                        requestId = command.requestId,
                        message = "Worker finished #${command.requestId} from ${command.trigger} via scope '${scopedBus.scopeName}' on ${Thread.currentThread().name}"
                    )
                )
            }
        }
    }

    fun close() {
        scope.cancel()
    }
}
