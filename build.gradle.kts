buildscript {
    // AGP's toolchain ships guava 33.4.0-jre; force the patched line on the build
    // classpath too so the dependency graph has no vulnerable copy anywhere.
    configurations.configureEach {
        resolutionStrategy.force("com.google.guava:guava:33.8.0-jre")
    }
}

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
}
