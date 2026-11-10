package com.logan.flowbusapp.testutil

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.view.ViewTreeObserver
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.FileInputStream
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class ShellLaunchedActivityRule(
    private val activityClass: Class<out Activity>
) : TestRule {
    private var launchedActivity: Activity? = null
    private var focusListener: ViewTreeObserver.OnWindowFocusChangeListener? = null
    private val focusEvents = StringBuilder()
    internal var diagnosticsDirectory: String = ""
        private set

    override fun apply(base: Statement, description: Description): Statement = object : Statement() {
        override fun evaluate() {
            val name = "${description.className}.${description.methodName}".replace(Regex("[^A-Za-z0-9._-]"), "_")
            diagnosticsDirectory = "/data/local/tmp/flowbus-ui-focus/$name-${UUID.randomUUID()}"
            var failure: Throwable? = null
            try {
                before()
                base.evaluate()
            } catch (error: Throwable) {
                failure = error
                try {
                    captureDiagnostics().forEach { SuppressedExceptions.add(error, it) }
                } catch (diagnosticError: Throwable) {
                    SuppressedExceptions.add(error, diagnosticError)
                }
            } finally {
                try {
                    after()
                } catch (cleanupError: Throwable) {
                    if (failure == null) failure = cleanupError else SuppressedExceptions.add(failure, cleanupError)
                }
            }
            failure?.let { throw it }
        }
    }

    private fun before() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        ResumedActivityRegistry.ensureRegistered()
        ResumedActivityRegistry.clearCurrentActivity()
        val windowState = executeShellCommand(instrumentation, "dumpsys window windows")
        // Google 模拟器的 Launcher ANR 对话框会抢占焦点；只恢复已确认的系统进程。
        // 不处理目标应用的 ANR，也不隐藏系统错误或放宽 Espresso 的焦点要求。
        if (windowState.contains("Application Not Responding: com.google.android.apps.nexuslauncher")) {
            println("FlowBus test environment: resetting unresponsive Google Launcher before launch:")
            println(windowState)
            executeShellCommand(instrumentation, "am force-stop com.google.android.apps.nexuslauncher")
        }
        launchTargetApp(instrumentation, targetContext.packageName)
        if (activityClass.name != "${targetContext.packageName}.MainActivity") {
            val intent = Intent(targetContext, activityClass)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            targetContext.startActivity(intent)
        }
        launchedActivity = ResumedActivityRegistry.waitForResumedActivity(activityClass)
        instrumentation.runOnMainSync {
            val activity = requireNotNull(launchedActivity)
            focusEvents.setLength(0)
            recordFocus("resumed", activity.hasWindowFocus())
            val listener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
                recordFocus("window-focus-changed", hasFocus)
            }
            focusListener = listener
            activity.window.decorView.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        }
    }

    private fun after() {
        val activity = launchedActivity
        if (activity != null) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            instrumentation.runOnMainSync {
                focusListener?.let { listener ->
                    val observer = activity.window.decorView.viewTreeObserver
                    if (observer.isAlive) observer.removeOnWindowFocusChangeListener(listener)
                }
            }
            if (!activity.isFinishing) {
                instrumentation.runOnMainSync { activity.finish() }
                ResumedActivityRegistry.waitForActivityToBeDestroyed(activity)
            }
        }
        focusListener = null
        launchedActivity = null
    }

    private fun recordFocus(stage: String, hasFocus: Boolean) {
        val event = "uptimeMs=${SystemClock.uptimeMillis()} stage=$stage hasWindowFocus=$hasFocus"
        focusEvents.append(event).append('\n')
        println("FlowBus UI focus ${activityClass.name}: $event")
    }

    private fun captureDiagnostics(): List<Throwable> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val failures = mutableListOf<Throwable>()
        val state = StringBuilder()
        instrumentation.runOnMainSync {
            state.append(focusEvents)
            launchedActivity?.let { activity ->
                state.append("failureUptimeMs=${SystemClock.uptimeMillis()} activity=${activity.javaClass.name} ")
                    .append("hasWindowFocus=${activity.hasWindowFocus()} isFinishing=${activity.isFinishing} ")
                    .append("isDestroyed=${activity.isDestroyed}\n")
            }
        }
        // 用 shell 文件保存完整现场；logcat 的长输出可能被截断，且必须先于 finish。
        val commands = listOf(
            "mkdir -p $diagnosticsDirectory",
            "printf '%s' '${state}' > $diagnosticsDirectory/activity-state.txt",
            "dumpsys window > $diagnosticsDirectory/window.txt",
            "dumpsys input > $diagnosticsDirectory/input.txt",
            "dumpsys activity activities > $diagnosticsDirectory/activities.txt",
            "screencap -p $diagnosticsDirectory/screenshot.png"
        )
        commands.forEach { command ->
            try {
                val result = executeDiagnosticScript("$command; echo FLOWBUS_DIAGNOSTIC_STATUS=\$?")
                check(result.trimEnd().endsWith("FLOWBUS_DIAGNOSTIC_STATUS=0")) { result }
            } catch (error: Exception) {
                failures.add(error)
                println("FlowBus UI diagnostic failed: $command: $error")
            }
        }
        println("FlowBus UI failure diagnostics before cleanup: $diagnosticsDirectory")
        return failures
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
}

internal fun executeDiagnosticScript(script: String): String {
    // UiAutomation 按空白拆分参数，不解析引号；用 shell 内建 printf 解码，兼容 API 21。
    val encoded = StringBuilder()
    for (byte in script.toByteArray(StandardCharsets.UTF_8)) {
        val value = byte.toInt() and 0xff
        encoded.append('\\').append((value shr 6) and 7).append((value shr 3) and 7).append(value and 7)
    }
    return executeShellCommand(
        InstrumentationRegistry.getInstrumentation(),
        "sh -c eval\${IFS}\"\$(printf\${IFS}'$encoded')\""
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

    fun waitForActivityToBeDestroyed(activity: Activity, timeoutMs: Long = 5_000L) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val destroyed = AtomicBoolean(false)
        val endTime = SystemClock.uptimeMillis() + timeoutMs
        do {
            // PAUSED 后窗口可能仍在退出，不能据此开始下一次触摸。
            instrumentation.runOnMainSync { destroyed.set(activity.isDestroyed) }
            if (destroyed.get()) {
                return
            }
            Thread.sleep(50)
        } while (SystemClock.uptimeMillis() < endTime)

        error("${activity::class.java.simpleName} was not destroyed within $timeoutMs ms")
    }

    fun requireResumedActivity(activityClass: Class<out Activity>): Activity {
        val activity = currentActivity.get()
        check(activity != null && activityClass.isInstance(activity)) {
            "No resumed ${activityClass.simpleName} found"
        }
        return activity
    }
}
