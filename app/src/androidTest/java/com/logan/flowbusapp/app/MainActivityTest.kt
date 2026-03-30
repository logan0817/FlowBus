package com.logan.flowbusapp.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.logan.flowbusapp.MainActivity
import com.logan.flowbusapp.R
import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityTest {

    @get:Rule
    val rule = activityScenarioRule<MainActivity>()

    @Test
    fun clickSendGlobalEvent_updatesGlobalEventViews() {
        onView(withId(R.id.btnSendGlobalEvent)).perform(click())

        onView(withId(R.id.tvGlobalEvent01)).check(matches(withText(containsString("Main GlobalEvent"))))
        onView(withId(R.id.tvGlobalEvent02)).check(matches(withText(containsString("MainGlobalEvent"))))
        onView(withId(R.id.tvGlobalEvent03)).check(matches(withText(containsString("Main GlobalEvent"))))
    }

    @Test
    fun clickSendActivityEvent_updatesActivityScopedViews() {
        onView(withId(R.id.btnSendActivityEvent)).perform(click())

        onView(withId(R.id.tvActivityEvent1)).check(matches(withText(containsString("Main ActivityEvent"))))
        onView(withId(R.id.tvActivityEvent3)).check(matches(withText(containsString("Main ActivityEvent"))))
        onView(withId(R.id.tvActivityEvent2)).check(matches(not(withText(R.string.default_placeholder_log_text))))
        onView(withId(R.id.tvActivityEvent2)).check(matches(isDisplayed()))
    }
}
