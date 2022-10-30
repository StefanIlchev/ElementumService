package service.elementum.android;

import android.net.Uri;
import android.os.Build;

import ilchev.stefan.binarywrapper.BaseForegroundService;

public class ForegroundService extends BaseForegroundService {

	@Override
	protected Class<?> getMainActivityClass() {
		return MainActivity.class;
	}

	@Override
	protected DaemonRunnable getDaemonRunnable(Uri data) {
		var fragment = data != null ? data.getFragment() : null;
		return new DaemonRunnable(this, MAIN_HANDLER, fragment != null ? fragment.split("\0") : null);
	}

	@Override
	protected String getVersionName(Uri data) {
		return MainActivity.getVersionName(data);
	}

	@Override
	protected String getUpdateFileName(String versionName) {
		var abi = Build.SUPPORTED_ABIS[0];
		return String.join("-", BuildConfig.PROJECT_NAME, abi, BuildConfig.BUILD_TYPE, versionName) + ".apk";
	}

	@Override
	protected Uri getUpdateDownloadUri(String versionName) {
		return Uri.parse(BuildConfig.REPO_URL + getUpdateFileName(versionName));
	}
}
