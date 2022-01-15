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
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Scanner;

public class DaemonRunnable implements Runnable {

	private final File bin;

	private final File assetsMarker;

	private final AssetManager assetManager;

	private final Map<String, File> assets;

	private volatile boolean isDestroyed = false;

	public DaemonRunnable(Context context) {
		var externalFilesDir = context.getExternalFilesDir(null);
		var assetsDir = externalFilesDir != null ? new File(externalFilesDir, BuildConfig.ASSETS_DIR) : null;
		var addonAssetsDir = assetsDir != null ? new File(assetsDir, BuildConfig.ADDON_ASSETS_DIR) : null;
		bin = new File(context.getApplicationInfo().nativeLibraryDir);
		assetsMarker = new File(context.getCodeCacheDir(), context.getPackageName());
		assetManager = context.getAssets();
		assets = addonAssetsDir == null ? Collections.emptyMap() : Collections.singletonMap(
				BuildConfig.ADDON_ID, addonAssetsDir
		);
	}

	public boolean isDestroyed() {
		return isDestroyed;
	}

	public void destroy() {
		isDestroyed = true;
	}

	private void extract(String src, File dst) throws Throwable {
		var node = src;
		var name = dst.getName();
		var parent = dst.getParent();
		if (dst.exists()) {
			try (var stream = Files.walk(dst.toPath())) {
				stream.sorted(Comparator.reverseOrder())
						.map(Path::toFile)
						.forEach(File::delete);
			}
		}
		for (var deque = new ArrayDeque<String>(); node != null && !isDestroyed(); node = name = deque.pollFirst()) {
			var children = assetManager.list(node);
			if (children != null) {
				for (var child : children) {
					deque.add(node + "/" + child);
				}
			}
			var path = parent != null ? Paths.get(parent, name) : Paths.get(name);
			try (var in = assetManager.open(node)) {
				Files.copy(in, path);
			} catch (FileNotFoundException ignore) {
				Files.createDirectories(path);
			}
		}
	}

	private void extract() throws Throwable {
		if (assetsMarker.exists()) {
			return;
		}
		for (var entry : assets.entrySet()) {
			if (isDestroyed()) {
				return;
			}
			extract(entry.getKey(), entry.getValue());
		}
		if (isDestroyed()) {
			return;
		}
		var parentFile = assetsMarker.getParentFile();
		if (parentFile != null) {
			parentFile.mkdirs();
		}
		assetsMarker.createNewFile();
	}

	private void execute() throws Throwable {
		for (var attempt = 0; !isDestroyed(); Thread.sleep(5_000L)) {
			var builder = new ProcessBuilder(BuildConfig.SUBPROCESS_CMD)
					.directory(bin)
					.redirectErrorStream(true);
			var environment = builder.environment();
			environment.putAll(BuildConfig.SUBPROCESS_ENV);
			environment.put("LD_LIBRARY_PATH", bin.getPath());
			var process = builder.start();
			try (var scanner = new Scanner(process.getInputStream())) {
				while (scanner.hasNextLine()) {
					var line = scanner.nextLine();
					Log.v(BuildConfig.ADDON_ID, line);
					if (isDestroyed()) {
						process.destroy();
					}
				}
			}
			var exitValue = process.waitFor();
			Log.v(BuildConfig.ADDON_ID, "exitValue = " + exitValue);
			if (isDestroyed()) {
				return;
			}
			switch (exitValue) {
				case 5:
					attempt = 0;
				case -1:
				case 0:
					break;
				case -9:
				case 1:
					return;
				default:
					if (++attempt > 3) {
						return;
					}
			}
		}
	}

	@Override
	public void run() {
		try {
			extract();
			execute();
		} catch (Throwable t) {
			Log.w(BuildConfig.ADDON_ID, t);
		}
	}
}
