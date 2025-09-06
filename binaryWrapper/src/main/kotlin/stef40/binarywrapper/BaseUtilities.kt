package stef40.binarywrapper

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import android.widget.Toast

private const val TAG = "BaseUtilities"

val mainHandler = Handler(Looper.getMainLooper())

val Context.sharedPreferences: SharedPreferences
	get() = getSharedPreferences(BuildConfig.LIBRARY_PACKAGE_NAME, Context.MODE_PRIVATE)

fun Context.isActivityFound(
	intent: Intent
) = try {
	val activityInfo = intent.resolveActivityInfo(packageManager, 0)
	activityInfo?.isEnabled == true && activityInfo.exported
} catch (t: Throwable) {
	Log.w(TAG, t)
	false
}

fun Context.tryStartActivity(intent: Intent, options: Bundle? = null) {
	try {
		startActivity(intent, options)
	} catch (t: Throwable) {
		Log.w(TAG, t)
	}
}

fun Context.tryStopService(
	intent: Intent
) = try {
	stopService(intent)
} catch (t: Throwable) {
	Log.w(TAG, t)
	false
}

@Suppress("deprecation", "KotlinRedundantDiagnosticSuppress")
fun Context.getPackageInfo(
	flags: Int = 0
): PackageInfo = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
	packageManager.getPackageInfo(packageName, flags)
} else {
	packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
}

@Suppress("deprecation")
fun <T : Parcelable> getParcelableExtra(
	intent: Intent,
	name: String,
	clazz: Class<T>
) = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
	intent.getParcelableExtra(name)
} else {
	intent.getParcelableExtra(name, clazz)
}

@SuppressLint("UnspecifiedRegisterReceiverFlag")
fun Context.registerExportedReceiver(
	receiver: BroadcastReceiver,
	filter: IntentFilter
) = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
	registerReceiver(receiver, filter, null, mainHandler)
} else {
	registerReceiver(receiver, filter, null, mainHandler, Context.RECEIVER_EXPORTED)
}

fun getUpdate(
	packageInfo: PackageInfo,
	versionName: String?
) = versionName.takeIf { packageInfo.versionName != it }

fun Context.tryShowDifferent(versionName: String?) {
	try {
		val packageInfo = getPackageInfo()
		getUpdate(packageInfo, versionName) ?: return
		Toast.makeText(this, "${packageInfo.versionName} \u2260 $versionName", Toast.LENGTH_LONG).show()
	} catch (t: Throwable) {
		Log.w(TAG, t)
	}
}
