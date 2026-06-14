package com.logan.flowbusapp.testutil

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.ExternalResource
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ShellLaunchedActivityRule(
    private val activityClass: Class<out Activity>
) : ExternalResource() {
    private var launchedActivity: Activity? = null

    override fun before() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        ResumedActivityRegistry.ensureRegistered()
        ResumedActivityRegistry.clearCurrentActivity()
        launchTargetApp(instrumentation, targetContext.packageName)
        if (activityClass.name != "${targetContext.packageName}.MainActivity") {
            val intent = Intent(targetContext, activityClass)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            targetContext.startActivity(intent)
        }
        launchedActivity = ResumedActivityRegistry.waitForResumedActivity(activityClass)
    }

    override fun after() {
        val activity = launchedActivity
        if (activity != null && !activity.isFinishing) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                activity.finish()
            }
            ResumedActivityRegistry.waitForActivityToStop(activity)
        }
        launchedActivity = null
    }

    private fun launchTargetApp(
        instrumentation: android.app.Instrumentation,
        packageName: String
    ) {
        val output = executeShellCommand(
            instrumentation,
            "am start -W -f 0x10008000 -n $packageName/.MainActivity"
        )
        check(output.contains("Status: ok") || output.contains("Status: warning")) {
            "Failed to bring $packageName to foreground: $output"
        }
        ResumedActivityRegistry.waitForResumedActivity(
            activityClass = Class.forName("$packageName.MainActivity").asSubclass(Activity::class.java)
        )
    }

    private fun executeShellCommand(
        instrumentation: android.app.Instrumentation,
        command: String
    ): String {
        val descriptor = instrumentation.uiAutomation.executeShellCommand(command)
        val input = FileInputStream(descriptor.fileDescriptor)
        return try {
            val output = StringBuilder()
            val buffer = ByteArray(8 * 1024)
            var bytesRead = input.read(buffer)
            while (bytesRead >= 0) {
                output.append(String(buffer, 0, bytesRead, StandardCharsets.UTF_8))
                bytesRead = input.read(buffer)
            }
            output.toString()
        } finally {
            input.close()
            descriptor.close()
        }
    }
}

internal object ResumedActivityRegistry {
    private val isRegistered = AtomicBoolean(false)
    private val currentActivity = AtomicReference<Activity?>()

    fun ensureRegistered() {
        if (isRegistered.get()) {
            return
        }

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            if (isRegistered.get()) {
                return@runOnMainSync
            }

            val application = instrumentation.targetContext.applicationContext as Application
            application.registerActivityLifecycleCallbacks(
                object : Application.ActivityLifecycleCallbacks {
                    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                    override fun onActivityStarted(activity: Activity) = Unit

                    override fun onActivityResumed(activity: Activity) {
                        currentActivity.set(activity)
                    }

                    override fun onActivityPaused(activity: Activity) {
                        currentActivity.compareAndSet(activity, null)
                    }
                    override fun onActivityStopped(activity: Activity) = Unit
                    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit

                    override fun onActivityDestroyed(activity: Activity) {
                        currentActivity.compareAndSet(activity, null)
                    }
                }
            )
            isRegistered.set(true)
        }
    }

    fun waitForResumedActivity(activityClass: Class<out Activity>, timeoutMs: Long = 5_000L): Activity {
        val endTime = System.currentTimeMillis() + timeoutMs
        do {
            val activity = currentActivity.get()
            if (activity != null && activityClass.isInstance(activity)) {
                return activity
            }
            Thread.sleep(50)
        } while (System.currentTimeMillis() < endTime)

        error("No resumed ${activityClass.simpleName} found within $timeoutMs ms")
    }

    fun clearCurrentActivity() {
        currentActivity.set(null)
    }

    fun waitForActivityToStop(activity: Activity, timeoutMs: Long = 5_000L) {
        val endTime = System.currentTimeMillis() + timeoutMs
        do {
            if (currentActivity.get() !== activity || activity.isDestroyed) {
                return
            }
            Thread.sleep(50)
        } while (System.currentTimeMillis() < endTime)

        error("${activity::class.java.simpleName} was still resumed after $timeoutMs ms")
    }

    fun requireResumedActivity(activityClass: Class<out Activity>): Activity {
        val activity = currentActivity.get()
        check(activity != null && activityClass.isInstance(activity)) {
            "No resumed ${activityClass.simpleName} found"
        }
        return activity
    }
}
