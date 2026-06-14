package com.logan.flowbusapp.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.logan.flowbusapp.GlobalCaseActivity
import com.logan.flowbusapp.R
import com.logan.flowbusapp.testutil.ShellLaunchedActivityRule
import com.logan.flowbusapp.testutil.stableScrollTo
import com.logan.flowbusapp.testutil.targetString
import com.logan.flowbusapp.testutil.waitForTextContaining
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlobalCaseActivityTest {

    @get:Rule
    val rule = ShellLaunchedActivityRule(GlobalCaseActivity::class.java)

    @Test
    fun clickSendGlobalRefresh_updatesResultAndTrace() {
        onView(withId(R.id.btnSendGlobalRefresh)).perform(stableScrollTo(), click())
        onView(isRoot()).perform(
            waitForTextContaining(
                R.id.tvGlobalCaseReceiveTrace,
                targetString(R.string.global_case_receive_trace_done)
            )
        )

        onView(withId(R.id.tvGlobalCaseResult)).check(
            matches(
                withText(
                    containsString(targetString(R.string.global_case_result_done))
                )
            )
        )
        onView(withId(R.id.tvGlobalCaseSource)).check(
            matches(
                withText(
                    containsString(targetString(R.string.global_case_trigger_source))
                )
            )
        )
        onView(withId(R.id.tvGlobalCaseSendTrace)).check(
            matches(
                withText(
                    containsString(targetString(R.string.global_case_send_trace_done, targetString(R.string.global_case_trigger_source)))
                )
            )
        )
        onView(withId(R.id.tvGlobalCaseReceiveTrace)).check(
            matches(
                withText(
                    containsString(targetString(R.string.global_case_receive_trace_done))
                )
            )
        )
    }
}
