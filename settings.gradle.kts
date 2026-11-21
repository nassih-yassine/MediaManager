// Keep the pre-push gate installed for every clone: the hook lives in the repo
// (.githooks/pre-push), git only has to be pointed at it. Runs on any Gradle
// invocation, so `make build` is enough. Set MM_NO_HOOKS=1 to opt out.
fun gitConfig(vararg args: String): String? = runCatching {
    val process = ProcessBuilder(listOf("git") + args)
        .directory(settingsDir)
        .redirectErrorStream(true)
        .start()
    val output = process.inputStream.bufferedReader().readText().trim()
    if (process.waitFor() == 0) output else null
}.getOrNull()

if (System.getenv("MM_NO_HOOKS") != "1" && java.io.File(settingsDir, ".githooks/pre-push").exists()) {
    val hooksPath = gitConfig("config", "--local", "--get", "core.hooksPath")
    if (hooksPath != ".githooks") {
        gitConfig("config", "--local", "core.hooksPath", ".githooks")
            ?.let { logger.lifecycle("git hooks installed: core.hooksPath = .githooks") }
    }
}

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "MediaManager"
include(":androidApp")
