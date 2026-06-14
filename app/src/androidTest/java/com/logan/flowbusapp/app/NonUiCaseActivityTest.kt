package com.logan.flowbusapp.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.logan.flowbusapp.NonUiCaseActivity
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
class NonUiCaseActivityTest {

    @get:Rule
    val rule = ShellLaunchedActivityRule(NonUiCaseActivity::class.java)

    @Test
    fun clickStartSync_updatesViewModelRepositoryWorkerPanels() {
        onView(isRoot()).perform(
            waitForTextContaining(
                R.id.tvUiResult,
                targetString(R.string.non_ui_case_ui_result_ready)
            )
        )

        onView(withId(R.id.btnStartSyncFlow)).perform(stableScrollTo(), click())
        onView(isRoot()).perform(
            waitForTextContaining(
                R.id.tvUiResult,
                targetString(R.string.non_ui_case_ui_result_done)
            )
        )

        onView(withId(R.id.tvViewModelLog)).check(
            matches(withText(containsString(targetString(R.string.non_ui_case_view_model_result))))
        )
        onView(withId(R.id.tvRepositoryLog)).check(
            matches(withText(containsString(targetString(R.string.non_ui_case_repository_result))))
        )
        onView(withId(R.id.tvWorkerLog)).check(
            matches(withText(containsString(targetString(R.string.non_ui_case_worker_result))))
        )
        onView(withId(R.id.tvUiResult)).check(
            matches(withText(containsString(targetString(R.string.non_ui_case_ui_result_done))))
        )
    }
}
