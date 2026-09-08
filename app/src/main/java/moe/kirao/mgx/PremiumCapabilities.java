package moe.kirao.mgx;

import org.thunderdog.challegram.unsorted.Settings;

/** Centralized client-only Premium capability policy. Server state remains authoritative. */
public final class PremiumCapabilities {
  private PremiumCapabilities () { }

  public static boolean localExperimentEnabled () {
    return Settings.instance().isLocalPremiumExperimentEnabled();
  }

  public static boolean effective (boolean serverPremium) {
    return serverPremium || localExperimentEnabled();
  }

  public static boolean localOnly (boolean serverPremium) {
    return localExperimentEnabled() && !serverPremium;
  }

  /** Local-only operations must never be sent as if Telegram had granted Premium. */
  public static boolean allowServerOperation (boolean serverPremium) {
    return serverPremium;
  }
}
