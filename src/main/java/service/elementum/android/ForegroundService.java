package service.elementum.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.File;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ForegroundService extends Service {

	private static final String CHANNEL_ID = "ForegroundService";

	private static final int NOTIFICATION_ID = 1;

	private static final Executor EXECUTOR = Executors.newSingleThreadExecutor();

	private static final Handler HANDLER = new Handler(Looper.getMainLooper());

	private DaemonRunnable daemonRunnable = null;

	private void startForeground() {
		var appName = getString(R.string.app_name);
		var notification = new Notification.Builder(this, CHANNEL_ID)
				.setContentTitle(appName)
				.setSmallIcon(R.mipmap.ic_launcher)
				.build();
		var notificationManager = getSystemService(NotificationManager.class);
		if (notificationManager != null) {
			var notificationChannel = new NotificationChannel(CHANNEL_ID, appName, NotificationManager.IMPORTANCE_LOW);
			notificationManager.createNotificationChannel(notificationChannel);
		}
		startForeground(NOTIFICATION_ID, notification);
	}

	private void stopForeground() {
		stopForeground(STOP_FOREGROUND_REMOVE);
		stopSelf();
	}

	private void startDaemon() {
		var bin = new File(getApplicationInfo().nativeLibraryDir);
		var assetsMarker = new File(getCodeCacheDir(), getPackageName());
		var externalFilesDir = getExternalFilesDir(null);
		var assetsDir = externalFilesDir != null ? new File(externalFilesDir, BuildConfig.ASSETS_DIR) : null;
		var addonAssetsDir = assetsDir != null ? new File(assetsDir, BuildConfig.ADDON_ASSETS_DIR) : null;
		var assets = addonAssetsDir != null ? Collections.singletonMap(BuildConfig.ADDON_ID, addonAssetsDir) : null;
		var daemonRunnable = new DaemonRunnable(bin, assetsMarker, getAssets(), assets);
		this.daemonRunnable = daemonRunnable;
		EXECUTOR.execute(() -> {
			daemonRunnable.run();
			HANDLER.post(() -> {
				if (!daemonRunnable.isDestroyed()) {
					stopForeground();
				}
			});
		});
	}

	private void stopDaemon() {
		var daemonRunnable = this.daemonRunnable;
		if (daemonRunnable != null) {
			daemonRunnable.destroy();
		}
	}

	@Override
	public void onCreate() {
		try {
			startForeground();
			try {
				startDaemon();
			} catch (Throwable ignore) {
				stopForeground();
			}
		} catch (Throwable ignore) {
		}
	}

	@Override
	public void onDestroy() {
		stopDaemon();
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
