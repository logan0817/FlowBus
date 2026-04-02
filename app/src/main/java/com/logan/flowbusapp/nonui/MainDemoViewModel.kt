package com.logan.flowbusapp.nonui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logan.flowbus.core.DefaultFlowBus
import com.logan.flowbus.core.flow
import com.logan.flowbus.core.post
import kotlinx.coroutines.launch

class MainDemoViewModel : ViewModel() {
    private val demoScope = DefaultFlowBus.openScope(owner = NON_UI_DEMO_OWNER, closeWhen = viewModelScope)
    private val repository = DemoRepository(owner = demoScope)
    private val worker = DemoWorker(owner = demoScope)

    init {
        repository.start()
        worker.start()

        DefaultFlowBus.post(
            ViewModelDemoLog(
                message = "Opened flowbus-core scope '${demoScope.scopeName}'"
            )
        )

        viewModelScope.launch {
            demoScope.flow<NonUiDemoCommand>().collect { command ->
                DefaultFlowBus.post(
                    ViewModelDemoLog(
                        message = "ViewModel received #${command.requestId} from ${command.trigger} in scoped bus '${demoScope.scopeName}' on ${Thread.currentThread().name}"
                    )
                )
            }
        }
    }

    fun runNonUiDemo() {
        demoScope.post(
            NonUiDemoCommand(
                requestId = System.currentTimeMillis(),
                trigger = "MainActivity button"
            )
        )
    }

    override fun onCleared() {
        repository.close()
        worker.close()
        super.onCleared()
    }
}
