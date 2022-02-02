package service.elementum.android;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
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

	private MediaSession mediaSession = null;

	private void startForeground(PendingIntent stopIntent) {
		var appName = getString(R.string.app_name);
		var closeApp = getString(R.string.close_app);
		var notification = new Notification.Builder(this, TAG)
				.setSmallIcon(R.mipmap.ic_launcher)
				.setContentTitle(appName)
				.setContentText(closeApp)
				.setContentIntent(stopIntent)
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

	private void stopMediaSession() {
		var mediaSession = this.mediaSession;
		if (mediaSession != null) {
			this.mediaSession = null;
			mediaSession.release();
		}
	}

	private void startMediaSession(PendingIntent stopIntent) {
		stopMediaSession();
		var icLauncher = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
		var appName = getString(R.string.app_name);
		var closeApp = getString(R.string.close_app);
		var mediaSession = new MediaSession(this, TAG);
		this.mediaSession = mediaSession;
		mediaSession.setMetadata(new MediaMetadata.Builder()
				.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, icLauncher)
				.putString(MediaMetadata.METADATA_KEY_TITLE, appName)
				.putString(MediaMetadata.METADATA_KEY_ARTIST, closeApp)
				.build());
		mediaSession.setPlaybackState(new PlaybackState.Builder()
				.setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0F)
				.build());
		mediaSession.setSessionActivity(stopIntent);
		mediaSession.setActive(true);
	}

	@Override
	public void onCreate() {
		try {
			var stopIntent = PendingIntent.getActivity(
					this,
					NOTIFICATION_ID,
					new Intent(this, MainActivity.class),
					PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
			startForeground(stopIntent);
			try {
				startDaemon();
			} catch (Throwable t) {
				stopForeground();
				throw t;
			}
			startMediaSession(stopIntent);
		} catch (Throwable t) {
			Log.w(TAG, t);
		}
	}

	@Override
	public void onDestroy() {
		try {
			stopDaemon();
			stopMediaSession();
		} catch (Throwable t) {
			Log.w(TAG, t);
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
}
