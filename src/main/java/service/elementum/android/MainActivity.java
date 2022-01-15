package service.elementum.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends Activity {

	private enum RequestCode {
		REQUESTED_PERMISSIONS,
		MANAGE_EXTERNAL_STORAGE
	}

	private static final String TAG = "MainActivity";

	@SuppressLint("InlinedApi")
	private static final String MANAGE_EXTERNAL_STORAGE =
			Manifest.permission.MANAGE_EXTERNAL_STORAGE;

	@SuppressLint("InlinedApi")
	private static final String ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION =
			Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;

	private static List<String> listPermissions(String[] permissions, int[] grantResults, int grantResult) {
		var result = new ArrayList<String>();
		for (var i = 0; i < grantResults.length; ++i) {
			if (grantResults[i] == grantResult) {
				result.add(permissions[i]);
			}
		}
		return result;
	}

	private static boolean isExternalStorageManager() {
		return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager();
	}

	private String[] requestManageExternalStorage(String packageName) {
		var uri = Uri.fromParts("package", packageName, null);
		var intent = new Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
		if (getPackageManager().queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY).isEmpty()) {
			var cmd = "adb shell appops set --uid " + packageName + " MANAGE_EXTERNAL_STORAGE allow";
			new AlertDialog.Builder(this)
					.setTitle(R.string.app_name)
					.setMessage(cmd)
					.setPositiveButton(android.R.string.ok, null)
					.setOnDismissListener(dialog ->
							onActivityResult(RequestCode.MANAGE_EXTERNAL_STORAGE.ordinal(), 0, null))
					.show();
		} else {
			startActivityForResult(intent, RequestCode.MANAGE_EXTERNAL_STORAGE.ordinal());
		}
		return new String[]{MANAGE_EXTERNAL_STORAGE};
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
					return requestManageExternalStorage(packageName);
				}
				if (!list.isEmpty()) {
					var permissions = list.toArray(new String[]{});
					requestPermissions(permissions, RequestCode.REQUESTED_PERMISSIONS.ordinal());
					return permissions;
				}
			}
		} catch (Throwable t) {
			Log.w(TAG, t);
		}
		return null;
	}

	private void tryStartForegroundService() {
		var intent = new Intent(this, ForegroundService.class);
		try {
			startForegroundService(intent);
		} catch (Throwable t) {
			Log.w(TAG, t);
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
			var deniedPermissions = listPermissions(permissions, grantResults, PackageManager.PERMISSION_DENIED);
			if (deniedPermissions.isEmpty()) {
				tryStartForegroundService();
			} else {
				Log.v(TAG, "deniedPermissions = [" + String.join(", ", deniedPermissions) + "]");
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
				Log.v(TAG, "deniedPermissions = [" + MANAGE_EXTERNAL_STORAGE + "]");
				finish();
			}
		}
	}
}
