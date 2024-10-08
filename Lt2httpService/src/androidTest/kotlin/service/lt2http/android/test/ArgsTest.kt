package service.lt2http.android.test

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.test.ext.junit.rules.activityScenarioRule
import org.junit.Assert
import org.junit.Assume
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import service.lt2http.android.BuildConfig
import service.lt2http.android.MainActivity
import java.io.File
import java.io.Reader
import java.net.InetSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets

@RunWith(Parameterized::class)
class ArgsTest(
	private val expected: String?,
	private vararg val args: String
) {

	@Rule
	fun rule(): TestRule {
		val data = Uri.fromParts("version", BuildConfig.VERSION_NAME, args.joinToString("\u0000"))
		val intent = Intent(Intent.ACTION_MAIN, data, targetContext, MainActivity::class.java)
		instrumentation.grantRequestedPermissions()
		return activityScenarioRule<MainActivity>(intent)
	}

	@Test
	fun test() {
		Assume.assumeNotNull(expected)
		val actual = Channels.newInputStream(SocketChannel.open(InetSocketAddress(BuildConfig.LOCAL_PORT)))
			.bufferedReader(StandardCharsets.ISO_8859_1)
			.use(Reader::readText)
		Assert.assertEquals(expected, actual)
	}

	companion object {

		private fun toExpectedTranslatePath(): String {
			val homeDir = File(targetContext.filesDir, ".kodi")
			val xbmcDir = File(targetContext.cacheDir, "apk/assets")
			return "${homeDir.path}/\u0000${xbmcDir.path}/"
		}

		private fun toExpectedAddonInfo() = "PK\u0005\u0006${"\u0000".repeat(18)}"

		@Parameterized.Parameters
		@JvmStatic
		fun data() = arrayOf(
			arrayOf(toExpectedTranslatePath(), arrayOf(BuildConfig.ARG_TRANSLATE_PATH)),
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
				arrayOf(null, arrayOf<String>())
			} else {
				arrayOf(toExpectedAddonInfo(), arrayOf("${BuildConfig.ARG_ADDON_INFO}=args.test"))
			}
		)
	}
}
