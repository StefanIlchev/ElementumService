package service.lt2http.android.test

import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.rules.activityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import service.lt2http.android.BuildConfig
import service.lt2http.android.MainActivity
import java.io.File
import java.io.InputStream
import java.net.InetSocketAddress
import java.nio.channels.Channels
import java.nio.channels.SocketChannel
import java.nio.charset.StandardCharsets

@RunWith(Parameterized::class)
class ArgsTest(
	private val expected: String,
	private vararg val args: String
) {

	@Rule
	fun rule(): TestRule {
		val data = Uri.fromParts("version", BuildConfig.VERSION_NAME, args.joinToString("\u0000"))
		InstrumentationRegistry.getInstrumentation().grantRequestedPermissions()
		return activityScenarioRule<MainActivity>(Intent(Intent.ACTION_MAIN, data))
	}

	@Test
	fun test() {
		val actual = Channels.newInputStream(SocketChannel.open(InetSocketAddress(BuildConfig.LOCAL_PORT)))
			.use(InputStream::readAllBytes)
			.toString(StandardCharsets.ISO_8859_1)
		Assert.assertEquals(expected, actual)
	}

	companion object {

		private fun toExpectedTranslatePath(): String {
			val context = InstrumentationRegistry.getInstrumentation().targetContext
			val homeDir = File(context.filesDir, ".kodi")
			val xbmcDir = File(context.cacheDir, "apk/assets")
			return "${homeDir.path}/\u0000${xbmcDir.path}/"
		}

		@Parameterized.Parameters
		@JvmStatic
		fun data() = arrayOf(
			arrayOf(toExpectedTranslatePath(), arrayOf(BuildConfig.ARG_TRANSLATE_PATH)),
			arrayOf("PK\u0005\u0006${"\u0000".repeat(18)}", arrayOf("${BuildConfig.ARG_ADDON_INFO}=args.test"))
		)
	}
}
