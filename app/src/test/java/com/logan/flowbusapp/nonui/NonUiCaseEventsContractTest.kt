package com.logan.flowbusapp.nonui

import org.junit.Assert.assertTrue
import org.junit.Test

class NonUiCaseEventsContractTest {

    @Test
    fun `non ui log events expose routing metadata outside message text`() {
        assertHasRoutingMetadata<ViewModelSyncLog>()
        assertHasRoutingMetadata<RepositorySyncLog>()
        assertHasRoutingMetadata<WorkerSyncLog>()
    }

    private inline fun <reified T : Any> assertHasRoutingMetadata() {
        val fields = T::class.java.declaredFields.map { it.name }

        assertTrue(fields.contains("scopeName"))
        assertTrue(fields.contains("requestId"))
        assertTrue(fields.contains("message"))
    }
}
