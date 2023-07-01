package service.elementum.android;

import android.content.Context;
import android.os.Handler;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ilchev.stefan.binarywrapper.BaseDaemonRunnable;

public class DaemonRunnable extends BaseDaemonRunnable {

	private static final Set<Integer> SUBPROCESS_EXIT_VALUES_END = Set.of(0, 1, 247);

	private static final Set<Integer> SUBPROCESS_EXIT_VALUES_SKIP = Set.of(255);

	private static final Set<Integer> SUBPROCESS_EXIT_VALUES_START = Set.of(5);

	private final Map<String, File> subprocessAssets;

	private final List<String> subprocessCmd;

	private final Map<String, String> subprocessEnv;

	private final File lockfile;

	public DaemonRunnable(Context context, Handler mainHandler, String... subprocessArgs) {
		super(context, mainHandler);
		var addonDir = new File(context.getFilesDir(), ".kodi/addons/" + BuildConfig.ADDON_ID);
		subprocessAssets = Map.of(BuildConfig.ADDON_ID, addonDir);
		var subprocessCmd = new ArrayList<String>();
		subprocessCmd.add("./libelementum.so");
		if (subprocessArgs != null) {
			Collections.addAll(subprocessCmd, subprocessArgs);
		}
		this.subprocessCmd = Collections.unmodifiableList(subprocessCmd);
		subprocessEnv = Map.of("LD_LIBRARY_PATH", context.getApplicationInfo().nativeLibraryDir);
		lockfile = new File(addonDir, ".lockfile");
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
		lockfile.delete();
		super.run();
	}
}
