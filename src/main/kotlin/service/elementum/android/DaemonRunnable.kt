package service.elementum.android

import android.content.Context
import android.os.Environment
import android.os.Handler
import ilchev.stefan.binarywrapper.BaseDaemonRunnable
import java.io.File
import java.nio.file.Paths
import java.util.Properties
import kotlin.io.path.writeLines

class DaemonRunnable(
	context: Context,
	mainHandler: Handler,
	vararg subprocessArgs: String
) : BaseDaemonRunnable(context, mainHandler) {

	override val subprocessAssets: Map<String, File>

	override val subprocessCmd: List<String>

	override val subprocessEnv = mapOf("LD_LIBRARY_PATH" to context.applicationInfo.nativeLibraryDir)

	override val subprocessExitValuesEnd = setOf(0, 1, 247)

	override val subprocessExitValuesSkip = setOf(255)

	override val subprocessExitValuesStart = setOf(5)

	override val subprocessRetriesCount = 3

	override val subprocessRetryDelay = 5_000L

	override val subprocessTag = BuildConfig.ADDON_ID

	private val lockfile: File

	private val dataDefault: String

	private val dataReplacement: String

	init {
		val filesDir = context.filesDir
		val addonDir = File(filesDir, ".kodi/addons/${BuildConfig.ADDON_ID}")
		subprocessAssets = mapOf(BuildConfig.ADDON_ID to addonDir)
		subprocessCmd = listOf("./libelementum.so", *subprocessArgs)
		lockfile = File(addonDir, ".lockfile")
		dataDefault = context.getExternalFilesDir(null)!!.path.replace(context.packageName, BuildConfig.KODI_ID)
		dataReplacement = filesDir.path
	}

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
		dataPath.writeLines(listOf(data, dataReplacement))
	}

	override fun run() {
		try {
			writeData()
		} catch (ignored: Throwable) {
		}
		lockfile.delete()
		super.run()
	}

	companion object {

		private const val KEY_DATA = "xbmc.data"
	}
}
