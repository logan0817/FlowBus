package com.logan.flowbusapp.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.logan.flowbusapp.GlobalCaseActivity
import com.logan.flowbusapp.R
import org.hamcrest.Matchers.anyOf
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlobalCaseActivityTest {

    @get:Rule
    val rule = activityScenarioRule<GlobalCaseActivity>()

    @Test
    fun clickSendGlobalRefresh_updatesResultAndTrace() {
        onView(withId(R.id.btnSendGlobalRefresh)).perform(click())

        onView(withId(R.id.tvGlobalCaseResult)).check(
            matches(
                withText(
                    anyOf(
                        containsString(targetString(R.string.global_case_result_done)),
                        containsString(targetString(R.string.global_case_result_sending))
                    )
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
        onView(withId(R.id.tvGlobalCaseReceiveTrace)).check(matches(withText(containsString(":"))))
    }

    private fun targetString(resId: Int, vararg formatArgs: String): String {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return context.getString(resId, *formatArgs)
    }
}
