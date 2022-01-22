package service.elementum.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ForegroundService extends Service {

	private static final String TAG = "ForegroundService";

	private static final int NOTIFICATION_ID = 1;

	private static final Executor WORK_EXECUTOR = Executors.newSingleThreadExecutor();

	public static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

	private DaemonRunnable daemonRunnable = null;

	private void startForeground() {
		var appName = getString(R.string.app_name);
		var notification = new Notification.Builder(this, TAG)
				.setContentTitle(appName)
				.setSmallIcon(R.mipmap.ic_launcher)
				.build();
		var notificationManager = getSystemService(NotificationManager.class);
		if (notificationManager != null) {
			var notificationChannel = new NotificationChannel(TAG, appName, NotificationManager.IMPORTANCE_LOW);
			notificationManager.createNotificationChannel(notificationChannel);
		}
		startForeground(NOTIFICATION_ID, notification);
	}

	private void stopForeground() {
		stopForeground(STOP_FOREGROUND_REMOVE);
		stopSelf();
	}

	private void stopDaemon() {
		var daemonRunnable = this.daemonRunnable;
		if (daemonRunnable != null) {
			this.daemonRunnable = null;
			daemonRunnable.destroy();
		}
	}

	private void startDaemon() {
		stopDaemon();
		var daemonRunnable = new DaemonRunnable(this);
		WORK_EXECUTOR.execute(() -> {
			daemonRunnable.run();
			MAIN_HANDLER.post(() -> {
				if (!daemonRunnable.isDestroyed()) {
					stopForeground();
				}
			});
		});
		this.daemonRunnable = daemonRunnable;
	}

	@Override
	public void onCreate() {
		try {
			startForeground();
			try {
				startDaemon();
			} catch (Throwable t) {
				stopForeground();
				throw t;
			}
		} catch (Throwable t) {
			Log.w(TAG, t);
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
