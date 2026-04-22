package com.logan.flowbusapp.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.util.TreeIterables
import com.logan.flowbusapp.NonUiCaseActivity
import com.logan.flowbusapp.R
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matcher
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import android.view.View
import java.util.concurrent.TimeoutException

@RunWith(AndroidJUnit4::class)
class NonUiCaseActivityTest {

    @get:Rule
    val rule = activityScenarioRule<NonUiCaseActivity>()

    @Test
    fun clickStartSync_updatesViewModelRepositoryWorkerPanels() {
        onView(withId(R.id.btnStartSyncFlow)).perform(click())
        onView(isRoot()).perform(waitForText(R.id.tvUiResult, targetString(R.string.non_ui_case_ui_result_done)))

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
                    val text = targetView?.let { it as? android.widget.TextView }?.text?.toString()
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
}
