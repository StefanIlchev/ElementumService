package service.lt2http.android

import stef40.binarywrapper.BaseMainActivity

class MainActivity : BaseMainActivity() {

	override val foregroundServiceClass = ForegroundService::class.java

	override val versionName
		get() = intent?.data?.schemeSpecificPart

	override val isStopIntent
		get() = super.isStopIntent || intent?.data?.scheme == "stop"
}
