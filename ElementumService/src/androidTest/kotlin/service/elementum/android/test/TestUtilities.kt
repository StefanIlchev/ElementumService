package service.elementum.android.test

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

private fun Instrumentation.executeAllowCmd(
	permission: String
) = executeShell("appops set ${targetContext.packageName} $permission allow")

private fun Instrumentation.tryGrantRuntimePermission(
	permission: String
) = try {
	uiAutomation.grantRuntimePermission(targetContext.packageName, permission)
} catch (ignored: Throwable) {
}

fun Instrumentation.grantRequestedPermissions() {
	val packageInfo = targetContext.getPackageInfo(PackageManager.GET_PERMISSIONS)
	val requestedPermissions = packageInfo.requestedPermissions ?: return
	executeAllowCmd("REQUEST_INSTALL_PACKAGES")
	if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
		executeAllowCmd("MANAGE_EXTERNAL_STORAGE")
	}
	if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
		requestedPermissions.forEach { executeShell("pm grant ${targetContext.packageName} $it") }
	} else {
		requestedPermissions.forEach(::tryGrantRuntimePermission)
	}
}
