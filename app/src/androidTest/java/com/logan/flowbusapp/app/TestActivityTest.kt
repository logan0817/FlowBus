package com.logan.flowbusapp.app

import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.util.TreeIterables
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.logan.flowbusapp.R
import com.logan.flowbusapp.TestActivity
import org.hamcrest.Matcher
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeoutException

@RunWith(AndroidJUnit4::class)
class TestActivityTest {

    @get:Rule
    val rule = activityScenarioRule<TestActivity>()

    @Test
    fun clickRememberLatestToolbarState_updatesStickyResultPanels() {
        clickById(R.id.btnRememberLatestToolbarState)

        onView(isRoot()).perform(
            waitForText(
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
        clickById(R.id.btnRememberLatestToolbarState)

        rule.scenario.recreate()

        onView(isRoot()).perform(
            waitForText(
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
        clickById(R.id.btnRememberLatestToolbarState)
        clickById(R.id.btnRecreateStickyCase)

        onView(isRoot()).perform(
            waitForText(
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
        clickById(R.id.btnRememberLatestToolbarState)
        clickById(R.id.btnClearLatestToolbarState)

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

    private fun targetString(resId: Int): String {
        return InstrumentationRegistry.getInstrumentation().targetContext.getString(resId)
    }

    private fun waitForText(viewId: Int, expectedText: String, timeoutMs: Long = 1_500L): ViewAction {
        return object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()

            override fun getDescription(): String {
                return "wait up to $timeoutMs ms for view $viewId to contain $expectedText"
            }

            override fun perform(uiController: UiController, view: View) {
                val endTime = System.currentTimeMillis() + timeoutMs
                do {
                    val targetView = TreeIterables.breadthFirstViewTraversal(view)
                        .firstOrNull { it.id == viewId }
                    val text = (targetView as? TextView)?.text?.toString()
                    if (text?.contains(expectedText) == true) {
                        return
                    }
                    uiController.loopMainThreadForAtLeast(50)
                } while (System.currentTimeMillis() < endTime)

                throw PerformException.Builder()
                    .withActionDescription(description)
                    .withCause(TimeoutException("View $viewId did not contain '$expectedText' within $timeoutMs ms"))
                    .build()
            }
        }
    }

    private fun clickById(viewId: Int) {
        rule.scenario.onActivity { activity ->
            activity.findViewById<Button>(viewId).performClick()
        }
    }
}
