package com.logan.flowbusapp.login

import android.os.SystemClock
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.closeSoftKeyboard
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.logan.flowbusapp.R
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginActivityTest {

    @get:Rule
    val rule = activityScenarioRule<LoginActivity>()

    @Test
    fun clickLoginWithEmptyFields_showsValidationFailure() {
        val failureText = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.login_failed)

        onView(withId(R.id.loginSub)).perform(click())

        onView(withId(R.id.tvLog)).check(matches(withText(containsString(failureText))))
    }

    @Test
    fun clickRegisterWithEmptyFields_showsValidationFailure() {
        val failureText = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.register_failed)

        onView(withId(R.id.registerSub)).perform(click())

        onView(withId(R.id.tvLog)).check(matches(withText(containsString(failureText))))
    }

    @Test
    fun clickLoginWithValidFields_completesOwnerScopedFlow() {
        val successText = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.login_successful)

        onView(withId(R.id.userName)).perform(replaceText("alice"), closeSoftKeyboard())
        onView(withId(R.id.password)).perform(replaceText("123456"), closeSoftKeyboard())
        onView(withId(R.id.loginSub)).perform(click())
        SystemClock.sleep(1_400)

        onView(withId(R.id.tvLog)).check(matches(withText(containsString(successText))))
        onView(withId(R.id.tvLog)).check(matches(withText(containsString("alice"))))
    }

    @Test
    fun clickRegisterWithValidFields_completesRegisterThenLoginFlow() {
        val successText = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.login_successful)

        onView(withId(R.id.userName)).perform(replaceText("bob"), closeSoftKeyboard())
        onView(withId(R.id.password)).perform(replaceText("654321"), closeSoftKeyboard())
        onView(withId(R.id.registerSub)).perform(click())
        SystemClock.sleep(2_700)

        onView(withId(R.id.tvLog)).check(matches(withText(containsString(successText))))
        onView(withId(R.id.tvLog)).check(matches(withText(containsString("bob"))))
    }
}
