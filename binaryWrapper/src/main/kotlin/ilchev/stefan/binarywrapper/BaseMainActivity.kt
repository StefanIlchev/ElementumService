package ilchev.stefan.binarywrapper

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.widget.Toast

abstract class BaseMainActivity : Activity() {

	private var allowCmdRunnable: Runnable? = null

	private var allowCmdDialog: Dialog? = null

	protected abstract val foregroundServiceClass: Class<*>

	protected open val versionName: String? = null

	protected open val isStopIntent
		get() = intent?.action == null

	private var isInstallPackagesRequester
		get() = packageManager.canRequestPackageInstalls()
		set(value) {
			if (value) {
				intent?.action = null
			}
		}

	private var isExternalStorageManager
		get() = Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager() ||
				getSharedPreferences(BuildConfig.LIBRARY_PACKAGE_NAME, MODE_PRIVATE)
					.getBoolean(MANAGE_EXTERNAL_STORAGE, false)
		set(value) {
			getSharedPreferences(BuildConfig.LIBRARY_PACKAGE_NAME, MODE_PRIVATE)
				.edit()
				.putBoolean(MANAGE_EXTERNAL_STORAGE, value)
				.apply()
		}

	private var isForegroundServiceStart
		get() = getSharedPreferences(BuildConfig.LIBRARY_PACKAGE_NAME, MODE_PRIVATE)
			.getBoolean(FOREGROUND_SERVICE, false)
		set(value) {
			getSharedPreferences(BuildConfig.LIBRARY_PACKAGE_NAME, MODE_PRIVATE)
				.edit()
				.putBoolean(FOREGROUND_SERVICE, value)
				.apply()
		}

	private fun isActivityFound(
		intent: Intent
	) = try {
		val activityInfo = intent.resolveActivityInfo(packageManager, 0)
		activityInfo?.isEnabled == true && activityInfo.exported
	} catch (t: Throwable) {
		Log.w(TAG, t)
		false
	}

