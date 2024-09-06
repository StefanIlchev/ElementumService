package service.elementum.android.test

import android.app.Instrumentation
import android.content.pm.PackageManager
import ilchev.stefan.binarywrapper.BaseForegroundService

private fun Instrumentation.executeAllowCmd(
	permission: String
) = uiAutomation.executeShellCommand("appops set ${targetContext.packageName} $permission allow").close()

fun Instrumentation.grantRequestedPermissions() {
	executeAllowCmd("REQUEST_INSTALL_PACKAGES")
	executeAllowCmd("MANAGE_EXTERNAL_STORAGE")
	BaseForegroundService.getPackageInfo(targetContext, PackageManager.GET_PERMISSIONS).requestedPermissions?.forEach {
		try {
			uiAutomation.grantRuntimePermission(targetContext.packageName, it)
		} catch (ignored: Throwable) {
		}
	}
}
