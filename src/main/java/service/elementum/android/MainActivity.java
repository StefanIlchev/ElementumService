package service.elementum.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Arrays;

public class MainActivity extends Activity {

	private enum RequestCode {
		REQUESTED_PERMISSIONS,
		MANAGE_EXTERNAL_STORAGE
	}

	@SuppressLint("InlinedApi")
	private static final String MANAGE_EXTERNAL_STORAGE =
			Manifest.permission.MANAGE_EXTERNAL_STORAGE;

	@SuppressLint("InlinedApi")
	private static final String ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION =
			Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;

	private static boolean isExternalStorageManager() {
		return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
	}

	private String[] requestRequestedPermissions() {
		var packageName = getPackageName();
		try {
			var requestedPermissions = getPackageManager()
					.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)
					.requestedPermissions;
			if (requestedPermissions != null && requestedPermissions.length > 0) {
				var list = new ArrayList<>(Arrays.asList(requestedPermissions));
				if (list.remove(MANAGE_EXTERNAL_STORAGE) && !isExternalStorageManager()) {
					var uri = Uri.fromParts("package", packageName, null);
					var intent = new Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
					startActivityForResult(intent, RequestCode.MANAGE_EXTERNAL_STORAGE.ordinal());
					return new String[]{MANAGE_EXTERNAL_STORAGE};
				}
				if (!list.isEmpty()) {
					var permissions = list.toArray(new String[]{});
					requestPermissions(permissions, RequestCode.REQUESTED_PERMISSIONS.ordinal());
					return permissions;
				}
			}
		} catch (Throwable ignore) {
		}
		return null;
	}

	private void tryStartForegroundService() {
		var intent = new Intent(this, ForegroundService.class);
		try {
			startForegroundService(intent);
		} catch (Throwable ignore) {
		}
	}

	private void requestRequestedPermissionsOrTryStartForegroundServiceAndFinish() {
		if (requestRequestedPermissions() == null) {
			tryStartForegroundService();
			finish();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestRequestedPermissionsOrTryStartForegroundServiceAndFinish();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == RequestCode.REQUESTED_PERMISSIONS.ordinal()) {
			if (Arrays.stream(grantResults).allMatch(value -> value == PackageManager.PERMISSION_GRANTED)) {
				tryStartForegroundService();
			}
			finish();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == RequestCode.MANAGE_EXTERNAL_STORAGE.ordinal()) {
			if (isExternalStorageManager()) {
				requestRequestedPermissionsOrTryStartForegroundServiceAndFinish();
			} else {
				finish();
			}
		}
	}
}
