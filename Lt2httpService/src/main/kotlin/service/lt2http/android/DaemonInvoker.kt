package service.lt2http.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import ilchev.stefan.binarywrapper.BaseDaemonInvoker
import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.ServerSocketChannel

class DaemonInvoker(
	context: Context,
	private val mainHandler: Handler,
	vararg subprocessArgs: String
) : BaseDaemonInvoker(context, mainHandler) {

	private val homeDir = File(context.filesDir, ".kodi")

	private val xbmcDir = File(context.cacheDir, "apk/assets")

	private val addonDir = File(homeDir, "addons/${BuildConfig.ADDON_ID}")

	private val lockfile = File(addonDir, ".lockfile")

	override val subprocessAssets = mapOf(BuildConfig.ADDON_ID to addonDir)

	override val subprocessCmd = listOf("./liblt2http.so", *subprocessArgs)

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

	private fun send(port: Int, data: String): Long {
		if (isDestroyed) return -1L
		ServerSocketChannel.open().use { channel ->
			if (!mainHandler.post(toSetChannelRunnable(channel))) return -1L
			channel.bind(InetSocketAddress(port))
			channel.accept().use { it.write(ByteBuffer.wrap(data.toByteArray())) }
			return if (isDestroyed ||
				!mainHandler.post(clearChannelRunnable)
			) -1L else 1_000L
		}
	}

	private val invoker = if (subprocessArgs.contains(BuildConfig.ARG_TRANSLATE_PATH)) {
		val regex = """-localPort=(\d+)""".toRegex()
		val port = subprocessArgs.firstNotNullOfOrNull {
			regex.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull()
		} ?: BuildConfig.LOCAL_PORT
		val data = "${homeDir.path}/\u0000${xbmcDir.path}/"
		{
			try {
				send(port, data)
			} catch (ignored: Throwable) {
				-1L
			}
		}
	} else {
		{
			lockfile.delete()
			super.invoke()
		}
	}

	override fun invoke() = invoker()
}
