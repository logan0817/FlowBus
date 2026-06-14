package com.logan.flowbusapp.app

import android.app.Activity
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.logan.flowbusapp.ChannelCaseActivity
import com.logan.flowbusapp.DecisionGuideActivity
import com.logan.flowbusapp.GlobalCaseActivity
import com.logan.flowbusapp.MainActivity
import com.logan.flowbusapp.NonUiCaseActivity
import com.logan.flowbusapp.R
import com.logan.flowbusapp.StickyCaseActivity
import com.logan.flowbusapp.ScopeCaseActivity
import com.logan.flowbusapp.testutil.ResumedActivityRegistry
import com.logan.flowbusapp.testutil.ShellLaunchedActivityRule
import com.logan.flowbusapp.testutil.stableScrollTo
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val rule = ShellLaunchedActivityRule(MainActivity::class.java)

    @Test
    fun scenarioCards_existAndOpenExpectedActivities() {
        onView(withId(R.id.cardGlobalCase)).check(matches(isDisplayed()))

        assertLaunchesActivity(R.id.btnOpenGlobalCase, GlobalCaseActivity::class.java)
        assertLaunchesActivity(R.id.btnOpenScopeCase, ScopeCaseActivity::class.java)
        assertLaunchesActivity(R.id.btnOpenStickyCase, StickyCaseActivity::class.java)
        assertLaunchesActivity(R.id.btnOpenChannelCase, ChannelCaseActivity::class.java)
        assertLaunchesActivity(R.id.btnOpenNonUiCase, NonUiCaseActivity::class.java)
        assertLaunchesActivity(R.id.btnOpenDecisionGuide, DecisionGuideActivity::class.java)
    }

    private fun assertLaunchesActivity(triggerViewId: Int, activityClass: Class<out Activity>) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val monitor = instrumentation.addMonitor(activityClass.name, null, false)

        try {
            onView(withId(triggerViewId)).perform(stableScrollTo(), click())
            val launchedActivity = instrumentation.waitForMonitorWithTimeout(monitor, 3_000)
            assertNotNull("Expected ${activityClass.simpleName} to launch", launchedActivity)
            launchedActivity?.finish()
            instrumentation.waitForIdleSync()
            ResumedActivityRegistry.waitForResumedActivity(MainActivity::class.java)
        } finally {
            instrumentation.removeMonitor(monitor)
        }
    }
}
