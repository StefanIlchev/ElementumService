package service.elementum.android;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Objects;
import java.util.Scanner;

public class DaemonRunnable implements Runnable {

	private static final String TAG = "DaemonRunnable";

	private final AssetManager assetManager;

	private final File assetsMarker;

	private final File nativeLibraryDir;

	private final File externalFilesDir;

	private volatile boolean isDestroyed = false;

	public DaemonRunnable(Context context) {
		assetManager = context.getAssets();
		assetsMarker = new File(context.getCodeCacheDir(), context.getPackageName());
		nativeLibraryDir = new File(context.getApplicationInfo().nativeLibraryDir);
		externalFilesDir = Objects.requireNonNull(context.getExternalFilesDir(null));
	}

	public boolean isDestroyed() {
		return isDestroyed;
	}

	public void destroy() {
		isDestroyed = true;
	}

	private String format(String format) {
		return String.format(
				format,
				nativeLibraryDir.getPath(),
				externalFilesDir.getPath());
	}

	private void extract(String src, File dst) throws Throwable {
		if (dst.exists()) {
			if (assetsMarker.exists() || isDestroyed()) {
				return;
			}
			try (var stream = Files.walk(dst.toPath())) {
				stream.sorted(Comparator.reverseOrder())
						.map(Path::toFile)
						.forEach(File::delete);
			}
		}
		var deque = new ArrayDeque<String>();
		for (String node = src, name = dst.getName(); node != null && !isDestroyed(); node = name = deque.pollFirst()) {
			var children = assetManager.list(node);
			if (children != null) {
				for (var child : children) {
					deque.add(node + "/" + child);
				}
			}
			var parent = dst.getParent();
			var path = parent != null ? Paths.get(parent, name) : Paths.get(name);
			try (var in = assetManager.open(node)) {
				Files.copy(in, path);
			} catch (FileNotFoundException ignore) {
				Files.createDirectories(path);
			}
		}
	}

	private void extract() throws Throwable {
		for (var entry : BuildConfig.SUBPROCESS_ASSETS.entrySet()) {
			extract(entry.getKey(), new File(format(entry.getValue())));
			if (isDestroyed()) {
				return;
			}
		}
		assetsMarker.mkdirs();
	}

	private ProcessBuilder build() {
		var command = new ArrayList<String>();
		for (var entry : BuildConfig.SUBPROCESS_CMD) {
			command.add(format(entry));
		}
		var builder = new ProcessBuilder(command)
				.directory(nativeLibraryDir)
				.redirectErrorStream(true);
		var environment = builder.environment();
		for (var entry : BuildConfig.SUBPROCESS_ENV.entrySet()) {
			environment.put(entry.getKey(), format(entry.getValue()));
		}
		return builder;
	}

	private void execute() throws Throwable {
		var builder = build();
		for (var attempt = 0; !isDestroyed(); Thread.sleep(BuildConfig.SUBPROCESS_RETRY_DELAY)) {
			var process = builder.start();
			try (var scanner = new Scanner(process.getInputStream())) {
				while (scanner.hasNextLine()) {
					var line = scanner.nextLine();
					Log.v(BuildConfig.SUBPROCESS_TAG, line);
					if (isDestroyed()) {
						process.destroy();
					}
				}
			}
			var exitValue = process.waitFor();
			Log.v(TAG, "subprocessExitValue = " + exitValue);
			if (BuildConfig.SUBPROCESS_EXIT_VALUES_END.contains(exitValue) || isDestroyed()) {
				break;
			}
			if (BuildConfig.SUBPROCESS_EXIT_VALUES_SKIP.contains(exitValue)) {
				continue;
			}
			if (BuildConfig.SUBPROCESS_EXIT_VALUES_START.contains(exitValue)) {
				attempt = 0;
			} else if (++attempt > BuildConfig.SUBPROCESS_RETRIES_COUNT) {
				break;
			}
		}
	}

	@Override
	public void run() {
		try {
			extract();
			execute();
		} catch (Throwable t) {
			Log.w(TAG, t);
		}
	}
}
