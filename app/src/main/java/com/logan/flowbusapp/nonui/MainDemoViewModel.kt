package com.logan.flowbusapp.nonui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logan.flowbus.core.DefaultFlowBus
import com.logan.flowbus.core.flowBusOwner
import com.logan.flowbus.core.post

class MainDemoViewModel : ViewModel() {
    private val demoOwner = flowBusOwner("$NON_UI_DEMO_SCOPE.${System.identityHashCode(this)}")
    private val demoScope = DefaultFlowBus.openScope(owner = demoOwner, closeWhen = viewModelScope)
    private val repository = DemoRepository(owner = demoScope)
    private val worker = DemoWorker(owner = demoScope)

    init {
        repository.start()
        worker.start()
    }

    fun runNonUiDemo(trigger: String) {
        val requestId = System.currentTimeMillis()
        DefaultFlowBus.post(
            ViewModelDemoLog(
                message = "ViewModel started sync #$requestId from $trigger via scope '${demoScope.scopeName}'"
            )
        )
        demoScope.post(
            NonUiDemoCommand(
                requestId = requestId,
                trigger = trigger
            )
        )
    }

    fun currentScopeName(): String = demoScope.scopeName

    override fun onCleared() {
        repository.close()
        worker.close()
        super.onCleared()
    }
}
