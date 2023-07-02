package service.elementum.android;

import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import ilchev.stefan.binarywrapper.BaseDaemonRunnable;

public class DaemonRunnable extends BaseDaemonRunnable {

	private static final String TAG = "DaemonRunnable";

	private static final String KEY_DATA = "xbmc.data";

	private static final Set<Integer> SUBPROCESS_EXIT_VALUES_END = Set.of(0, 1, 247);

	private static final Set<Integer> SUBPROCESS_EXIT_VALUES_SKIP = Set.of(255);

	private static final Set<Integer> SUBPROCESS_EXIT_VALUES_START = Set.of(5);

	private final Map<String, File> subprocessAssets;

	private final List<String> subprocessCmd;

	private final Map<String, String> subprocessEnv;

	private final File lockfile;

	private final String dataDefault;

	private final String dataReplacement;

	public DaemonRunnable(Context context, Handler mainHandler, String... subprocessArgs) {
		super(context, mainHandler);
		var filesDir = context.getFilesDir();
		var addonDir = new File(filesDir, ".kodi/addons/" + BuildConfig.ADDON_ID);
		subprocessAssets = Map.of(BuildConfig.ADDON_ID, addonDir);
		var subprocessCmd = new ArrayList<String>();
		subprocessCmd.add("./libelementum.so");
		if (subprocessArgs != null) {
			Collections.addAll(subprocessCmd, subprocessArgs);
		}
		this.subprocessCmd = Collections.unmodifiableList(subprocessCmd);
		subprocessEnv = Map.of("LD_LIBRARY_PATH", context.getApplicationInfo().nativeLibraryDir);
		lockfile = new File(addonDir, ".lockfile");
		var externalFilesDir = Objects.requireNonNull(context.getExternalFilesDir(null));
		dataDefault = externalFilesDir.getPath().replace(context.getPackageName(), BuildConfig.KODI_ID);
		dataReplacement = filesDir.getPath();
	}

	@SuppressWarnings({"deprecation", "RedundantSuppression"})
	private String loadData() throws Exception {
		var properties = new Properties();
		var xbmcEnvFile = new File(Environment.getExternalStorageDirectory(), "xbmc_env.properties");
		properties.setProperty(KEY_DATA, dataDefault);
		if (xbmcEnvFile.isFile()) {
			try (var in = new FileInputStream(xbmcEnvFile)) {
				properties.load(in);
			}
		}
		return properties.getProperty(KEY_DATA);
	}

	private void writeData() {
		try {
			var data = loadData();
			var dataDir = new File(data.replace(BuildConfig.KODI_DATA_DIR, BuildConfig.DATA_DIR));
			var dataPath = Paths.get(dataDir.getPath(), BuildConfig.PROJECT_NAME);
			dataDir.mkdirs();
			Files.write(dataPath, List.of(data, dataReplacement));
		} catch (Throwable t) {
			Log.w(TAG, t);
		}
	}

	@Override
	protected Map<String, File> getSubprocessAssets() {
		return subprocessAssets;
	}

	@Override
	protected List<String> getSubprocessCmd() {
		return subprocessCmd;
	}

	@Override
	protected Map<String, String> getSubprocessEnv() {
		return subprocessEnv;
	}

	@Override
	protected Set<Integer> getSubprocessExitValuesEnd() {
		return SUBPROCESS_EXIT_VALUES_END;
	}

	@Override
	protected Set<Integer> getSubprocessExitValuesSkip() {
		return SUBPROCESS_EXIT_VALUES_SKIP;
	}

	@Override
	protected Set<Integer> getSubprocessExitValuesStart() {
		return SUBPROCESS_EXIT_VALUES_START;
	}

	@Override
	protected int getSubprocessRetriesCount() {
		return 3;
	}

	@Override
	protected long getSubprocessRetryDelay() {
		return 5_000L;
	}

	@Override
	protected String getSubprocessTag() {
		return BuildConfig.ADDON_ID;
	}

	@Override
	public void run() {
		writeData();
		lockfile.delete();
		super.run();
	}
}
