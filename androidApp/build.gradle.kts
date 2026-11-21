import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    jacoco
    alias(libs.plugins.ktlint)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}

fun signingValue(key: String): String? =
    providers.gradleProperty(key).orNull ?: localProps.getProperty(key)

/** Git facts for the version string; a source zip without .git still builds. */
fun git(vararg args: String): String? = runCatching {
    val process = ProcessBuilder(listOf("git") + args)
        .directory(rootProject.projectDir)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    val text = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() != 0) null else text.ifBlank { null }
}.getOrNull()

val commitCount = git("rev-list", "--count", "HEAD")?.toIntOrNull() ?: 1
val releaseTag = git("describe", "--tags", "--abbrev=0", "--match", "android-v*")
    ?.removePrefix("android-v")
// Tagged builds read as their tag; anything else counts commits so no two builds match.
val appVersionName = "${releaseTag ?: "0.1"}.$commitCount"

val releaseStoreFile = signingValue("MM_STORE_FILE")
val releaseStorePassword = signingValue("MM_STORE_PASSWORD")
val releaseKeyAlias = signingValue("MM_KEY_ALIAS")
val releaseKeyPassword = signingValue("MM_KEY_PASSWORD")

android {
    namespace = "dev.mediamanager.mobile"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.mediamanager.mobile"
        minSdk = 26
        targetSdk = 36
        versionCode = 10_000 + commitCount
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (releaseStoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/LICENSE*")
        jniLibs.useLegacyPackaging = true
    }

    splits {
        abi {
            isEnable = true
            reset()
            // Phones are arm64; -PmmEmulatorAbi adds x86_64 for emulator runs.
            if (providers.gradleProperty("mmEmulatorAbi").isPresent) {
                include("arm64-v8a", "x86_64")
            } else {
                include("arm64-v8a")
            }
            isUniversalApk = false
        }
    }
}

/**
 * youtubedl-android 0.18.1 pins 2020-era Jackson and Apache Commons. Those bytes ship
 * inside the APK, so its Dependabot alerts are real, unlike the build-toolchain ones.
 * Jackson 2.x and Commons 2.x are binary compatible across minors.
 */
ktlint {
    // Pinned: the plugin's default ktlint version changes between patch releases.
    version = "1.8.0"
    android.set(true)
    // Gradle scripts keep their own DSL formatting; ktlint owns the Kotlin sources.
    filter {
        exclude("**/*.kts")
    }
}

/**
 * ktlint 1.8's own CLI resolves logback 1.3.16. It is a developer tool, never in the
 * APK, but Dependabot rightly flags it; force the patched 1.5 line.
 */
configurations.matching { it.name == "ktlint" }.configureEach {
    resolutionStrategy.force(
        "ch.qos.logback:logback-core:1.5.34",
        "ch.qos.logback:logback-classic:1.5.34",
    )
}

configurations.configureEach {
    resolutionStrategy {
        force(
            "com.fasterxml.jackson.core:jackson-core:2.18.11",
            "com.fasterxml.jackson.core:jackson-databind:2.18.11",
            "com.fasterxml.jackson.core:jackson-annotations:2.18.11",
            "commons-io:commons-io:2.22.0",
            "org.apache.commons:commons-compress:1.28.0",
            "com.google.guava:guava:33.8.0-android",
        )
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.datastore.preferences)

    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)

    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)
    implementation(libs.newpipe.extractor)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)

}

/**
 * Unit-test coverage: `make coverage` (or ./gradlew coverageReport).
 * Robolectric tests run on the JVM, so JaCoCo instruments them like plain tests.
 */
tasks.withType<Test>().configureEach {
    configure<JacocoTaskExtension> {
        isIncludeNoLocationClasses = true
        excludes = listOf("jdk.internal.*")
    }
}

val unitTestTask = "testDebugUnitTest"
val buildDirectory = layout.buildDirectory

tasks.register<JacocoReport>("coverageReport") {
    // AGP 9 creates test tasks lazily, so this must stay a name, not tasks.named().
    dependsOn(unitTestTask)
    group = "verification"
    description = "Unit-test coverage for the debug variant."

    val classExcludes = listOf(
        "**/R.class",
        "**/R\$*.class",
        "**/BuildConfig.*",
        "**/Manifest*.*",
        "**/*_Factory.*",
        "**/*_Impl.*",
        "**/ComposableSingletons*.*",
    )

    classDirectories.setFrom(
        files(
            // AGP 9 compiles Kotlin through its built-in kotlinc task.
            buildDirectory.dir("intermediates/built_in_kotlinc/debug/compileDebugKotlin/classes"),
            buildDirectory.dir("intermediates/javac/debug/compileDebugJavaWithJavac/classes"),
        ).asFileTree.matching { exclude(classExcludes) }
    )
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    // Read the path the test task actually wrote instead of guessing it.
    executionData.setFrom(
        provider {
            tasks.named<Test>(unitTestTask).get()
                .extensions.getByType<JacocoTaskExtension>().destinationFile
                ?: buildDirectory.file("jacoco/$unitTestTask.exec").get().asFile
        },
    )

    reports {
        html.required.set(true)
        xml.required.set(true)
        csv.required.set(false)
        html.outputLocation.set(buildDirectory.dir("reports/coverage"))
        xml.outputLocation.set(buildDirectory.file("reports/coverage/coverageReport.xml"))
    }
}
