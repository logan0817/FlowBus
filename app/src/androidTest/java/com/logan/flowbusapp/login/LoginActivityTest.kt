package com.logan.flowbusapp.login

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.logan.flowbusapp.R
import com.logan.flowbusapp.testutil.ShellLaunchedActivityRule
import com.logan.flowbusapp.testutil.setTextDirectly
import com.logan.flowbusapp.testutil.targetString
import com.logan.flowbusapp.testutil.waitForTextContaining
import org.hamcrest.Matchers.containsString
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoginActivityTest {

    @get:Rule
    val rule = ShellLaunchedActivityRule(LoginActivity::class.java)

    @Test
    fun clickLoginWithEmptyFields_showsValidationFailure() {
        val failureText = targetString(R.string.login_failed)

        onView(withId(R.id.loginSub)).perform(click())
        onView(isRoot()).perform(waitForTextContaining(R.id.tvLog, failureText))

        onView(withId(R.id.tvLog)).check(matches(withText(containsString(failureText))))
    }

    @Test
    fun clickRegisterWithEmptyFields_showsValidationFailure() {
        val failureText = targetString(R.string.register_failed)

        onView(withId(R.id.registerSub)).perform(click())
        onView(isRoot()).perform(waitForTextContaining(R.id.tvLog, failureText))

        onView(withId(R.id.tvLog)).check(matches(withText(containsString(failureText))))
    }

    @Test
    fun clickLoginWithValidFields_completesOwnerScopedFlow() {
        val successText = targetString(R.string.login_successful)

        onView(isRoot()).perform(setTextDirectly(R.id.userName, "alice"))
        onView(isRoot()).perform(setTextDirectly(R.id.password, "123456"))
        onView(withId(R.id.loginSub)).perform(click())
        onView(isRoot()).perform(waitForTextContaining(R.id.tvLog, successText, timeoutMs = 4_000L))

        onView(withId(R.id.tvLog)).check(matches(withText(containsString(successText))))
        onView(withId(R.id.tvLog)).check(matches(withText(containsString("alice"))))
    }

    @Test
    fun clickRegisterWithValidFields_completesRegisterThenLoginFlow() {
        val successText = targetString(R.string.login_successful)

        onView(isRoot()).perform(setTextDirectly(R.id.userName, "bob"))
        onView(isRoot()).perform(setTextDirectly(R.id.password, "654321"))
        onView(withId(R.id.registerSub)).perform(click())
        onView(isRoot()).perform(waitForTextContaining(R.id.tvLog, successText, timeoutMs = 3_500L))

        onView(withId(R.id.tvLog)).check(matches(withText(containsString(successText))))
        onView(withId(R.id.tvLog)).check(matches(withText(containsString("bob"))))
    }
}
