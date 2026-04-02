plugins {
    id("com.android.application") apply false
    id("com.android.library") apply false
    kotlin("android") apply false
    kotlin("jvm") apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
    base
}
