package service.lt2http.android

import android.content.Context
import android.os.Environment
import android.os.Handler
import ilchev.stefan.binarywrapper.BaseDaemonInvoker
import java.io.File
import java.nio.file.Paths
import java.util.Properties
import kotlin.io.path.writeLines

class DaemonInvoker(
	context: Context,
	mainHandler: Handler,
	vararg subprocessArgs: String
) : BaseDaemonInvoker(context, mainHandler) {

	private val addonDir = File(context.filesDir, ".kodi/addons/${BuildConfig.ADDON_ID}")

	override val subprocessAssets = mapOf(BuildConfig.ADDON_ID to addonDir)

	override val subprocessCmd = listOf("./liblt2http.so", *subprocessArgs)

	override val subprocessEnv = mapOf("LD_LIBRARY_PATH" to context.applicationInfo.nativeLibraryDir)

	override val subprocessExitValuesEnd = setOf(0, 1, 247)

	override val subprocessExitValuesSkip = setOf(255)

	override val subprocessExitValuesStart = setOf(5)

	override val subprocessRetriesCount = 3

	override val subprocessRetryDelay = 5_000L

	override val subprocessTag = BuildConfig.ADDON_ID

	private val lockfile = File(addonDir, ".lockfile")

	private val dataDefault = context.getExternalFilesDir(null)!!.path.replace(context.packageName, BuildConfig.KODI_ID)

	@Suppress("deprecation", "KotlinRedundantDiagnosticSuppress")
	private fun loadData(): String {
		val properties = Properties()
		val xbmcEnvFile = File(Environment.getExternalStorageDirectory(), "xbmc_env.properties")
		properties.setProperty(KEY_DATA, dataDefault)
		xbmcEnvFile.takeIf(File::isFile)?.bufferedReader()?.use(properties::load)
		return properties.getProperty(KEY_DATA)
	}

	private fun writeData() {
		val data = loadData()
		val dataDir = File(data.replace(BuildConfig.KODI_DATA_DIR, BuildConfig.DATA_DIR))
		val dataPath = Paths.get(dataDir.path, BuildConfig.PROJECT_NAME)
		dataDir.mkdirs()
		dataPath.writeLines(listOf(data, addonDir.parent))
	}

	override fun invoke() = if (subprocessCmd.size < 2) {
		try {
			writeData()
		} catch (ignored: Throwable) {
		}
		-1L
	} else {
		lockfile.delete()
		super.invoke()
	}

	companion object {

		private const val KEY_DATA = "xbmc.data"
	}
}
