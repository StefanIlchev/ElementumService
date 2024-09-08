package service.lt2http.android

import android.net.Uri
import android.os.Build
import ilchev.stefan.binarywrapper.BaseForegroundService

class ForegroundService : BaseForegroundService() {

	override val mainActivityClass = MainActivity::class.java

	override fun getDaemonInvoker(
		data: Uri?
	) = DaemonInvoker(this, *data?.fragment?.split("\u0000")?.toTypedArray() ?: emptyArray())

	override fun getVersionName(
		data: Uri?
	) = data?.schemeSpecificPart

	override fun getUpdateFileName(
		versionName: String
	) = "${BuildConfig.PROJECT_NAME}-${Build.SUPPORTED_ABIS[0]}-${BuildConfig.BUILD_TYPE}-$versionName.apk"

	override fun getUpdateDownloadUri(
		versionName: String
	) = if (BuildConfig.REPO_URL.isEmpty()) {
		null
	} else {
		Uri.parse("${BuildConfig.REPO_URL}${getUpdateFileName(versionName)}")
	}
}
