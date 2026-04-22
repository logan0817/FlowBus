package com.logan.flowbusapp.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import android.widget.Button
import com.logan.flowbusapp.R
import com.logan.flowbusapp.TestFragmentActivity
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestFragmentActivityTest {

    @get:Rule
    val rule = activityScenarioRule<TestFragmentActivity>()

    @Test
    fun clickNotifyActivityToolbar_updatesHostLogAndFragmentBoundaryHint() {
        clickById(R.id.btnNotifyActivityToolbar)

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
        clickById(R.id.btnRefreshCurrentPanel)

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
        clickById(R.id.btnSendLocalMessageToHost)

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

    private fun targetString(resId: Int): String {
        return InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)
    }

    private fun clickById(viewId: Int) {
        rule.scenario.onActivity { activity ->
            activity.findViewById<Button>(viewId).performClick()
        }
    }
}
