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
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.util.Arrays;
import java.util.HashSet;
import java.util.function.Consumer;
import java.util.function.Supplier;

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

	@SuppressLint("InlinedApi")
	private static final String UPDATE_PACKAGES_WITHOUT_USER_ACTION =
			Manifest.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION;

	private Runnable allowCmdRunnable = null;

	private Dialog allowCmdDialog = null;

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

	private boolean isActivityFound(Intent intent) {
		try {
			var activityInfo = intent.resolveActivityInfo(getPackageManager(), 0);
			return activityInfo != null && activityInfo.isEnabled() && activityInfo.exported;
		} catch (Throwable t) {
			Log.w(TAG, t);
		}
		return false;
	}

	private void tryStartActivityForResult(Intent intent, int requestCode, Bundle options) {
		try {
			startActivityForResult(intent, requestCode, options);
		} catch (Throwable t) {
			Log.w(TAG, t);
		}
	}

	private void hideAllowCmd() {
		var allowCmdRunnable = this.allowCmdRunnable;
		if (allowCmdRunnable != null) {
			this.allowCmdRunnable = null;
			ForegroundService.MAIN_HANDLER.removeCallbacks(allowCmdRunnable);
		}
		var allowCmdDialog = this.allowCmdDialog;
		if (allowCmdDialog != null) {
			this.allowCmdDialog = null;
			allowCmdDialog.dismiss();
		}
	}

	private void showAllowCmd(String permission, Supplier<Boolean> supplier, Consumer<Boolean> consumer) {
		hideAllowCmd();
		var allowCmdDialog = new AlertDialog.Builder(this)
				.setIcon(R.mipmap.ic_launcher)
				.setTitle(R.string.app_name)
				.setMessage("adb shell appops set --uid " + getPackageName() + " " + permission + " allow")
				.setPositiveButton(R.string.allow, (dialog, which) -> {
					if (!supplier.get()) {
						Toast.makeText(this, R.string.allow_message, Toast.LENGTH_SHORT).show();
					}
				})
				.setNegativeButton(R.string.deny, (dialog, which) ->
						consumer.accept(!supplier.get()))
				.setNeutralButton(R.string.close_app, (dialog, which) ->
						finish())
				.setCancelable(false)
				.show();
		this.allowCmdDialog = allowCmdDialog;
		var allowCmdRunnable = new Runnable() {

			@Override
			public void run() {
				if (isFinishing() || isDestroyed()) {
					return;
				}
				if (getIntent().getAction() == null) {
					callForegroundServiceAndFinish();
				} else if (allowCmdDialog.isShowing() && !supplier.get()) {
					ForegroundService.MAIN_HANDLER.postDelayed(this, 100L);
				} else if (requestRequestedPermissions() == null) {
					callForegroundServiceAndFinish();
				}
			}
		};
		ForegroundService.MAIN_HANDLER.post(allowCmdRunnable);
		this.allowCmdRunnable = allowCmdRunnable;
	}

	private boolean requestManageExternalStorage() {
		if (isExternalStorageManager()) {
			return false;
		}
		var uri = Uri.fromParts("package", getPackageName(), null);
		var intent = new Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, uri);
		if (isActivityFound(intent) ||
				isActivityFound(intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS))) {
			tryStartActivityForResult(intent, RequestCode.MANAGE_EXTERNAL_STORAGE.ordinal(), null);
		} else {
			showAllowCmd("MANAGE_EXTERNAL_STORAGE", this::isExternalStorageManager, this::setExternalStorageManager);
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
				set.remove(Manifest.permission.REQUEST_INSTALL_PACKAGES);
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
					set.remove(FOREGROUND_SERVICE);
				}
				if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
					set.remove(UPDATE_PACKAGES_WITHOUT_USER_ACTION);
				}
				if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
					set.remove(Manifest.permission.READ_EXTERNAL_STORAGE);
					set.remove(Manifest.permission.WRITE_EXTERNAL_STORAGE);
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

	private void callForegroundServiceAndFinish() {
		var intent = new Intent(getIntent()).setClass(this, ForegroundService.class);
		try {
			if (intent.getAction() == null) {
				stopService(intent);
			} else {
				startForegroundService(intent);
			}
		} catch (Throwable t) {
			Log.w(TAG, t);
		}
		finish();
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (getIntent().getAction() == null ||
				requestRequestedPermissions() == null) {
			callForegroundServiceAndFinish();
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		hideAllowCmd();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
		if (requestCode == RequestCode.REQUESTED_PERMISSIONS.ordinal()) {
			callForegroundServiceAndFinish();
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == RequestCode.MANAGE_EXTERNAL_STORAGE.ordinal()) {
			setExternalStorageManager(!isExternalStorageManager());
		}
	}

	@Override
	protected void onNewIntent(Intent intent) {
		setIntent(intent);
	}
}
