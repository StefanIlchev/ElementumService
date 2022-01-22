package service.elementum.android;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;

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

	@SuppressLint("InlinedApi")
	private static final String FOREGROUND_SERVICE =
			Manifest.permission.FOREGROUND_SERVICE;

	private Runnable manageExternalStorageAllowCmdRunnable = null;

	private Dialog manageExternalStorageAllowCmdDialog = null;

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

	private void hideManageExternalStorageAllowCmd() {
		var manageExternalStorageAllowCmdRunnable = this.manageExternalStorageAllowCmdRunnable;
		if (manageExternalStorageAllowCmdRunnable != null) {
			this.manageExternalStorageAllowCmdRunnable = null;
			ForegroundService.HANDLER.removeCallbacks(manageExternalStorageAllowCmdRunnable);
		}
		var manageExternalStorageAllowCmdDialog = this.manageExternalStorageAllowCmdDialog;
		if (manageExternalStorageAllowCmdDialog != null) {
			this.manageExternalStorageAllowCmdDialog = null;
			manageExternalStorageAllowCmdDialog.dismiss();
		}
	}

	private void showManageExternalStorageAllowCmd() {
		hideManageExternalStorageAllowCmd();
		var manageExternalStorageAllowCmdDialog = new AlertDialog.Builder(this)
				.setTitle(R.string.app_name)
				.setIcon(R.mipmap.ic_launcher)
				.setMessage("adb shell appops set --uid " + getPackageName() + " MANAGE_EXTERNAL_STORAGE allow")
				.setPositiveButton(R.string.manage_external_storage_allow, (dialog, which) -> {
					if (!isExternalStorageManager()) {
						Toast.makeText(this, R.string.manage_external_storage_allow_message, Toast.LENGTH_SHORT).show();
					}
				})
				.setNegativeButton(R.string.manage_external_storage_deny, (dialog, which) ->
						setExternalStorageManager(!isExternalStorageManager()))
				.setNeutralButton(R.string.close_app, (dialog, which) ->
						finish())
				.setCancelable(false)
				.show();
		this.manageExternalStorageAllowCmdDialog = manageExternalStorageAllowCmdDialog;
		var manageExternalStorageAllowCmdRunnable = new Runnable() {

			@Override
			public void run() {
				if (manageExternalStorageAllowCmdDialog.isShowing() && !isExternalStorageManager()) {
					ForegroundService.HANDLER.postDelayed(this, 100L);
				} else if (requestRequestedPermissions() == null) {
					startForegroundServiceAndFinish();
				}
			}
		};
		ForegroundService.HANDLER.post(manageExternalStorageAllowCmdRunnable);
		this.manageExternalStorageAllowCmdRunnable = manageExternalStorageAllowCmdRunnable;
	}

	private boolean requestManageExternalStorage() {
		if (isExternalStorageManager()) {
			return false;
		}
		var uri = Uri.fromParts("package", getPackageName(), null);
		var intent = new Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
		if (intent.resolveActivity(getPackageManager()) != null) {
			startActivityForResult(intent, RequestCode.MANAGE_EXTERNAL_STORAGE.ordinal());
		} else {
			showManageExternalStorageAllowCmd();
		}
		return true;
	}

	private String[] requestRequestedPermissions() {
		try {
			var requestedPermissions = getPackageManager()
					.getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS)
					.requestedPermissions;
			if (requestedPermissions != null && requestedPermissions.length > 0) {
				var set = new HashSet<>(Arrays.asList(requestedPermissions));
				if (set.remove(MANAGE_EXTERNAL_STORAGE) &&
						requestManageExternalStorage()) {
					return new String[]{MANAGE_EXTERNAL_STORAGE};
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

	private void startForegroundServiceAndFinish() {
		var intent = new Intent(this, ForegroundService.class);
		try {
			startForegroundService(intent);
		} catch (Throwable t) {
			Log.w(TAG, t);
		}
		finish();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (requestRequestedPermissions() == null) {
			startForegroundServiceAndFinish();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		hideManageExternalStorageAllowCmd();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == RequestCode.REQUESTED_PERMISSIONS.ordinal()) {
			startForegroundServiceAndFinish();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == RequestCode.MANAGE_EXTERNAL_STORAGE.ordinal()) {
			setExternalStorageManager(!isExternalStorageManager());
		}
	}
}
