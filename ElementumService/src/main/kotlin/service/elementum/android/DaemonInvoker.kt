package service.elementum.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import ilchev.stefan.binarywrapper.BaseDaemonInvoker
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DaemonInvoker(
	context: Context,
	private val mainHandler: Handler,
	vararg subprocessArgs: String
) : BaseDaemonInvoker(context, mainHandler) {

	private val homeDir = File(context.filesDir, ".kodi")

	private val xbmcDir = File(context.cacheDir, "apk/assets")

	private val addonsDir = File(homeDir, "addons")

	private val addonDir = File(addonsDir, BuildConfig.ADDON_ID)

	private val lockfile = File(addonDir, ".lockfile")

	override val subprocessAssets = mapOf(BuildConfig.ADDON_ID to addonDir)

	override val subprocessCmd = listOf("./libelementum.so", *subprocessArgs)

	override val subprocessEnv = mapOf("LD_LIBRARY_PATH" to context.applicationInfo.nativeLibraryDir)

	override val subprocessExitValuesEnd = setOf(0, 1, 247)

	override val subprocessExitValuesSkip = setOf(255)

	override val subprocessExitValuesStart = setOf(5)

	override val subprocessRetriesCount = 3

	override val subprocessRetryDelay = 5_000L

	override val subprocessTag = BuildConfig.ADDON_ID

	private var channel: ServerSocketChannel? = null

	private val closeChannelRunnable = Runnable {
		channel?.apply {
			channel = null
			try {
				close()
			} catch (ignored: Throwable) {
			}
		}
	}

	private val clearChannelRunnable = Runnable {
		channel = null
	}

	override fun destroy() {
		super.destroy()
		if (Looper.myLooper() == mainHandler.looper) {
			closeChannelRunnable.run()
		} else {
			mainHandler.post(closeChannelRunnable)
		}
	}

	private fun toSetChannelRunnable(
		value: ServerSocketChannel
	) = Runnable {
		if (isDestroyed) {
			try {
				value.close()
			} catch (ignored: Throwable) {
			}
		} else {
			channel = value
		}
	}

	private fun send(port: Int, data: ByteArray): Long {
		if (isDestroyed) return -1L
		ServerSocketChannel.open().use { channel ->
			if (!mainHandler.post(toSetChannelRunnable(channel))) return -1L
			channel.bind(InetSocketAddress(port))
			channel.accept().use { it.write(ByteBuffer.wrap(data)) }
			return if (isDestroyed ||
				!mainHandler.post(clearChannelRunnable)
			) -1L else 1_000L
		}
	}

	private fun toSendInvoker(subprocessArgs: Array<out String>, data: ByteArray): () -> Long {
		val regex = """${BuildConfig.ARG_LOCAL_PORT}=(\d+)""".toRegex()
		val port = subprocessArgs.firstNotNullOfOrNull {
			regex.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull()
		} ?: BuildConfig.LOCAL_PORT
		return {
			try {
				send(port, data)
			} catch (ignored: Throwable) {
				-1L
			}
		}
	}

	private val invoker by lazy {
		"""${BuildConfig.ARG_ADDON_INFO}=(\S+)""".toRegex().let { regex ->
			subprocessArgs.firstNotNullOfOrNull { regex.matchEntire(it)?.groupValues?.get(1) }?.let { addonId ->
				ByteArrayOutputStream().use { data ->
					ZipOutputStream(data).use { zip ->
						for (file in File(addonsDir, addonId).walkTopDown()) {
							if (file.isDirectory) {
								zip.putNextEntry(ZipEntry("${file.relativeTo(addonsDir).path}/"))
							} else if (file.isFile) {
								zip.putNextEntry(ZipEntry(file.relativeTo(addonsDir).path))
								file.inputStream().use { it.copyTo(zip) }
							}
						}
					}
					toSendInvoker(subprocessArgs, data.toByteArray())
				}
			}
		} ?: if (subprocessArgs.contains(BuildConfig.ARG_TRANSLATE_PATH)) {
			val data = "${homeDir.path}/\u0000${xbmcDir.path}/"
			toSendInvoker(subprocessArgs, data.toByteArray())
		} else {
			{
				lockfile.delete()
				super.invoke()
			}
		}
	}

	override fun invoke() = invoker()
}
