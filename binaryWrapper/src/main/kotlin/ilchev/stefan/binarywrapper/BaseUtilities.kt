package ilchev.stefan.binarywrapper

import android.annotation.SuppressLint
import android.app.Service.RECEIVER_EXPORTED
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import android.util.Log
import android.widget.Toast

private const val TAG = "BaseUtilities"

val mainHandler = Handler(Looper.getMainLooper())

val Context.sharedPreferences: SharedPreferences
	get() = getSharedPreferences(BuildConfig.LIBRARY_PACKAGE_NAME, Context.MODE_PRIVATE)

@Suppress("deprecation", "KotlinRedundantDiagnosticSuppress")
fun Context.getPackageInfo(
	flags: Int
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
	registerReceiver(receiver, filter, null, mainHandler, RECEIVER_EXPORTED)
}

fun getUpdate(
	packageInfo: PackageInfo,
	versionName: String?
) = versionName.takeIf { packageInfo.versionName != it }

fun Context.tryShowDifferent(versionName: String?) {
	try {
		val packageInfo = getPackageInfo(0)
		getUpdate(packageInfo, versionName) ?: return
		Toast.makeText(this, "${packageInfo.versionName} \u2260 $versionName", Toast.LENGTH_LONG).show()
	} catch (t: Throwable) {
		Log.w(TAG, t)
	}
}
