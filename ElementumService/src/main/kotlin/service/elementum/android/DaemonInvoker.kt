package service.elementum.android

import android.content.Context
import android.os.Handler
import android.os.Looper
import ilchev.stefan.binarywrapper.BaseDaemonInvoker
import java.io.File
import java.net.InetSocketAddress
import java.nio.ByteBuffer
import java.nio.channels.Channels
import java.nio.channels.ServerSocketChannel
import java.nio.channels.WritableByteChannel
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

	private inline fun send(port: Int, consumer: (WritableByteChannel) -> Unit): Long {
		if (isDestroyed) return -1L
		ServerSocketChannel.open().use { channel ->
			if (!mainHandler.post(toSetChannelRunnable(channel))) return -1L
			channel.bind(InetSocketAddress(port))
			consumer(channel.accept())
			return if (isDestroyed ||
				!mainHandler.post(clearChannelRunnable)
			) -1L else 1_000L
		}
	}

	private inline fun toSendInvoker(crossinline consumer: (WritableByteChannel) -> Unit): () -> Long {
		val regex = """${BuildConfig.ARG_LOCAL_PORT}=(\d+)""".toRegex()
		val port = subprocessCmd.firstNotNullOfOrNull {
			regex.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull()
		} ?: BuildConfig.LOCAL_PORT
		return {
			try {
				send(port, consumer)
			} catch (ignored: Throwable) {
				-1L
			}
		}
	}

	private fun toAddonInfoConsumer(
		addonId: String
	): (WritableByteChannel) -> Unit = { channel ->
		ZipOutputStream(Channels.newOutputStream(channel).buffered()).use { zip ->
			for (file in File(addonsDir, addonId).walkTopDown()) {
				if (file.isDirectory) {
					zip.putNextEntry(ZipEntry("${file.relativeTo(addonsDir).path}/"))
				} else if (file.isFile) {
					zip.putNextEntry(ZipEntry(file.relativeTo(addonsDir).path))
					file.inputStream().use { it.copyTo(zip) }
				}
			}
		}
	}

	private val invoker = """${BuildConfig.ARG_ADDON_INFO}=(\S+)""".toRegex().let { regex ->
		subprocessCmd.firstNotNullOfOrNull { regex.matchEntire(it)?.groupValues?.get(1) }?.let {
			toSendInvoker(toAddonInfoConsumer(it))
		}
	} ?: if (subprocessCmd.contains(BuildConfig.ARG_TRANSLATE_PATH)) {
		toSendInvoker { channel ->
			channel.use { it.write(ByteBuffer.wrap("${homeDir.path}/\u0000${xbmcDir.path}/".toByteArray())) }
		}
	} else {
		{
			lockfile.delete()
			super.invoke()
		}
	}

	override fun invoke() = invoker()
}
