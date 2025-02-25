package service.elementum.android.test

import android.content.Intent
import android.os.Build
import org.junit.After
import org.junit.Assert
import org.junit.Assume
import org.junit.Before
import org.junit.Test
import service.elementum.android.BuildConfig
import java.time.Duration
import java.time.Instant

class UpdateTest {

	private var isInstalled = false

	@Before
	fun before() {
		val buildType = if (BuildConfig.DEBUG) "release" else "debug"
		val task = "assemble${buildType.replaceFirstChar { it.uppercaseChar() }}"
		val fileName = "${BuildConfig.PROJECT_NAME}-universal-$buildType-$VERSION_NAME.apk"
		val filePath = "build/outputs/apk/$buildType/$fileName"
		val build = "$task -p ${BuildConfig.PROJECT_NAME} -Dversion.name=\"$VERSION_NAME\""
		val install = "install -g -r $filePath"
		isInstalled = BuildConfig.DEBUG &&
				executeGradle(build) &&
				executeAdb(install)
	}

	@After
	fun after() {
		if (isInstalled) {
			val uninstall = "uninstall ${BuildConfig.APPLICATION_ID}"
			executeAdb(uninstall)
		}
	}

	@Test
	fun test() {
		Assume.assumeTrue(isInstalled)
		val manageExternalStorage = listOf(
			"test \$(getprop ro.build.version.sdk) -lt ${Build.VERSION_CODES.R}",
			"appops set ${BuildConfig.APPLICATION_ID} MANAGE_EXTERNAL_STORAGE allow"
		).joinToString(" || ", "(", ")")
		val data = "version:${BuildConfig.VERSION_NAME}"
		val start = listOf(
			"appops set ${BuildConfig.APPLICATION_ID} REQUEST_INSTALL_PACKAGES allow",
			manageExternalStorage,
			"am force-stop ${BuildConfig.APPLICATION_ID}",
			"am start -W -a ${Intent.ACTION_MAIN} -d $data ${BuildConfig.APPLICATION_ID}"
		).joinToString(" && ", "shell ")
		Assert.assertTrue(executeAdb(start))
		assertUpdate()
	}

	companion object {

		private const val VERSION_NAME = "update.test"

		private fun toCheck(
			versionName: String
		) = listOf(
			"dumpsys package ${BuildConfig.APPLICATION_ID}",
			"grep 'versionName=$versionName'"
		).joinToString(" | ", "shell ")

		private fun assertUpdate() {
			val check = toCheck(VERSION_NAME)
			val range = Instant.now().let { it..it + Duration.ofMinutes(10L) }
			Assume.assumeFalse(BuildConfig.REPO_URL.isEmpty())
			while (executeAdb(check)) {
				Assert.assertTrue(Instant.now() in range)
				Thread.sleep(1_000L)
			}
			Assert.assertTrue(executeAdb(toCheck(BuildConfig.VERSION_NAME)))
		}
	}
}
