package service.elementum.android.test

import service.elementum.android.BuildConfig
import java.io.File

private val isWindows = "windows" in (System.getProperty("os.name")?.lowercase() ?: "")

fun executeGradle(
	args: String
) = if (isWindows) {
	ProcessBuilder("cmd", "/c", "gradlew $args")
} else {
	ProcessBuilder("sh", "-c", "sh gradlew $args")
}.directory(File("..")).inheritIO().start().waitFor() == 0

fun executeAdb(
	args: String
) = executeGradle("adb -p ${BuildConfig.PROJECT_NAME} -Dadb.args=\"$args\"")
