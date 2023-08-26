package service.lt2http.android

import android.net.Uri
import ilchev.stefan.binarywrapper.BaseForegroundService

class ForegroundService : BaseForegroundService() {

	override val mainActivityClass = MainActivity::class.java

	override fun getDaemonInvoker(
		data: Uri?
	) = DaemonInvoker(this, mainHandler, *data?.fragment?.split("\u0000")?.toTypedArray() ?: emptyArray())

	override fun getVersionName(
		data: Uri?
	) = data?.schemeSpecificPart
}
