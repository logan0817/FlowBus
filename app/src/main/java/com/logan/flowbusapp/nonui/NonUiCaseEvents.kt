package com.logan.flowbusapp.nonui

import com.logan.flowbus.core.FlowBusOwner
import com.logan.flowbus.core.flowBusOwner

const val NON_UI_CASE_SCOPE = "case.non_ui"
const val NON_UI_REPOSITORY_COMPONENT = "repository"
const val NON_UI_WORKER_COMPONENT = "worker"

val NON_UI_CASE_OWNER: FlowBusOwner = flowBusOwner(NON_UI_CASE_SCOPE)

data class NonUiSyncCommand(
    val requestId: Long,
    val trigger: String
)

data class ViewModelSyncLog(
    val scopeName: String,
    val requestId: Long,
    val message: String
)

data class RepositorySyncLog(
    val scopeName: String,
    val requestId: Long,
    val message: String
)

data class WorkerSyncLog(
    val scopeName: String,
    val requestId: Long,
    val message: String
)
