package service.elementum.android;

import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInstaller;
import android.graphics.BitmapFactory;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ForegroundService extends Service {

	private static final String TAG = "ForegroundService";

	private static final int NOTIFICATION_ID = 1;

	private static final Executor WORK_EXECUTOR = Executors.newSingleThreadExecutor();

	public static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

	private static Uri toUri(String versionName) {
		var abi = Build.SUPPORTED_ABIS[0];
		var fileName = String.join("-", BuildConfig.PROJECT_NAME, abi, BuildConfig.BUILD_TYPE, versionName);
		return Uri.parse(BuildConfig.REPO_URL + fileName + ".apk");
	}

	private DaemonRunnable daemonRunnable = null;

	private volatile String updateVersionName = null;

	private String updateVersionNameMsg = null;

	private BroadcastReceiver updateInstallReceiver = null;

	private volatile int updateInstallId = 0;

	private BroadcastReceiver updateDownloadReceiver = null;

	private long updateDownloadId = 0L;

	private MediaSession mediaSession = null;

	private void tryStartActivity(Intent intent, Bundle options) {
		try {
			startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options);
		} catch (Throwable t) {
			Log.w(TAG, t);
		}
	}

	private void startForeground(PendingIntent stopIntent) {
		var appName = getString(R.string.app_name);
		var stop = getString(R.string.stop);
		var builder = new Notification.Builder(this, TAG)
				.setSmallIcon(R.mipmap.ic_launcher)
				.setContentTitle(appName)
				.setContentText(stop)
				.setContentIntent(stopIntent);
		var manager = getSystemService(NotificationManager.class);
		if (manager != null) {
			var channel = new NotificationChannel(TAG, appName, NotificationManager.IMPORTANCE_LOW);
			manager.createNotificationChannel(channel);
		}
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
			builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
		}
		startForeground(NOTIFICATION_ID, builder.build());
	}

	private void stopForeground() {
		updateVersionNameMsg = updateVersionName;
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

	private void deleteUpdateDir() {
		var updateDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS);
		if (updateDir == null || !updateDir.exists()) {
			return;
		}
		try (var stream = Files.walk(updateDir.toPath())) {
			stream.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(File::delete);
		} catch (Throwable t) {
			Log.w(TAG, t);
		}
	}

	private void stopUpdateInstall() {
		var updateInstallReceiver = this.updateInstallReceiver;
		if (updateInstallReceiver != null) {
			this.updateInstallReceiver = null;
			unregisterReceiver(updateInstallReceiver);
		}
		var updateInstallId = this.updateInstallId;
		if (updateInstallId != 0) {
			this.updateInstallId = 0;
			getPackageManager().getPackageInstaller().abandonSession(updateInstallId);
		}
	}

	private void updateInstall(File file) throws Exception {
		var installer = getPackageManager().getPackageInstaller();
		var params = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
		params.setSize(file.length());
		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
			params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
		}
		var updateInstallId = installer.createSession(params);
		this.updateInstallId = updateInstallId;
		try (var session = installer.openSession(updateInstallId)) {
			try (var out = session.openWrite(file.getName(), 0L, file.length())) {
				Files.copy(file.toPath(), out);
				session.fsync(out);
			}
			var statusReceiver = PendingIntent.getBroadcast(
					this,
					updateInstallId,
					new Intent(TAG),
					Build.VERSION.SDK_INT > Build.VERSION_CODES.R ? PendingIntent.FLAG_MUTABLE : 0)
					.getIntentSender();
			session.commit(statusReceiver);
		}
	}

	private void startUpdateInstall(long updateDownloadId) {
		stopUpdateInstall();
		var manager = getSystemService(DownloadManager.class);
		if (manager == null) {
			stopForeground();
			return;
		}
		var receiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				var sessionId = intent != null ? intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, 0) : 0;
				if (sessionId == 0 || sessionId != updateInstallId) {
					return;
				}
				updateInstallId = 0;
				updateInstallReceiver = null;
				unregisterReceiver(this);
				var status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
				Intent userAction = status == PackageInstaller.STATUS_PENDING_USER_ACTION
						? intent.getParcelableExtra(Intent.EXTRA_INTENT)
						: null;
				if (userAction != null) {
					updateVersionName = null;
					tryStartActivity(userAction, null);
				} else if (status == PackageInstaller.STATUS_SUCCESS) {
					updateVersionName = null;
				}
				stopForeground();
			}
		};
		registerReceiver(receiver, new IntentFilter(TAG));
		updateInstallReceiver = receiver;
		WORK_EXECUTOR.execute(() -> {
			var query = new DownloadManager.Query().setFilterById(updateDownloadId);
			try (var cursor = manager.query(query)) {
				var uri = cursor != null ? manager.getUriForDownloadedFile(updateDownloadId) : null;
				if (uri != null && cursor.moveToFirst()) {
					var localUri = cursor.getString(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_LOCAL_URI));
					updateInstall(new File(Uri.parse(localUri).getPath()));
					return;
				}
			} catch (Throwable t) {
				Log.w(TAG, t);
			} finally {
				deleteUpdateDir();
			}
			MAIN_HANDLER.post(this::stopForeground);
		});
	}

	private int stopUpdateDownload() {
		var updateDownloadReceiver = this.updateDownloadReceiver;
		if (updateDownloadReceiver != null) {
			this.updateDownloadReceiver = null;
			unregisterReceiver(updateDownloadReceiver);
		}
		var updateDownloadId = this.updateDownloadId;
		if (updateDownloadId != 0L) {
			this.updateDownloadId = 0L;
			var manager = getSystemService(DownloadManager.class);
			return manager != null ? manager.remove(updateDownloadId) : 0;
		}
		return 0;
	}

	private void startUpdateDownload(String versionName) {
		stopUpdateDownload();
		var receiver = new BroadcastReceiver() {

			@Override
			public void onReceive(Context context, Intent intent) {
				var action = intent != null ? intent.getAction() : null;
				if (DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(action)) {
					var id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0L);
					if (id != 0L && id == updateDownloadId) {
						updateDownloadId = 0L;
						updateDownloadReceiver = null;
						unregisterReceiver(this);
						startUpdateInstall(id);
					}
				} else if (DownloadManager.ACTION_NOTIFICATION_CLICKED.equals(action)) {
					var id = updateDownloadId;
					var ids = intent.getLongArrayExtra(DownloadManager.EXTRA_NOTIFICATION_CLICK_DOWNLOAD_IDS);
					if (id != 0L && ids != null && Arrays.stream(ids).anyMatch(it -> it == id)) {
						stopForeground();
					}
				}
			}
		};
		var filter = new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE);
		filter.addAction(DownloadManager.ACTION_NOTIFICATION_CLICKED);
		registerReceiver(receiver, filter);
		updateDownloadReceiver = receiver;
		var uri = toUri(versionName);
		var appName = getString(R.string.app_name);
		var stop = getString(R.string.stop);
		var request = new DownloadManager.Request(uri)
				.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, uri.getLastPathSegment())
				.setTitle(appName)
				.setDescription(stop);
		var manager = getSystemService(DownloadManager.class);
		var updateDownloadId = manager != null ? manager.enqueue(request) : 0L;
		if (updateDownloadId == 0L) {
			stopForeground();
			return;
		}
		WORK_EXECUTOR.execute(() -> {
			var query = new DownloadManager.Query().setFilterById(updateDownloadId);
			try {
				for (; updateVersionName != null; Thread.sleep(1000L)) {
					try (var cursor = manager.query(query)) {
						var status = cursor != null && cursor.moveToFirst()
								? cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
								: DownloadManager.STATUS_FAILED;
						switch (status) {
							case DownloadManager.STATUS_FAILED:
								MAIN_HANDLER.post(() -> {
									if (updateVersionName != null) {
										stopForeground();
									}
								});
							case DownloadManager.STATUS_SUCCESSFUL:
								return;
						}
					}
				}
			} catch (Throwable t) {
				Log.w(TAG, t);
			}
		});
		this.updateDownloadId = updateDownloadId;
	}

	private void stopUpdate() {
		updateVersionName = null;
		var updateVersionNameMsg = this.updateVersionNameMsg;
		this.updateVersionNameMsg = null;
		if (updateVersionNameMsg != null && !BuildConfig.VERSION_NAME.equals(updateVersionNameMsg)) {
			Toast.makeText(this, BuildConfig.VERSION_NAME + " =/= " + updateVersionNameMsg, Toast.LENGTH_LONG).show();
		}
		stopUpdateDownload();
		stopUpdateInstall();
		deleteUpdateDir();
	}

	private void startUpdate(String versionName) {
		stopUpdate();
		updateVersionName = versionName;
		startUpdateDownload(versionName);
	}

	private void stopMediaSession() {
		var mediaSession = this.mediaSession;
		if (mediaSession != null) {
			this.mediaSession = null;
			mediaSession.setActive(false);
			mediaSession.release();
		}
	}

	private void startMediaSession(PendingIntent stopIntent) {
		stopMediaSession();
		var icLauncher = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);
		var appName = getString(R.string.app_name);
		var stop = getString(R.string.stop);
		var mediaSession = new MediaSession(this, TAG);
		this.mediaSession = mediaSession;
		mediaSession.setMetadata(new MediaMetadata.Builder()
				.putBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON, icLauncher)
				.putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, appName)
				.putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, stop)
				.putString(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION, stop)
				.putString(MediaMetadata.METADATA_KEY_TITLE, appName)
				.putString(MediaMetadata.METADATA_KEY_ARTIST, stop)
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
					PendingIntent.FLAG_IMMUTABLE);
			startForeground(stopIntent);
			startMediaSession(stopIntent);
		} catch (Throwable t) {
			Log.w(TAG, t);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		var data = intent.getData();
		var versionName = data != null ? data.getSchemeSpecificPart() : null;
		try {
			if (versionName == null || BuildConfig.VERSION_NAME.equals(versionName)) {
				if (daemonRunnable == null) {
					stopUpdate();
					startDaemon();
				}
			} else if (!versionName.equals(updateVersionName)) {
				stopDaemon();
				startUpdate(versionName);
			}
		} catch (Throwable t) {
			Log.w(TAG, t);
			stopForeground();
		}
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		try {
			stopDaemon();
			stopUpdate();
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
