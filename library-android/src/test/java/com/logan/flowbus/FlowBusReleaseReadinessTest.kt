package com.logan.flowbus

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FlowBusReleaseReadinessTest {

    @Test
    fun `sample release build enables minify for consumer shrink validation`() {
        val appBuild = File("../app/build.gradle.kts").readText()

        assertTrue(appBuild.contains("getByName(\"release\")"))
        assertTrue(appBuild.contains("isMinifyEnabled = true"))
        assertTrue(appBuild.contains("testBuildType = \"release\""))
        assertTrue(appBuild.contains("signingConfig = signingConfigs.getByName(\"debug\")"))
        assertTrue(appBuild.contains("testProguardFiles(\"proguard-android-test.pro\")"))
        val appRules = File("../app/proguard-rules.pro").readText()
        assertTrue("Target app ProGuard rules must not keep all AndroidX classes.", !appRules.contains("androidx.**"))
        assertTrue("Target app ProGuard rules must not keep all Kotlin classes.", !appRules.contains("kotlin.**"))
        assertTrue(
            "Target app ProGuard rules must not keep all Guava listenablefuture classes.",
            !appRules.contains("com.google.common.util.concurrent.**")
        )
        assertTrue(appRules.contains("androidx.tracing.Trace"))
        assertTrue(appRules.contains("kotlin.LazyKt"))
        assertTrue(appRules.contains("kotlin.collections.**"))
        assertTrue(appRules.contains("kotlin.text.**"))
        assertTrue(appRules.contains("kotlin.jvm.internal.Reflection"))
        assertTrue(appRules.contains("kotlin.reflect.KClass"))
        assertTrue(appRules.contains("com.google.common.util.concurrent.ListenableFuture"))
        assertTrue(appRules.contains("**.R$*"))
        assertTrue(appRules.contains("com.logan.flowbusapp.**Activity"))
        val activityRule = File("../app/src/androidTest/java/com/logan/flowbusapp/testutil/ShellLaunchedActivityRule.kt")
            .readText()
        assertTrue(activityRule.contains("launchTargetApp(instrumentation, targetContext.packageName)"))
        assertTrue(activityRule.contains("packageName/.MainActivity"))
        assertTrue(activityRule.contains("targetContext.startActivity"))
        assertTrue(activityRule.contains("waitForResumedActivity"))
        assertTrue(
            "Release instrumentation launch must not depend on global idle from startActivitySync.",
            !activityRule.contains("startActivitySync")
        )
        assertTrue("Release instrumentation must only shell-launch the exported launcher activity.", !activityRule.contains("packageName/\${activityClass.name}"))
        assertTrue(!activityRule.contains("ActivityScenario.launchActivityForResult"))
        assertTrue(
            "Release instrumentation should use AndroidX Test bootstrap instead of a hand-rolled PendingIntent launcher.",
            !activityRule.contains("PendingIntent.getActivity")
        )
        val releaseManifest = File("../app/src/release/AndroidManifest.xml")
        assertTrue(
            "Release manifest must not export sample-only activities for instrumentation startup.",
            !releaseManifest.exists() || !releaseManifest.readText().contains("android:exported=\"true\"")
        )
        val androidTestRules = File("../app/proguard-android-test.pro").readText()
        assertTrue(androidTestRules.contains("javax.lang.model.element.Modifier"))
        assertTrue(androidTestRules.contains("androidx.tracing"))
    }

    @Test
    fun `maven local verification checks public dependency metadata`() {
        val releaseVerification = File("../gradle/release-verification.gradle.kts").readText()

        listOf(
            "pom = corePom",
            "module = coreModule",
            "pom = androidPom",
            "module = androidModule",
            "org.jetbrains.kotlinx",
            "kotlinx-coroutines-core",
            "androidx.lifecycle",
            "lifecycle-runtime-ktx",
            "lifecycle-viewmodel-ktx",
            "compile",
            "prepareMavenLocalConsumer",
            "verifyMavenLocalConsumer",
            "prepareMavenLocalCoreConsumer",
            "verifyMavenLocalCoreConsumer",
            "POM_LICENSE_NAME",
            "POM_DEVELOPER_ID",
            "android.useAndroidX=true",
            "kotlinOptions",
            "collectEvent",
            "onEvent",
            "postScopedEvent"
        ).forEach { expected ->
            assertTrue("Missing release metadata gate: $expected", releaseVerification.contains(expected))
        }
    }

    @Test
    fun `ci runs maven local artifact and minified consumer gates`() {
        val ci = File("../.github/workflows/ci.yml").readText()

        assertTrue(ci.contains("permissions:"))
        assertTrue(ci.contains("contents: read"))
        assertTrue(ci.contains("cache-read-only: false"))
        assertTrue(ci.contains("verifyMavenLocalArtifacts"))
        assertTrue(ci.contains("verifyMavenLocalConsumer"))
        assertTrue(ci.contains("verifyMavenLocalCoreConsumer"))
        assertTrue(ci.contains(":app:connectedReleaseAndroidTest"))
    }

    @Test
    fun `release checklist documents shrink and dependency metadata gates`() {
        val checklist = File("../docs/release-checklist.md").readText()
        val englishChecklist = File("../docs/en/release-checklist.md").readText()
        val releaseNotes = File("../docs/release-notes-1.0.6.md").readText()
        val englishReleaseNotes = File("../docs/en/release-notes-1.0.6.md").readText()
        val changelog = File("../CHANGELOG.md").readText()

        listOf(checklist to "依赖", englishChecklist to "dependency").forEach { (text, dependencyText) ->
            assertTrue(text.contains("R8"))
            assertTrue(text.contains("POM"))
            assertTrue(text.contains("module metadata"))
            assertTrue(text.contains("verifyMavenLocalConsumer"))
            assertTrue(text.contains("verifyMavenLocalCoreConsumer"))
            assertTrue(text.contains(":app:connectedReleaseAndroidTest"))
            assertTrue("Release notes/checklist must not point release validation at app debug instrumentation.", !text.contains(":app:connectedDebugAndroidTest"))
            assertTrue(text.contains(dependencyText))
        }
        listOf(releaseNotes, englishReleaseNotes).forEach { text ->
            assertTrue(text.contains(":app:connectedReleaseAndroidTest"))
            assertTrue("Release notes must not point release validation at app debug instrumentation.", !text.contains(":app:connectedDebugAndroidTest"))
        }
        listOf("verifyMavenLocalCoreConsumer", "verifyMavenLocalConsumer", "license", "developer").forEach { expected ->
            assertTrue("CHANGELOG must summarize release gate: $expected", changelog.contains(expected))
        }
    }

    @Test
    fun `root readme keeps user usage path before release maintenance details`() {
        val readme = File("../README.md").readText()
        val englishReadme = File("../README_EN.md").readText()

        listOf(
            "## 功能点",
            "## 直接安装",
            "## 5 分钟上手",
            "## 常用场景",
            "## API 选择速记",
            "## 兼容与发布状态"
        ).forEach { expected ->
            assertTrue("Root README is missing user-facing section: $expected", readme.contains(expected))
        }
        assertTrue("Root README should not duplicate repository structure.", readme.headingCount("## 仓库结构") <= 1)
        readme.assertSectionOrder("## 功能点", "## 直接安装")
        readme.assertSectionOrder("## 直接安装", "## 5 分钟上手")
        readme.assertSectionOrder("## 5 分钟上手", "## 常用场景")
        readme.assertSectionOrder("## 常用场景", "## API 选择速记")
        readme.assertSectionOrder("## API 选择速记", "## 兼容与发布状态")
        listOf(":flowbus-core:test", ":library-android:testDebugUnitTest", ":library-android:lintRelease").forEach { expected ->
            assertTrue("Root README release gate should match CI/checklist task: $expected", readme.contains(expected))
        }

        listOf(
            "## Features",
            "## Install",
            "## 5-minute quick start",
            "## Common scenarios",
            "## API selection cheat sheet",
            "## Compatibility and release status"
        ).forEach { expected ->
            assertTrue("English root README is missing user-facing section: $expected", englishReadme.contains(expected))
        }
        assertTrue("English root README should not duplicate repository layout.", englishReadme.headingCount("## Repository layout") <= 1)
        englishReadme.assertSectionOrder("## Features", "## Install")
        englishReadme.assertSectionOrder("## Install", "## 5-minute quick start")
        englishReadme.assertSectionOrder("## 5-minute quick start", "## Common scenarios")
        englishReadme.assertSectionOrder("## Common scenarios", "## API selection cheat sheet")
        englishReadme.assertSectionOrder("## API selection cheat sheet", "## Compatibility and release status")
        listOf(":flowbus-core:test", ":library-android:testDebugUnitTest", ":library-android:lintRelease").forEach { expected ->
            assertTrue("English root README release gate should match CI/checklist task: $expected", englishReadme.contains(expected))
        }
    }

    @Test
    fun `release sources do not keep debug apk artifacts`() {
        val gitignore = File("../.gitignore").readText()

        assertTrue("Debug APK artifacts must stay ignored.", !gitignore.contains("!/app/apk/app-debug.apk"))
        assertTrue("Debug APK artifact must not be kept in release sources.", !File("../app/apk/app-debug.apk").exists())
    }

    private fun String.headingCount(heading: String): Int {
        return lineSequence().count { it == heading }
    }

    private fun String.assertSectionOrder(before: String, after: String) {
        val beforeIndex = indexOf(before)
        val afterIndex = indexOf(after)

        assertTrue("Missing README section: $before", beforeIndex >= 0)
        assertTrue("Missing README section: $after", afterIndex >= 0)
        assertTrue("README section `$before` must appear before `$after`.", beforeIndex < afterIndex)
    }
}
