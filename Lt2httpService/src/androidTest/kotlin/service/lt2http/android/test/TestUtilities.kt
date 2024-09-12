package service.lt2http.android.test

import android.app.Instrumentation
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import ilchev.stefan.binarywrapper.getPackageInfo
import java.util.Scanner

private const val TAG = "TestUtilities"

val instrumentation: Instrumentation
	get() = InstrumentationRegistry.getInstrumentation()

val targetContext: Context
	get() = instrumentation.targetContext

private fun Instrumentation.executeShell(
	command: String
) = Scanner(ParcelFileDescriptor.AutoCloseInputStream(uiAutomation.executeShellCommand(command))).use {
	while (it.hasNextLine()) {
		val line = it.nextLine()
		Log.v(TAG, line)
	}
}

private fun Instrumentation.tryGrantRuntimePermission(
	permission: String
) = try {
	uiAutomation.grantRuntimePermission(targetContext.packageName, permission)
} catch (ignored: Throwable) {
}

fun Instrumentation.grantRequestedPermissions() {
	val packageInfo = targetContext.getPackageInfo(PackageManager.GET_PERMISSIONS)
	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
		packageInfo.requestedPermissions?.forEach { executeShell("pm grant ${targetContext.packageName} $it") }
	} else {
		packageInfo.requestedPermissions?.forEach(::tryGrantRuntimePermission)
	}
	executeShell("appops set ${targetContext.packageName} REQUEST_INSTALL_PACKAGES allow")
	if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
		executeShell("appops set ${targetContext.packageName} MANAGE_EXTERNAL_STORAGE allow")
	}
}
