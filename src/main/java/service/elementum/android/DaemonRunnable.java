package service.elementum.android;

import android.content.Context;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import ilchev.stefan.binarywrapper.BaseDaemonRunnable;

@SuppressWarnings("Java9CollectionFactory")
public class DaemonRunnable extends BaseDaemonRunnable {

	private static final Set<Integer> SUBPROCESS_EXIT_VALUES_END = Collections.unmodifiableSet(new HashSet<>(
			Arrays.asList(-9, 1)
	));

	private static final Set<Integer> SUBPROCESS_EXIT_VALUES_SKIP = Collections.unmodifiableSet(new HashSet<>(
			Arrays.asList(-1, 0)
	));

	private static final Set<Integer> SUBPROCESS_EXIT_VALUES_START = Collections.singleton(5);

	private final Map<String, File> subprocessAssets;

	private final List<String> subprocessCmd;

	private final Map<String, String> subprocessEnv;

	private final File lockFile;

	public DaemonRunnable(Context context, String... subprocessArgs) {
		super(context);
		var externalFilesDir = Objects.requireNonNull(context.getExternalFilesDir(null));
		var nativeLibraryDir = context.getApplicationInfo().nativeLibraryDir;
		var addonDir = new File(externalFilesDir, ".kodi/addons/" + BuildConfig.ADDON_ID);
		subprocessAssets = Collections.unmodifiableMap(new HashMap<>() {{
			put(BuildConfig.ADDON_ID, addonDir);
		}});
		subprocessCmd = Collections.unmodifiableList(new ArrayList<>() {{
			add("./libelementum.so");
			if (subprocessArgs != null) {
				Collections.addAll(this, subprocessArgs);
			}
		}});
		subprocessEnv = Collections.unmodifiableMap(new HashMap<>() {{
			put("LD_LIBRARY_PATH", nativeLibraryDir);
		}});
		lockFile = new File(addonDir, ".lockfile");
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
		lockFile.delete();
		super.run();
	}
}
