package com.logan.flowbusapp.testutil

import android.app.Activity
import android.graphics.Rect
import android.view.View
import android.view.ViewParent
import android.widget.TextView
import android.widget.ScrollView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.util.TreeIterables
import androidx.test.platform.app.InstrumentationRegistry
import org.hamcrest.Matcher
import java.util.concurrent.TimeoutException
import kotlin.math.abs

fun targetString(resId: Int, vararg formatArgs: Any): String {
    val context = InstrumentationRegistry.getInstrumentation().targetContext
    return context.getString(resId, *formatArgs)
}

fun waitForTextContaining(
    viewId: Int,
    expectedText: String,
    timeoutMs: Long = 2_000L
): ViewAction {
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

            throw TimeoutException("View $viewId did not contain '$expectedText' within $timeoutMs ms")
        }
    }
}

fun stableScrollTo(): ViewAction {
    return object : ViewAction {
        override fun getConstraints(): Matcher<View> = isAssignableFrom(View::class.java)

        override fun getDescription(): String {
            return "scroll target view into the nearest ScrollView viewport"
        }

        override fun perform(uiController: UiController, view: View) {
            val scrollView = view.findScrollParent()
            if (scrollView == null) {
                view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
                uiController.loopMainThreadUntilIdle()
                return
            }

            repeat(20) {
                val scrollRect = Rect()
                scrollView.getGlobalVisibleRect(scrollRect)
                val location = IntArray(2)
                view.getLocationOnScreen(location)

                val viewCenter = location[1] + view.height / 2
                val viewportCenter = scrollRect.top + scrollRect.height() / 2
                val delta = viewCenter - viewportCenter

                if (abs(delta) <= SCROLL_MARGIN_PX && view.isVisibleEnough(0.95)) {
                    return
                }
                val beforeScrollY = scrollView.scrollY
                scrollView.scrollBy(0, delta)
                uiController.loopMainThreadForAtLeast(50)
                if (scrollView.scrollY == beforeScrollY && view.isVisibleEnough(0.9)) {
                    return
                }
            }

            check(view.isVisibleEnough(0.9)) {
                "View ${view.id} could not be scrolled into the visible viewport."
            }
        }
    }
}

fun setTextDirectly(viewId: Int, text: String): ViewAction {
    return object : ViewAction {
        override fun getConstraints(): Matcher<View> = isRoot()

        override fun getDescription(): String {
            return "set text on view $viewId"
        }

        override fun perform(uiController: UiController, view: View) {
            val targetView = TreeIterables.breadthFirstViewTraversal(view)
                .firstOrNull { it.id == viewId } as? TextView
            check(targetView != null) {
                "No TextView found for id $viewId"
            }
            targetView.text = text
            uiController.loopMainThreadUntilIdle()
        }
    }
}

private const val SCROLL_MARGIN_PX = 24

private fun View.findScrollParent(): ScrollView? {
    var parentView: ViewParent? = parent
    while (parentView is View) {
        if (parentView is ScrollView) {
            return parentView
        }
        parentView = (parentView as View).parent
    }
    return null
}

private fun View.isVisibleEnough(requiredRatio: Double): Boolean {
    if (!isShown || width <= 0 || height <= 0) {
        return false
    }
    val visibleRect = Rect()
    if (!getGlobalVisibleRect(visibleRect)) {
        return false
    }
    val visibleArea = visibleRect.width() * visibleRect.height()
    val totalArea = width * height
    return visibleArea >= totalArea * requiredRatio
}

fun recreateCurrentActivity(activityClass: Class<out Activity>): ViewAction {
    return object : ViewAction {
        override fun getConstraints(): Matcher<View> = isRoot()

        override fun getDescription(): String {
            return "recreate current ${activityClass.simpleName}"
        }

        override fun perform(uiController: UiController, view: View) {
            val activity = ResumedActivityRegistry.requireResumedActivity(activityClass)
            activity.recreate()
            uiController.loopMainThreadForAtLeast(100)
        }
    }
}
