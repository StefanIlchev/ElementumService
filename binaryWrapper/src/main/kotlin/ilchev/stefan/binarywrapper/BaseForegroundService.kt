package ilchev.stefan.binarywrapper

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.content.pm.ServiceInfo
import android.graphics.BitmapFactory
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import java.io.File

abstract class BaseForegroundService : Service() {

	private var workHandler: Handler? = null

	private var daemonInvoker: BaseDaemonInvoker? = null

	@Volatile
	private var updateVersionName: String? = null

	private var updateVersionNameMsg: String? = null

	private var updateInstallReceiver: BroadcastReceiver? = null

	private var updateInstallId = 0

	private var updateDownloadReceiver: BroadcastReceiver? = null

	private var updateDownloadId = -1L

	private var mediaSession: MediaSession? = null

	protected abstract val mainActivityClass: Class<*>

	protected abstract fun getDaemonInvoker(data: Uri?): BaseDaemonInvoker

	protected open fun getVersionName(data: Uri?): String? = null

	protected open fun getUpdateFileName(versionName: String): String? = null

	protected open fun getUpdateDownloadUri(versionName: String): Uri? = null

	private fun getData(intent: Intent?): Uri? {
		intent ?: return sharedPreferences.getString(BuildConfig.LIBRARY_PACKAGE_NAME, null)?.let(Uri::parse)
		return intent.data.also {
			sharedPreferences.edit()
				.putString(BuildConfig.LIBRARY_PACKAGE_NAME, it?.toString())
				.apply()
		}
	}

	private fun getUpdateVersionName(
		data: Uri?
	) = try {
		getUpdate(getPackageInfo(), getVersionName(data))
	} catch (t: Throwable) {
		Log.w(TAG, t)
		null
	}

