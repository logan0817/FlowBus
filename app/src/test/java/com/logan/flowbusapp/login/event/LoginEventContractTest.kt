package com.logan.flowbusapp.login.event

import org.junit.Assert.assertFalse
import org.junit.Test

class LoginEventContractTest {

    @Test
    fun `login sample events do not expose password payloads`() {
        val loginFields = LoginEvent::class.java.declaredFields.map { it.name }
        val registerFields = RegisterEvent::class.java.declaredFields.map { it.name }

        assertFalse(loginFields.any { it.contains("password", ignoreCase = true) })
        assertFalse(registerFields.any { it.contains("password", ignoreCase = true) })
    }
}