	private fun tryStartActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
		try {
			startActivityForResult(intent, requestCode, options)
		} catch (t: Throwable) {
			Log.w(TAG, t)
		}
	}

	private fun tryStopService(
		intent: Intent
	) = try {
		stopService(intent)
	} catch (t: Throwable) {
		Log.w(TAG, t)
		false
	}

	private fun hideAllowCmd() {
		allowCmdRunnable?.also {
			allowCmdRunnable = null
			BaseForegroundService.mainHandler.removeCallbacks(it)
		}
		allowCmdDialog?.apply {
			allowCmdDialog = null
			dismiss()
		}
	}

	private inline fun showAllowCmd(
		permission: String,
		crossinline supplier: () -> Boolean,
		crossinline consumer: (Boolean) -> Unit
	) {
		hideAllowCmd()
		val applicationInfo = applicationInfo
		val applicationIcon = applicationInfo.icon
		val applicationLabel = packageManager.getApplicationLabel(applicationInfo)
		val cmd = "adb shell appops set --uid $packageName $permission allow"
		val allowCmdDialog = AlertDialog.Builder(this)
			.setIcon(applicationIcon)
			.setTitle(applicationLabel)
			.setMessage(cmd)
			.setPositiveButton(R.string.allow) { _, _ ->
				if (!supplier()) {
					Toast.makeText(this, R.string.allow_cmd_allow_msg, Toast.LENGTH_SHORT).show()
				}
			}
			.setNegativeButton(R.string.deny) { _, _ ->
				if (!supplier()) {
					consumer(true)
				}
			}
			.setNeutralButton(R.string.close_app) { _, _ ->
				intent = null
			}
			.setCancelable(false)
			.show()
		this.allowCmdDialog = allowCmdDialog
		val allowCmdRunnable = object : Runnable {

			override fun run() {
				if (isFinishing || isDestroyed) return
				if (isStopIntent) {
					callForegroundServiceAndFinish()
				} else if (allowCmdDialog.isShowing && !supplier()) {
					BaseForegroundService.mainHandler.postDelayed(this, 100L)
				} else if (requestRequestedPermissions() == null) {
					callForegroundServiceAndFinish()
				}
			}
		}
		BaseForegroundService.mainHandler.post(allowCmdRunnable)
		this.allowCmdRunnable = allowCmdRunnable
	}

	private inline fun request(
		permission: String,
		crossinline supplier: () -> Boolean,
		crossinline consumer: (Boolean) -> Unit,
		action: String,
		requestCode: RequestCode
	): Boolean {
		if (supplier()) return false
		val intent = Intent(action, Uri.fromParts("package", packageName, null))
		if (isActivityFound(intent) ||
			isActivityFound(intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))
		) {
			tryStartActivityForResult(intent, requestCode.ordinal, null)
		} else {
			showAllowCmd(permission, supplier, consumer)
		}
		return true
	}

	private fun requestRequestedPermissions(): Array<String>? {
		try {
			val packageInfo = BaseForegroundService.getPackageInfo(this, PackageManager.GET_PERMISSIONS)
			val set = mutableSetOf(*packageInfo.requestedPermissions ?: emptyArray())
			if (set.remove(Manifest.permission.REQUEST_INSTALL_PACKAGES) &&
				BaseForegroundService.getUpdate(
					packageInfo,
					versionName
				) != null &&
				request(
					"REQUEST_INSTALL_PACKAGES",
					{ isInstallPackagesRequester },
					{ isInstallPackagesRequester = it },
					Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
					RequestCode.REQUEST_INSTALL_PACKAGES
				)
			) {
				tryStopService(Intent(this, foregroundServiceClass))
				return arrayOf(Manifest.permission.REQUEST_INSTALL_PACKAGES)
			}
			if (set.remove(MANAGE_EXTERNAL_STORAGE) &&
				request(
					"MANAGE_EXTERNAL_STORAGE",
					{ isExternalStorageManager },
					{ isExternalStorageManager = it },
					ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
					RequestCode.MANAGE_EXTERNAL_STORAGE
				)
			) return arrayOf(MANAGE_EXTERNAL_STORAGE)
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
				set.remove(FOREGROUND_SERVICE)
			}
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
				set.remove(UPDATE_PACKAGES_WITHOUT_USER_ACTION)
			}
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
				set.remove(POST_NOTIFICATIONS)
			}
			if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
				set.remove(Manifest.permission.READ_EXTERNAL_STORAGE)
				set.remove(Manifest.permission.WRITE_EXTERNAL_STORAGE)
			}
			if (set.isNotEmpty()) {
				val permissions = set.toTypedArray()
				requestPermissions(permissions, RequestCode.REQUESTED_PERMISSIONS.ordinal)
				return permissions
			}
		} catch (t: Throwable) {
			Log.w(TAG, t)
		}
		return null
	}

	private fun callForegroundServiceAndFinish() {
		val isForegroundServiceStart = isForegroundServiceStart
		if (isForegroundServiceStart) {
			this.isForegroundServiceStart = false
		}
		try {
			val service = (intent?.let(::Intent) ?: Intent()).setClass(this, foregroundServiceClass)
			if (isStopIntent) {
				tryStopService(service)
				BaseForegroundService.tryShowDifferent(this, versionName)
			} else if (isForegroundServiceStart) {
				startForegroundService(service)
			}
		} catch (t: Throwable) {
			Log.w(TAG, t)
		}
		finish()
	}

	override fun onResume() {
		super.onResume()
		isForegroundServiceStart = true
		if (isStopIntent || requestRequestedPermissions() == null) {
			callForegroundServiceAndFinish()
		}
	}

	override fun onPause() {
		super.onPause()
		hideAllowCmd()
	}

	override fun onRequestPermissionsResult(
		requestCode: Int,
		permissions: Array<String>,
		grantResults: IntArray
	) = when (requestCode) {
		RequestCode.REQUESTED_PERMISSIONS.ordinal -> callForegroundServiceAndFinish()
		else -> Unit
	}

	override fun onActivityResult(
		requestCode: Int,
		resultCode: Int,
		data: Intent?
	) = when (requestCode) {
		RequestCode.REQUEST_INSTALL_PACKAGES.ordinal -> isInstallPackagesRequester = !isInstallPackagesRequester
		RequestCode.MANAGE_EXTERNAL_STORAGE.ordinal -> isExternalStorageManager = !isExternalStorageManager
		else -> Unit
	}

	override fun onNewIntent(intent: Intent) {
		this.intent = intent
	}

	private enum class RequestCode {
		REQUESTED_PERMISSIONS,
		REQUEST_INSTALL_PACKAGES,
		MANAGE_EXTERNAL_STORAGE
	}

	companion object {

		private const val TAG = "BaseMainActivity"

		@SuppressLint("InlinedApi")
		private const val ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION =
			Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION

		@SuppressLint("InlinedApi")
		private const val FOREGROUND_SERVICE = Manifest.permission.FOREGROUND_SERVICE

		@SuppressLint("InlinedApi")
		private const val UPDATE_PACKAGES_WITHOUT_USER_ACTION = Manifest.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION

		@SuppressLint("InlinedApi")
		private const val POST_NOTIFICATIONS = Manifest.permission.POST_NOTIFICATIONS

		@SuppressLint("InlinedApi")
		private const val MANAGE_EXTERNAL_STORAGE = Manifest.permission.MANAGE_EXTERNAL_STORAGE
	}
}
