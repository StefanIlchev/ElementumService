package ilchev.stefan.binarywrapper

import android.content.Context
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.FileNotFoundException
import java.util.Objects
import java.util.Scanner
import javax.security.auth.Destroyable

abstract class BaseDaemonInvoker(
	context: Context
) : () -> Long, Destroyable {

	private val assetManager = context.assets

	private val assetsMarker = File(context.codeCacheDir, BuildConfig.LIBRARY_PACKAGE_NAME)

	private val bin = File(context.applicationInfo.nativeLibraryDir)

	private var attempt = -1

	@Volatile
	private var isDestroyed = false

	private var process: Process? = null

	private val destroyProcessRunnable = Runnable {
		process?.apply {
			process = null
			destroy()
		}
	}

	private val clearProcessRunnable = Runnable {
		process = null
	}

	protected open val subprocessAssets
		get() = emptyMap<String, File>()

	protected abstract val subprocessCmd: List<String>

	protected open val subprocessEnv
		get() = emptyMap<String, String>()

	protected open val subprocessExitValuesEnd
		get() = emptySet<Int>()

	protected open val subprocessExitValuesSkip
		get() = emptySet<Int>()

	protected open val subprocessExitValuesStart
		get() = emptySet<Int>()

	protected open val subprocessRetriesCount
		get() = 0

	protected open val subprocessRetryDelay
		get() = 0L

	protected open val subprocessTag
		get() = "Subprocess"

	private val builder by lazy {
		ProcessBuilder(subprocessCmd).apply {
			directory(bin)
			redirectErrorStream(true)
			environment() += subprocessEnv
		}
	}

	override fun hashCode() = Objects.hash(
		subprocessAssets,
		subprocessCmd,
		subprocessEnv,
		subprocessExitValuesEnd,
		subprocessExitValuesSkip,
		subprocessExitValuesStart,
		subprocessRetriesCount,
		subprocessRetryDelay,
		subprocessTag
	)

	override fun equals(
		other: Any?
	) = other === this || other is BaseDaemonInvoker &&
			other.javaClass == javaClass &&
			other.subprocessAssets == subprocessAssets &&
			other.subprocessCmd == subprocessCmd &&
			other.subprocessEnv == subprocessEnv &&
			other.subprocessExitValuesEnd == subprocessExitValuesEnd &&
			other.subprocessExitValuesSkip == subprocessExitValuesSkip &&
			other.subprocessExitValuesStart == subprocessExitValuesStart &&
			other.subprocessRetriesCount == subprocessRetriesCount &&
			other.subprocessRetryDelay == subprocessRetryDelay &&
			other.subprocessTag == subprocessTag

	override fun isDestroyed() = isDestroyed

	override fun destroy() {
		isDestroyed = true
		if (Looper.myLooper() == mainHandler.looper) {
			destroyProcessRunnable.run()
		} else {
			mainHandler.post(destroyProcessRunnable)
		}
	}

	private fun extract(src: String, dst: File) {
		if (dst.exists()) {
			if (assetsMarker.exists() || isDestroyed) return
			dst.deleteRecursively()
		} else {
			assetsMarker.delete()
		}
		val parent = dst.parent
		val deque = ArrayDeque<String>()
		var node: String? = src
		var name: String? = dst.name
		while (!isDestroyed) {
			node ?: break
			name ?: break
			for (child in assetManager.list(node) ?: emptyArray()) {
				deque += "$node/$child"
			}
			val file = File(parent, name)
			try {
				assetManager.open(node).use { file.outputStream().use(it::copyTo) }
			} catch (ignored: FileNotFoundException) {
				file.mkdirs()
			}
			node = deque.removeFirstOrNull()
			name = node
		}
	}

	private fun extract() {
		for ((key, value) in subprocessAssets) {
			extract(key, value)
			if (isDestroyed) return
		}
		assetsMarker.mkdirs()
	}

	private fun toSetProcessRunnable(
		value: Process
	) = Runnable {
		if (isDestroyed) {
			value.destroy()
		} else {
			process = value
		}
	}

	private fun execute(): Long {
		if (attempt < 0) {
			extract()
			attempt = 0
		}
		if (isDestroyed) return -1L
		val process = builder.start()
		if (!mainHandler.post(toSetProcessRunnable(process))) {
			process.destroy()
			return -1L
		}
		Scanner(process.inputStream).use {
			while (it.hasNextLine()) {
				val line = it.nextLine()
				Log.v(subprocessTag, line)
			}
		}
		val exitValue = process.waitFor()
		Log.v(TAG, "SUBPROCESS_EXIT_VALUE = $exitValue")
		if (isDestroyed ||
			!mainHandler.post(clearProcessRunnable) ||
			exitValue in subprocessExitValuesEnd
		) return -1L
		if (exitValue in subprocessExitValuesSkip) {
			return subprocessRetryDelay
		}
		if (exitValue in subprocessExitValuesStart) {
			attempt = 0
		} else if (++attempt > subprocessRetriesCount) {
			return -1L
		}
		return subprocessRetryDelay
	}

	override fun invoke() = try {
		execute()
	} catch (t: Throwable) {
		Log.w(TAG, t)
		-1L
	}

	companion object {

		private const val TAG = "BaseDaemonInvoker"
	}
}
