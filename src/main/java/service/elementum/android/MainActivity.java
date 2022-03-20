package service.elementum.android;

import android.net.Uri;

import ilchev.stefan.binarywrapper.BaseMainActivity;

public class MainActivity extends BaseMainActivity {

	public static String getVersionName(Uri data) {
		return data != null ? data.getSchemeSpecificPart() : null;
	}

	@Override
	protected Class<?> getForegroundServiceClass() {
		return ForegroundService.class;
	}

	@Override
	protected String getVersionName() {
		var intent = getIntent();
		return getVersionName(intent != null ? intent.getData() : null);
	}
}
