package com.logan.flowbusapp.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.logan.flowbusapp.R
import com.logan.flowbusapp.StickyCaseActivity
import com.logan.flowbusapp.testutil.ShellLaunchedActivityRule
import com.logan.flowbusapp.testutil.recreateCurrentActivity
import com.logan.flowbusapp.testutil.stableScrollTo
import com.logan.flowbusapp.testutil.targetString
import com.logan.flowbusapp.testutil.waitForTextContaining
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StickyCaseActivityTest {

    @get:Rule
    val rule = ShellLaunchedActivityRule(StickyCaseActivity::class.java)

    @Test
    fun clickRememberLatestToolbarState_updatesStickyResultPanels() {
        onView(withId(R.id.btnRememberLatestToolbarState)).perform(stableScrollTo(), click())

        onView(isRoot()).perform(
            waitForTextContaining(
                R.id.tvStickyLatestState,
                targetString(R.string.sticky_case_state_toolbar_ready)
            )
        )

        onView(withId(R.id.tvStickyLatestState)).check(
            matches(withText(containsString(targetString(R.string.sticky_case_state_toolbar_ready))))
        )
        onView(withId(R.id.tvStickyReplayHint)).check(
            matches(withText(containsString(targetString(R.string.sticky_case_hint_live))))
        )
        onView(withId(R.id.tvStickyCaseLog)).check(
            matches(withText(containsString(targetString(R.string.sticky_case_log_live))))
        )
    }

    @Test
    fun recreateAfterRememberingState_replaysLatestStickyValue() {
        onView(withId(R.id.btnRememberLatestToolbarState)).perform(stableScrollTo(), click())

        onView(isRoot()).perform(recreateCurrentActivity(StickyCaseActivity::class.java))

        onView(isRoot()).perform(
            waitForTextContaining(
                R.id.tvStickyReplayHint,
                targetString(R.string.sticky_case_hint_replayed)
            )
        )

        onView(withId(R.id.tvStickyLatestState)).check(
            matches(withText(containsString(targetString(R.string.sticky_case_state_toolbar_ready))))
        )
        onView(withId(R.id.tvStickyReplayHint)).check(
            matches(withText(containsString(targetString(R.string.sticky_case_hint_replayed))))
        )
        onView(withId(R.id.tvStickyCaseLog)).check(
            matches(withText(containsString(targetString(R.string.sticky_case_log_replayed))))
        )
    }

    @Test
    fun clickRecreateCurrentPage_replaysLatestStickyValue() {
        onView(withId(R.id.btnRememberLatestToolbarState)).perform(stableScrollTo(), click())
        onView(withId(R.id.btnRecreateStickyCase)).perform(stableScrollTo(), click())

        onView(isRoot()).perform(
            waitForTextContaining(
                R.id.tvStickyReplayHint,
                targetString(R.string.sticky_case_hint_replayed)
            )
        )

        onView(withId(R.id.tvStickyLatestState)).check(
            matches(withText(containsString(targetString(R.string.sticky_case_state_toolbar_ready))))
        )
        onView(withId(R.id.tvStickyReplayHint)).check(
            matches(withText(containsString(targetString(R.string.sticky_case_hint_replayed))))
        )
    }

    @Test
    fun clearLatestToolbarState_resetsStickyPanels() {
        onView(withId(R.id.btnRememberLatestToolbarState)).perform(stableScrollTo(), click())
        onView(withId(R.id.btnClearLatestToolbarState)).perform(stableScrollTo(), click())

        onView(withId(R.id.tvStickyLatestState)).check(
            matches(withText(targetString(R.string.sticky_case_state_waiting)))
        )
        onView(withId(R.id.tvStickyReplayHint)).check(
            matches(withText(targetString(R.string.sticky_case_hint_waiting)))
        )
        onView(withId(R.id.tvStickyCaseLog)).check(
            matches(withText(targetString(R.string.sticky_case_log_waiting)))
        )
    }
}
