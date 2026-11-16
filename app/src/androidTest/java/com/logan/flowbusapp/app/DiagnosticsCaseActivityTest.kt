package com.logan.flowbusapp.app

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.logan.flowbusapp.DiagnosticsCaseActivity
import com.logan.flowbusapp.R
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
class DiagnosticsCaseActivityTest {
    @get:Rule
    val rule = ShellLaunchedActivityRule(DiagnosticsCaseActivity::class.java)

    @Test
    fun fourCasesShowActualSourceAndFailureMetadata() {
        onView(isRoot()).perform(
            waitForTextContaining(R.id.tvDiagnosticsState, targetString(R.string.diagnostics_case_ready))
        )
        onView(withId(R.id.btnDiagnosticsNoSubscriber)).perform(stableScrollTo(), click())
        onView(withId(R.id.tvDiagnosticsLog)).check(matches(withText(containsString("diagnostics.offline"))))
        onView(withId(R.id.tvDiagnosticsLog)).check(matches(withText(containsString("AcceptedWithoutActiveSubscriber"))))

        onView(withId(R.id.btnDiagnosticsWrongSource)).perform(stableScrollTo(), click())
        onView(withId(R.id.tvDiagnosticsLog)).check(matches(withText(containsString("B bus="))))
        onView(withId(R.id.btnDiagnosticsRejected)).perform(stableScrollTo(), click())
        onView(withId(R.id.tvDiagnosticsLog)).check(matches(withText(containsString("RejectedByBuffer"))))

        onView(withId(R.id.btnDiagnosticsCallback)).perform(stableScrollTo(), click())
        onView(isRoot()).perform(waitForTextContaining(R.id.tvDiagnosticsLog, "SubscriberCallback"))
        onView(withId(R.id.tvDiagnosticsLog)).check(matches(withText(containsString("java.lang.IllegalStateException"))))
        onView(withId(R.id.tvDiagnosticsState)).check(matches(withText(R.string.diagnostics_case_ready)))
    }

    @Test
    fun recreateInstallsDiagnosticsAndSubscribesOnNewPage() {
        onView(isRoot()).perform(
            waitForTextContaining(R.id.tvDiagnosticsState, targetString(R.string.diagnostics_case_ready))
        )
        onView(isRoot()).perform(recreateCurrentActivity(DiagnosticsCaseActivity::class.java))
        onView(isRoot()).perform(
            waitForTextContaining(R.id.tvDiagnosticsState, targetString(R.string.diagnostics_case_ready))
        )
        onView(withId(R.id.tvDiagnosticsLog)).check(matches(withText(containsString("SubscriptionStarted"))))
        onView(withId(R.id.btnDiagnosticsCallback)).perform(stableScrollTo(), click())
        onView(isRoot()).perform(waitForTextContaining(R.id.tvDiagnosticsLog, "SubscriberCallback"))
    }
}
