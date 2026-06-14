package com.logan.flowbusapp.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.logan.flowbusapp.R
import com.logan.flowbusapp.ScopeCaseActivity
import com.logan.flowbusapp.testutil.ShellLaunchedActivityRule
import com.logan.flowbusapp.testutil.stableScrollTo
import com.logan.flowbusapp.testutil.targetString
import com.logan.flowbusapp.testutil.waitForTextContaining
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ScopeCaseActivityTest {

    @get:Rule
    val rule = ShellLaunchedActivityRule(ScopeCaseActivity::class.java)

    @Test
    fun clickNotifyActivityToolbar_updatesHostLogAndFragmentBoundaryHint() {
        onView(withId(R.id.btnNotifyActivityToolbar)).perform(stableScrollTo(), click())
        onView(isRoot()).perform(
            waitForTextContaining(
                R.id.tvHostLog,
                targetString(R.string.scope_host_log_activity_refreshed)
            )
        )

        onView(withId(R.id.tvHostLog)).check(
            matches(
                withText(
                    containsString(targetString(R.string.scope_host_log_activity_refreshed))
                )
            )
        )
        onView(withId(R.id.tvFragmentScopeHint)).check(
            matches(
                withText(
                    containsString(targetString(R.string.scope_fragment_hint_activity_event))
                )
            )
        )
    }

    @Test
    fun clickRefreshCurrentPanel_updatesFragmentStateAndHostBoundaryHint() {
        onView(withId(R.id.btnRefreshCurrentPanel)).perform(stableScrollTo(), click())
        onView(isRoot()).perform(
            waitForTextContaining(
                R.id.tvFragmentPanelState,
                targetString(R.string.scope_fragment_state_refreshed)
            )
        )

        onView(withId(R.id.tvFragmentPanelState)).check(
            matches(
                withText(
                    containsString(targetString(R.string.scope_fragment_state_refreshed))
                )
            )
        )
        onView(withId(R.id.tvFragmentScopeHint)).check(
            matches(
                withText(
                    containsString(targetString(R.string.scope_fragment_hint_fragment_event))
                )
            )
        )
    }

    @Test
    fun clickSendLocalMessageToHost_updatesHostLog() {
        onView(withId(R.id.btnSendLocalMessageToHost)).perform(stableScrollTo(), click())
        onView(isRoot()).perform(
            waitForTextContaining(
                R.id.tvHostLog,
                targetString(R.string.scope_host_log_fragment_message)
            )
        )

        onView(withId(R.id.tvHostLog)).check(
            matches(
                withText(
                    containsString(targetString(R.string.scope_host_log_fragment_message))
                )
            )
        )
        onView(withId(R.id.tvFragmentScopeHint)).check(
            matches(
                withText(
                    containsString(targetString(R.string.scope_fragment_hint_fragment_to_host))
                )
            )
        )
    }

}
