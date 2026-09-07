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
