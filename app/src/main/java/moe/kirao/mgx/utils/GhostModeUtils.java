package moe.kirao.mgx.utils;

import moe.kirao.mgx.MoexConfig;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.navigation.ViewController;
import org.thunderdog.challegram.unsorted.Settings;

/** Centralized, client-only privacy gates for the experimental Ghost mode. */
public final class GhostModeUtils {
  private GhostModeUtils () { }

  private static final String KEY_WARNING_ACCEPTED = "ghost_warning_accepted";

  public static void toggleWithWarning (ViewController<?> controller, Runnable after) {
    MoexConfig config = MoexConfig.instance();
    Runnable toggle = () -> {
      if (controller.isDestroyed()) return;
      config.putBoolean(KEY_WARNING_ACCEPTED, true);
      config.toggleGhostMode();
      after.run();
    };
    // A persisted toggle also recognizes users who used Ghost mode before this warning existed.
    if (!MoexConfig.ghostMode && !config.containsKey(MoexConfig.KEY_GHOST_MODE) && !config.getBoolean(KEY_WARNING_ACCEPTED, false)) {
      controller.showConfirm(Lang.getString(R.string.GhostModeWarning), Lang.getString(R.string.GhostModeEnable), toggle);
    } else {
      toggle.run();
    }
  }

  private static boolean isEnabled (int flag) {
    return Settings.instance().isExperimentEnabled(Settings.EXPERIMENT_FLAG_ALLOW_EXPERIMENTS)
      && MoexConfig.ghostMode
      && (MoexConfig.instance().getGhostModeFlags() & flag) != 0;
  }

  public static boolean isFlagEnabled (int enabledFlags, int flag) {
    return (enabledFlags & flag) != 0;
  }

  public static int flagsForEnabling (int flags, int previousFlags) {
    flags &= MoexConfig.GHOST_MODE_ALL;
    if (flags != 0) return flags;
    previousFlags &= MoexConfig.GHOST_MODE_ALL;
    return previousFlags != 0 ? previousFlags : MoexConfig.GHOST_MODE_ALL;
  }

  public static boolean shouldSuppressRead () {
    return isEnabled(MoexConfig.GHOST_NO_READ);
  }

  public static boolean shouldSuppressTyping () {
    return isEnabled(MoexConfig.GHOST_NO_TYPING);
  }

  public static boolean shouldForceOffline () {
    return isEnabled(MoexConfig.GHOST_OFFLINE);
  }
}
