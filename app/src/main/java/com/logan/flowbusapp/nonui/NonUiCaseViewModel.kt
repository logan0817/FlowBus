package com.logan.flowbusapp.nonui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.logan.flowbus.core.DefaultFlowBus
import com.logan.flowbus.core.emit
import com.logan.flowbus.core.flowBusOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NonUiCaseViewModel : ViewModel() {
    private val caseOwner = flowBusOwner("$NON_UI_CASE_SCOPE.${System.identityHashCode(this)}")
    private val caseScope = DefaultFlowBus.openScope(owner = caseOwner, closeWhen = viewModelScope)
    private val readyComponents = mutableSetOf<String>()
    private val _collectorsReady = MutableStateFlow(false)
    private val repository = SyncRepository(owner = caseScope, onReady = ::markCollectorReady)
    private val worker = SyncWorker(owner = caseScope, onReady = ::markCollectorReady)

    val collectorsReady: StateFlow<Boolean> = _collectorsReady.asStateFlow()

    init {
        repository.start()
        worker.start()
    }

    fun startSyncCase(trigger: String) {
        val requestId = System.currentTimeMillis()
        viewModelScope.launch {
            DefaultFlowBus.emit(
                ViewModelSyncLog(
                    scopeName = caseScope.scopeName,
                    requestId = requestId,
                    message = "ViewModel started sync #$requestId from $trigger via scope '${caseScope.scopeName}'"
                )
            )

            caseScope.emit(
                NonUiSyncCommand(
                    requestId = requestId,
                    trigger = trigger
                )
            )
        }
    }

    fun currentScopeName(): String = caseScope.scopeName

    private fun markCollectorReady(component: String) {
        synchronized(readyComponents) {
            readyComponents += component
            _collectorsReady.value =
                readyComponents.contains(NON_UI_REPOSITORY_COMPONENT) &&
                readyComponents.contains(NON_UI_WORKER_COMPONENT)
        }
    }

    override fun onCleared() {
        repository.close()
        worker.close()
        super.onCleared()
    }
}
