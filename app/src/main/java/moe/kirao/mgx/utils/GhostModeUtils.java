package moe.kirao.mgx.utils;

import moe.kirao.mgx.MoexConfig;
import org.thunderdog.challegram.unsorted.Settings;

/** Centralized, client-only privacy gates for the experimental Ghost mode. */
public final class GhostModeUtils {
  private GhostModeUtils () { }

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
