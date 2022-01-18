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
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

public class MainActivity extends Activity {

	private enum RequestCode {
		REQUESTED_PERMISSIONS,
		MANAGE_EXTERNAL_STORAGE
	}

	private static final String TAG = "MainActivity";

	private static final long MANAGE_EXTERNAL_STORAGE_CHECK_DELAY_MILLIS = 100L;

	@SuppressLint("InlinedApi")
	private static final String MANAGE_EXTERNAL_STORAGE =
			Manifest.permission.MANAGE_EXTERNAL_STORAGE;

	@SuppressLint("InlinedApi")
	private static final String ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION =
			Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;

	@SuppressLint("InlinedApi")
	private static final String FOREGROUND_SERVICE =
			Manifest.permission.FOREGROUND_SERVICE;

	private static List<String> listPermissions(String[] permissions, int[] grantResults, int grantResult) {
		var result = new ArrayList<String>();
		for (var i = 0; i < grantResults.length; ++i) {
			if (grantResults[i] == grantResult) {
				result.add(permissions[i]);
			}
		}
		return result;
	}

	private boolean isExternalStorageManager() {
		return Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager() ||
				getSharedPreferences(TAG, MODE_PRIVATE).getBoolean(MANAGE_EXTERNAL_STORAGE, false);
	}

	private void setExternalStorageManager(boolean value) {
		getSharedPreferences(TAG, MODE_PRIVATE)
				.edit()
				.putBoolean(MANAGE_EXTERNAL_STORAGE, value)
				.apply();
	}

	private void showAllowManageExternalStorageCmd() {
		var cmd = "adb shell appops set --uid " + getPackageName() + " MANAGE_EXTERNAL_STORAGE allow";
		var cmdDialog = new AlertDialog.Builder(this)
				.setTitle(R.string.app_name)
				.setIcon(R.mipmap.ic_launcher)
				.setMessage(cmd)
				.setPositiveButton(R.string.manage_external_storage_allow, (dialog, which) ->
						Toast.makeText(this, R.string.manage_external_storage_allow_message, Toast.LENGTH_SHORT).show())
				.setNegativeButton(R.string.manage_external_storage_deny, (dialog, which) ->
						setExternalStorageManager(true))
				.setNeutralButton(R.string.close_app, (dialog, which) ->
						finish())
				.setCancelable(false)
				.show();
		var check = new Runnable() {

			@Override
			public void run() {
				if (isDestroyed() || isFinishing()) {
					return;
				}
				if (isExternalStorageManager() || !cmdDialog.isShowing()) {
					requestRequestedPermissionsOrTryStartForegroundServiceAndFinish();
				} else {
					ForegroundService.HANDLER.postDelayed(this, MANAGE_EXTERNAL_STORAGE_CHECK_DELAY_MILLIS);
				}
			}
		};
		ForegroundService.HANDLER.postDelayed(check, MANAGE_EXTERNAL_STORAGE_CHECK_DELAY_MILLIS);
	}

	private String[] requestManageExternalStorage() {
		var uri = Uri.fromParts("package", getPackageName(), null);
		var intent = new Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
		if (intent.resolveActivity(getPackageManager()) != null) {
			startActivityForResult(intent, RequestCode.MANAGE_EXTERNAL_STORAGE.ordinal());
		} else {
			showAllowManageExternalStorageCmd();
		}
		return new String[]{MANAGE_EXTERNAL_STORAGE};
	}

	private String[] requestRequestedPermissions() {
		try {
			var requestedPermissions = getPackageManager()
					.getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS)
					.requestedPermissions;
			if (requestedPermissions != null && requestedPermissions.length > 0) {
				var set = new HashSet<>(Arrays.asList(requestedPermissions));
				if (set.remove(MANAGE_EXTERNAL_STORAGE) && !isExternalStorageManager()) {
					return requestManageExternalStorage();
				}
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
					set.remove(FOREGROUND_SERVICE);
				}
				if (!set.isEmpty()) {
					var permissions = set.toArray(new String[]{});
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
