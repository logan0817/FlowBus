package com.logan.flowbusapp.testutil

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.logan.flowbusapp.MainActivity
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.junit.runners.model.Statement

@RunWith(AndroidJUnit4::class)
class ShellLaunchedActivityRuleTest {
    @Test
    fun failureKeepsOriginalExceptionAndCapturesStateBeforeCleanup() {
        val rule = ShellLaunchedActivityRule(MainActivity::class.java)
        val expected = AssertionError("controlled diagnostic regression")
        val statement = rule.apply(object : Statement() {
            override fun evaluate() {
                throw expected
            }
        }, Description.createTestDescription(javaClass, "controlledFailure"))

        val actual = assertThrows(AssertionError::class.java) { statement.evaluate() }
        assertSame(expected, actual)
        val suppressed = SuppressedExceptions.get(actual)
        assertTrue("Diagnostic failures: ${suppressed.toList()}", suppressed.isEmpty())

        val directory = rule.diagnosticsDirectory
        val state = executeDiagnosticScript(
            "cat $directory/activity-state.txt; " +
                "for name in window.txt input.txt activities.txt screenshot.png; do " +
                "test -s $directory/\$name && echo FILE_OK=\$name; done"
        )
        assertTrue(state.contains("stage=resumed"))
        assertTrue(state.contains("activity=${MainActivity::class.java.name}"))
        assertTrue(state.contains("isFinishing=false isDestroyed=false"))
        listOf("window.txt", "input.txt", "activities.txt", "screenshot.png").forEach { name ->
            assertTrue("Missing diagnostic file: $name", state.contains("FILE_OK=$name"))
        }
    }
}
