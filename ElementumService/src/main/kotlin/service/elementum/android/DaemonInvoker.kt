package service.elementum.android

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

	private val addonDir = File(context.filesDir, ".kodi/addons/${BuildConfig.ADDON_ID}")

	override val subprocessAssets = mapOf(BuildConfig.ADDON_ID to addonDir)

	override val subprocessCmd = listOf("./libelementum.so", *subprocessArgs)

	override val subprocessEnv = mapOf("LD_LIBRARY_PATH" to context.applicationInfo.nativeLibraryDir)

	override val subprocessExitValuesEnd = setOf(0, 1, 247)

	override val subprocessExitValuesSkip = setOf(255)

	override val subprocessExitValuesStart = setOf(5)

	override val subprocessRetriesCount = 3

	override val subprocessRetryDelay = 5_000L

	override val subprocessTag = BuildConfig.ADDON_ID

	private val lockfile = File(addonDir, ".lockfile")

	private val homeDir = File(context.filesDir, ".kodi")

	private val xbmcDir = File(context.cacheDir, "apk/assets")

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

	private fun translatePath() {
		if (isDestroyed) return
		val regex = "-localPort=(\\d+)".toRegex()
		val port = subprocessCmd.firstNotNullOfOrNull {
			regex.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull()
		} ?: 65220
		ServerSocketChannel.open().bind(InetSocketAddress(port)).use { channel ->
			if (mainHandler.post(toSetChannelRunnable(channel))) {
				channel.accept().use {
					it.write(ByteBuffer.wrap("${homeDir.path}/\u0000${xbmcDir.path}/".toByteArray()))
				}
				if (!isDestroyed) {
					mainHandler.post(clearChannelRunnable)
				}
			}
		}
	}

	override fun invoke(): Long {
		if (subprocessCmd.contains(BuildConfig.ARG_TRANSLATE_PATH)) {
			try {
				translatePath()
			} catch (ignored: Throwable) {
			}
			return -1L
		}
		lockfile.delete()
		return super.invoke()
	}
}
