package com.logan.flowbusapp.nonui

import com.logan.flowbus.core.FlowBusOwner
import com.logan.flowbus.core.flowBusOwner

const val NON_UI_DEMO_SCOPE = "demo.non_ui"

val NON_UI_DEMO_OWNER: FlowBusOwner = flowBusOwner(NON_UI_DEMO_SCOPE)

data class NonUiDemoCommand(
    val requestId: Long,
    val trigger: String
)

data class ViewModelDemoLog(
    val message: String
)

data class RepositoryDemoLog(
    val message: String
)

data class WorkerDemoLog(
    val message: String
)
