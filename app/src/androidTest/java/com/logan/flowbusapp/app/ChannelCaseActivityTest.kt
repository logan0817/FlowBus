package com.logan.flowbusapp.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.logan.flowbusapp.ChannelCaseActivity
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
class ChannelCaseActivityTest {

    @get:Rule
    val rule = ShellLaunchedActivityRule(ChannelCaseActivity::class.java)

    @Test
    fun clickToastCommand_updatesOnlyToastPanel() {
        onView(withId(R.id.btnSendToastCommand)).perform(stableScrollTo(), click())
        onView(isRoot()).perform(
            waitForTextContaining(
                R.id.tvToastChannelLog,
                targetString(R.string.channel_case_toast_result)
            )
        )

        onView(withId(R.id.tvToastChannelLog)).check(
            matches(withText(containsString(targetString(R.string.channel_case_toast_result))))
        )
        onView(withId(R.id.tvNavigationChannelLog)).check(
            matches(withText(targetString(R.string.channel_case_navigation_waiting)))
        )
        onView(withId(R.id.tvSnackbarChannelLog)).check(
            matches(withText(targetString(R.string.channel_case_snackbar_waiting)))
        )
    }

    @Test
    fun clickNavigationCommand_updatesOnlyNavigationPanel() {
        onView(withId(R.id.btnSendNavigationCommand)).perform(stableScrollTo(), click())
        onView(isRoot()).perform(
            waitForTextContaining(
                R.id.tvNavigationChannelLog,
                targetString(R.string.channel_case_navigation_result)
            )
        )

        onView(withId(R.id.tvNavigationChannelLog)).check(
            matches(withText(containsString(targetString(R.string.channel_case_navigation_result))))
        )
        onView(withId(R.id.tvToastChannelLog)).check(
            matches(withText(targetString(R.string.channel_case_toast_waiting)))
        )
        onView(withId(R.id.tvSnackbarChannelLog)).check(
            matches(withText(targetString(R.string.channel_case_snackbar_waiting)))
        )
    }

    @Test
    fun clickSnackbarCommand_updatesOnlySnackbarPanel() {
        onView(withId(R.id.btnSendSnackbarCommand)).perform(stableScrollTo(), click())
        onView(isRoot()).perform(
            waitForTextContaining(
                R.id.tvSnackbarChannelLog,
                targetString(R.string.channel_case_snackbar_result)
            )
        )

        onView(withId(R.id.tvSnackbarChannelLog)).check(
            matches(withText(containsString(targetString(R.string.channel_case_snackbar_result))))
        )
        onView(withId(R.id.tvToastChannelLog)).check(
            matches(withText(targetString(R.string.channel_case_toast_waiting)))
        )
        onView(withId(R.id.tvNavigationChannelLog)).check(
            matches(withText(targetString(R.string.channel_case_navigation_waiting)))
        )
    }

}