	private fun startForeground(stopIntent: PendingIntent) {
		val applicationInfo = applicationInfo
		val applicationIcon = applicationInfo.icon
		val applicationLabel = packageManager.getApplicationLabel(applicationInfo)
		val stop = getString(R.string.stop)
		val builder = Notification.Builder(this, BuildConfig.LIBRARY_PACKAGE_NAME)
			.setSmallIcon(applicationIcon)
			.setContentTitle(applicationLabel)
			.setContentText(stop)
			.setContentIntent(stopIntent)
		val manager = getSystemService(NotificationManager::class.java)
		if (manager != null) {
			val channel = NotificationChannel(
				BuildConfig.LIBRARY_PACKAGE_NAME,
				applicationLabel,
				NotificationManager.IMPORTANCE_LOW
			)
			manager.createNotificationChannel(channel)
		}
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
			builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
		}
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
			startForeground(NOTIFICATION_ID, builder.build())
		} else {
			startForeground(NOTIFICATION_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
		}
	}

	private fun stopForeground() {
		updateVersionNameMsg = updateVersionName
		stopForeground(STOP_FOREGROUND_REMOVE)
		stopSelf()
	}

	private fun stopWorkLooper() {
		workHandler?.apply {
			workHandler = null
			looper.quitSafely()
		}
	}

	private fun startWorkLooper() {
		stopWorkLooper()
		val workThread = HandlerThread(TAG)
		workThread.start()
		workHandler = Handler(workThread.looper)
	}

	private fun stopDaemon() {
		daemonInvoker?.apply {
			daemonInvoker = null
			destroy()
		}
	}

	private fun startDaemon(daemonInvoker: BaseDaemonInvoker, workHandler: Handler) {
		stopDaemon()
		workHandler.post(object : Runnable {

			override fun run() {
				val nextInvokeDelay = daemonInvoker()
				if (nextInvokeDelay < 0L) {
					mainHandler.post {
						if (!daemonInvoker.isDestroyed) {
							stopForeground()
						}
					}
				} else {
					workHandler.postDelayed(this, nextInvokeDelay)
				}
			}
		})
		this.daemonInvoker = daemonInvoker
	}

	private fun postUpdateStop(versionName: String) {
		mainHandler.post {
			if (versionName == updateVersionName) {
				stopForeground()
			}
		}
	}

	private fun unregisterUpdateReceiver(
		versionName: String,
		receiver: BroadcastReceiver
	) = if (versionName == updateVersionName) {
		false
	} else {
		unregisterReceiver(receiver)
		true
	}

	private fun stopUpdateInstall() {
		updateInstallReceiver?.also {
			updateInstallReceiver = null
			unregisterReceiver(it)
		}
		updateInstallId.takeIf { it > 0 }?.also {
			updateInstallId = 0
			try {
				packageManager.packageInstaller.abandonSession(it)
			} catch (_: Throwable) {
			}
		}
	}

	private fun startUpdateInstall(file: File, versionName: String, workHandler: Handler) {
		if (!file.isFile) return stopForeground()
		val receiver = object : BroadcastReceiver() {

			override fun onReceive(context: Context?, intent: Intent?) {
				if (unregisterUpdateReceiver(versionName, this)) return
				val id = intent?.takeIf {
					it.action == BuildConfig.LIBRARY_PACKAGE_NAME
				}?.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, 0) ?: return
				if (id == 0 || id != updateInstallId) return
				updateInstallId = 0
				updateInstallReceiver = null
				unregisterReceiver(this)
				val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
				val activity = if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
					getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
				} else {
					null
				}
				if (activity != null) {
					updateVersionName = null
					tryStartActivity(activity.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
				} else if (status == PackageInstaller.STATUS_SUCCESS) {
					updateVersionName = null
				}
				stopForeground()
			}
		}
		val filter = IntentFilter(BuildConfig.LIBRARY_PACKAGE_NAME)
		registerExportedReceiver(receiver, filter)
		updateInstallReceiver = receiver
		val installer = packageManager.packageInstaller
		val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
		params.setSize(file.length())
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
			params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
		}
		val updateInstallId = installer.createSession(params).also { updateInstallId = it }
		val intent = Intent(BuildConfig.LIBRARY_PACKAGE_NAME).setPackage(packageName)
		val flags = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
			PendingIntent.FLAG_UPDATE_CURRENT
		} else {
			PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
		}
		val statusReceiver = PendingIntent.getBroadcast(this, updateInstallId, intent, flags).intentSender
		workHandler.post {
			if (versionName != updateVersionName) return@post
			try {
				installer.openSession(updateInstallId).use { session ->
					session.openWrite(file.name, 0L, file.length()).use { out ->
						file.inputStream().use { it.copyTo(out) }
						session.fsync(out)
					}
					session.commit(statusReceiver)
				}
			} catch (t: Throwable) {
				Log.w(TAG, t)
				postUpdateStop(versionName)
			}
		}
	}

	private fun stopUpdateDownload(): Int {
		updateDownloadReceiver?.also {
			updateDownloadReceiver = null
			unregisterReceiver(it)
		}
		return updateDownloadId.takeIf { it != -1L }?.let {
			updateDownloadId = -1L
			val manager = getSystemService(DownloadManager::class.java)
			manager?.remove(it)
		} ?: 0
	}

	private fun startUpdateDownload(versionName: String, workHandler: Handler) {
		val manager = getSystemService(DownloadManager::class.java) ?: return stopForeground()
		val downloadUri = getUpdateDownloadUri(versionName) ?: return stopForeground()
		val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return stopForeground()
		val fileName = getUpdateFileName(versionName) ?: return stopForeground()
		val file = File(dir, fileName)
		val receiver = object : BroadcastReceiver() {

			override fun onReceive(context: Context?, intent: Intent?) {
				if (unregisterUpdateReceiver(versionName, this)) return
				when (intent?.action) {
					DownloadManager.ACTION_DOWNLOAD_COMPLETE -> {
						val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
						if (id != -1L && id == updateDownloadId) {
							updateDownloadId = -1L
							updateDownloadReceiver = null
							unregisterReceiver(this)
							try {
								startUpdateInstall(file, versionName, workHandler)
							} catch (t: Throwable) {
								Log.w(TAG, t)
								stopForeground()
							}
						}
					}

					DownloadManager.ACTION_NOTIFICATION_CLICKED -> {
						val id = updateDownloadId
						val ids = intent.getLongArrayExtra(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS)
						if (id != -1L && ids?.contains(id) == true) {
							stopForeground()
						}
					}

					else -> Unit
				}
			}
		}
		val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
		filter.addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED)
		registerExportedReceiver(receiver, filter)
		updateDownloadReceiver = receiver
		val applicationInfo = applicationInfo
		val applicationLabel = packageManager.getApplicationLabel(applicationInfo)
		val stop = getString(R.string.stop)
		val request = DownloadManager.Request(downloadUri)
			.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, fileName)
			.setTitle(applicationLabel)
			.setDescription(stop)
		val updateDownloadId = manager.enqueue(request).also { updateDownloadId = it }
		val query = DownloadManager.Query().setFilterById(updateDownloadId)
		workHandler.post(object : Runnable {

			override fun run() {
				try {
					val status = manager.query(query).use {
						if (it?.moveToFirst() == true) {
							it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
						} else {
							DownloadManager.STATUS_FAILED
						}
					}
					if (status == DownloadManager.STATUS_SUCCESSFUL || versionName != updateVersionName) return
					if (status != DownloadManager.STATUS_FAILED) {
						workHandler.postDelayed(this, 1_000L)
						return
					}
				} catch (t: Throwable) {
					Log.w(TAG, t)
				}
				postUpdateStop(versionName)
			}
		})
	}

	private fun stopUpdate() {
		val versionName = updateVersionNameMsg
		updateVersionName = null
		updateVersionNameMsg = null
		tryShowDifferent(versionName)
		stopUpdateInstall()
		stopUpdateDownload()
		try {
			getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.deleteRecursively()
		} catch (t: Throwable) {
			Log.w(TAG, t)
		}
	}

	private fun startUpdate(versionName: String, workHandler: Handler) {
		stopUpdate()
		updateVersionName = versionName
		startUpdateDownload(versionName, workHandler)
	}

	private fun stopMediaSession() {
		mediaSession?.apply {
			mediaSession = null
			isActive = false
			release()
		}
	}

	private fun startMediaSession(stopIntent: PendingIntent) {
		stopMediaSession()
		val applicationInfo = applicationInfo
		val applicationIcon = BitmapFactory.decodeResource(resources, applicationInfo.icon)
		val applicationLabel = packageManager.getApplicationLabel(applicationInfo).toString()
		val stop = getString(R.string.stop)
		val mediaSession = MediaSession(this, BuildConfig.LIBRARY_PACKAGE_NAME).also { mediaSession = it }
		mediaSession.setMetadata(
			MediaMetadata.Builder()
				.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, applicationIcon)
				.putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, applicationLabel)
				.putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, stop)
				.putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, stop)
				.putString(MediaMetadata.METADATA_KEY_TITLE, applicationLabel)
				.putString(MediaMetadata.METADATA_KEY_ARTIST, stop)
				.build()
		)
		mediaSession.setPlaybackState(
			PlaybackState.Builder()
				.setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0F)
				.build()
		)
		mediaSession.setSessionActivity(stopIntent)
		mediaSession.isActive = true
	}

	override fun onCreate() {
		try {
			val stopIntent = PendingIntent.getActivity(
				this,
				NOTIFICATION_ID,
				Intent(this, mainActivityClass),
				PendingIntent.FLAG_IMMUTABLE
			)
			startForeground(stopIntent)
			startWorkLooper()
			startMediaSession(stopIntent)
		} catch (t: Throwable) {
			Log.w(TAG, t)
		}
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		val data = getData(intent)
		try {
			val versionName = getUpdateVersionName(data)
			if (versionName == null) {
				val daemonInvoker = getDaemonInvoker(data)
				if (daemonInvoker != this.daemonInvoker) {
					stopUpdate()
					startDaemon(daemonInvoker, workHandler!!)
				}
			} else if (versionName != updateVersionName) {
				stopDaemon()
				startUpdate(versionName, workHandler!!)
			}
		} catch (t: Throwable) {
			Log.w(TAG, t)
			stopForeground()
		}
		return START_STICKY
	}

	override fun onDestroy() {
		try {
			stopWorkLooper()
			stopDaemon()
			stopUpdate()
			stopMediaSession()
		} catch (t: Throwable) {
			Log.w(TAG, t)
		}
	}

	override fun onBind(intent: Intent?) = null

	companion object {

		private const val TAG = "BaseForegroundService"

		private const val NOTIFICATION_ID = 1
	}
}
