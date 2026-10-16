package moe.kirao.mgx.utils;

import android.widget.Toast;
import androidx.annotation.Nullable;
import org.drinkless.tdlib.TdApi;
import org.thunderdog.challegram.R;
import org.thunderdog.challegram.core.Lang;
import org.thunderdog.challegram.telegram.ConnectionState;
import org.thunderdog.challegram.telegram.PrivacySettingsListener;
import org.thunderdog.challegram.telegram.Tdlib;
import org.thunderdog.challegram.tool.UI;
import java.util.Arrays;
import java.util.function.BiConsumer;
import moe.kirao.mgx.MoexConfig;

/** Connects the account's serial privacy session to TDLib and private storage. */
public final class GhostOnlineManager implements MoexConfig.SettingsChangeListener, PrivacySettingsListener {
  public static final String KEY_CHANGED = "ghost_online_changed";
  private final Tdlib tdlib;
  private GhostOnlineSession session;
  private long sessionUserId = Long.MIN_VALUE;
  private volatile String error;
  private volatile boolean pending;

  public GhostOnlineManager (Tdlib tdlib) {
    this.tdlib = tdlib;
    MoexConfig.instance().addSettingsListener(this);
    tdlib.listeners().subscribeToPrivacyUpdates(this);
  }

  private GhostOnlineSession session () {
    long userId = tdlib.myUserId();
    if (session == null || sessionUserId != userId) {
      if (session != null) session.close();
      sessionUserId = userId;
      session = createSession(userId);
    }
    return session;
  }

  private GhostOnlineSession createSession (long userId) {
    return new GhostOnlineSession(new GhostOnlineSession.Backend() {
      private boolean isCurrent () { return sessionUserId == userId && tdlib.myUserId() == userId; }
      @Override public boolean available () { return isCurrent() && userId != 0 && tdlib.authorizationStatus() == Tdlib.Status.READY && tdlib.connectionState() == ConnectionState.CONNECTED; }
      @Override public boolean hideOnline () { return GhostModeUtils.shouldForceOffline(); }
      @Override public boolean onlineRequested () { return tdlib.isOnline(); }
      @Override public long[] users () { return exceptions(userId); }
      @Override public long[] load (String suffix) { return MoexConfig.instance().getLongArray(key(userId, suffix)); }
      @Override public void save (String suffix, long[] value) {
        if (value != null) MoexConfig.instance().putLongArray(key(userId, suffix), value);
        else MoexConfig.instance().remove(key(userId, suffix));
      }
      @Override public void getRules (BiConsumer<TdApi.UserPrivacySettingRules, TdApi.Error> callback) {
        tdlib.send(new TdApi.GetUserPrivacySettingRules(new TdApi.UserPrivacySettingShowStatus()), callback::accept);
      }
      @Override public void setRules (TdApi.UserPrivacySettingRules rules, BiConsumer<TdApi.Ok, TdApi.Error> callback) {
        tdlib.send(new TdApi.SetUserPrivacySettingRules(new TdApi.UserPrivacySettingShowStatus(), rules), callback::accept);
      }
      @Override public void setOnline (boolean online) {
        if (!isCurrent()) return;
        tdlib.client().send(new TdApi.SetOption("online", new TdApi.OptionValueBoolean(online)), tdlib.silentHandler());
      }
      @Override public void stateChanged (String failure, boolean isPending) {
        if (!isCurrent()) return;
        boolean newError = failure != null && !failure.equals(error);
        error = failure;
        pending = isPending;
        if (newError && tdlib.isOnline()) UI.showToast(Lang.getString(R.string.GhostOnlineError, failure), Toast.LENGTH_LONG);
        MoexConfig.instance().notifyClientListeners(KEY_CHANGED, tdlib.id(), null);
      }
    });
  }

  private String key (long userId, String suffix) {
    return "ghost_online_" + tdlib.id() + "_" + userId + "_" + suffix;
  }

  private long[] exceptions (long userId) {
    long[] ids = MoexConfig.instance().getLongArray(key(userId, "users"));
    return ids != null ? ids.clone() : new long[0];
  }

  public long[] exceptions () { return exceptions(tdlib.myUserId()); }

  public void setExceptions (long[] ids) {
    long userId = tdlib.myUserId();
    long[] users = Arrays.stream(ids).filter(id -> id > 0 && id != userId).distinct().sorted().limit(1000).toArray();
    MoexConfig.instance().putLongArray(key(userId, "users"), users);
    refresh();
  }

  @Nullable public String error () { return error; }
  public boolean pending () { return pending; }

  @Override
  public void onSettingsChanged (String key, Object value, Object old) {
    if (MoexConfig.KEY_GHOST_MODE.equals(key) || MoexConfig.KEY_GHOST_MODE_FLAGS.equals(key) || "experiments".equals(key)) refresh();
  }

  @Override
  public void onPrivacySettingRulesChanged (TdApi.UserPrivacySetting setting, TdApi.UserPrivacySettingRules rules) {
    if (setting instanceof TdApi.UserPrivacySettingShowStatus) session().onPrivacyChanged(rules);
  }

  public void refresh () { tdlib.runOnTdlibThread(() -> session().refresh()); }
}
