package service.lt2http.android.test

import service.lt2http.android.BuildConfig
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

fun executeAdbShell(
	args: String
) = executeGradle("adbShell -p ${BuildConfig.PROJECT_NAME} -Dadb.shell.args=\"$args\"")
