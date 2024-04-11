package service.elementum.android.test

import android.content.Intent
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import service.elementum.android.BuildConfig
import java.time.Duration
import java.time.Instant

class UpdateTest {

	@Before
	fun before() {
		val buildType = if (BuildConfig.DEBUG) "release" else "debug"
		val task = "assemble${buildType.replaceFirstChar { it.uppercaseChar() }}"
		val fileName = "${BuildConfig.PROJECT_NAME}-universal-$buildType-$VERSION_NAME.apk"
		val filePath = "build/outputs/apk/$buildType/$fileName"
		val build = "$task -p ${BuildConfig.PROJECT_NAME} -Dversion.name=\"$VERSION_NAME\""
		val install = "install -g $filePath"
		Assert.assertTrue(executeGradle(build))
		Assert.assertTrue(executeAdb(install))
	}

	@After
	fun after() {
		val uninstall = "uninstall ${BuildConfig.APPLICATION_ID}"
		Assert.assertTrue(executeAdb(uninstall))
	}

	@Test
	fun test() {
		val data = "version:${BuildConfig.VERSION_NAME}"
		val start = listOf(
			"appops set --uid ${BuildConfig.APPLICATION_ID} REQUEST_INSTALL_PACKAGES allow",
			"appops set --uid ${BuildConfig.APPLICATION_ID} MANAGE_EXTERNAL_STORAGE allow",
			"am start -W -S -a ${Intent.ACTION_MAIN} -d $data ${BuildConfig.APPLICATION_ID}"
		).joinToString("; ", "shell ")
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
			while (executeAdb(check)) {
				Assert.assertTrue(Instant.now() in range)
				Thread.sleep(1_000L)
			}
			Assert.assertTrue(executeAdb(toCheck(BuildConfig.VERSION_NAME)))
		}
	}
}
