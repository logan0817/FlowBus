plugins {
    id("com.android.application") apply false
    id("com.android.library") apply false
    alias(libs.plugins.vanniktech.maven.publish) apply false
    base
}
