package service.elementum.android.test

import android.app.Instrumentation
import android.content.Context
import android.content.pm.PackageManager
import androidx.test.platform.app.InstrumentationRegistry
import ilchev.stefan.binarywrapper.getPackageInfo

val instrumentation: Instrumentation
	get() = InstrumentationRegistry.getInstrumentation()

val targetContext: Context
	get() = instrumentation.targetContext

private fun Instrumentation.executeAllowCmd(
	permission: String
) = uiAutomation.executeShellCommand("appops set ${targetContext.packageName} $permission allow").close()

fun Instrumentation.grantRequestedPermissions() {
	executeAllowCmd("REQUEST_INSTALL_PACKAGES")
	executeAllowCmd("MANAGE_EXTERNAL_STORAGE")
	targetContext.getPackageInfo(PackageManager.GET_PERMISSIONS).requestedPermissions?.forEach {
		try {
			uiAutomation.grantRuntimePermission(targetContext.packageName, it)
		} catch (ignored: Throwable) {
		}
	}
}
