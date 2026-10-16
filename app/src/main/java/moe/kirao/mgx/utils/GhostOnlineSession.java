package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;
import org.drinkless.tdlib.TdApi;
import java.util.function.BiConsumer;

/** Account-local privacy transaction. All calls and callbacks use one serial queue. */
public final class GhostOnlineSession {
  public interface Backend {
    boolean available ();
    boolean hideOnline ();
    boolean onlineRequested ();
    long[] users ();
    @Nullable long[] load (String key);
    void save (String key, @Nullable long[] value);
    void getRules (BiConsumer<TdApi.UserPrivacySettingRules, TdApi.Error> callback);
    void setRules (TdApi.UserPrivacySettingRules rules, BiConsumer<TdApi.Ok, TdApi.Error> callback);
    void setOnline (boolean online);
    void stateChanged (@Nullable String error, boolean pending);
  }

  private final Backend backend;
  private long revision;
  private boolean busy;
  private boolean closed;
  private TdApi.UserPrivacySettingRules expected;
  private TdApi.UserPrivacySettingRules foreignRules;

  public GhostOnlineSession (Backend backend) { this.backend = backend; }

  private boolean selective () { return backend.hideOnline() && backend.users().length > 0; }
  private TdApi.UserPrivacySettingRules snapshot (String key) { return GhostPrivacyRules.decode(backend.load(key)); }
  private void save (String key, TdApi.UserPrivacySettingRules rules) { backend.save(key, GhostPrivacyRules.encode(rules)); }
  private void clear () { backend.save("backup", null); backend.save("applied", null); }

  public void refresh () {
    if (closed) return;
    revision++;
    backend.setOnline(false);
    backend.stateChanged(null, selective() || backend.load("backup") != null);
    reconcile();
  }

  public void onPrivacyChanged (TdApi.UserPrivacySettingRules rules) {
    if (closed) return;
    if (!selective() && backend.load("backup") == null) return;
    TdApi.UserPrivacySettingRules applied = snapshot("applied");
    if (expected != null && GhostPrivacyRules.equals(expected, rules) || expected == null && applied != null && GhostPrivacyRules.equals(applied, rules)) return;
    if (busy) {
      foreignRules = rules;
      save("backup", rules);
      revision++;
      backend.setOnline(false);
    } else refresh();
  }

  private void reconcile () {
    if (closed || busy || !backend.available()) return;
    boolean selective = selective();
    if (backend.load("backup") != null && snapshot("backup") == null || backend.load("applied") != null && snapshot("applied") == null) {
      fail(new TdApi.Error(500, "Invalid privacy snapshot"), revision);
      return;
    }
    if (!selective && snapshot("backup") == null) { finish(); return; }
    busy = true;
    long requestRevision = revision;
    backend.getRules((current, error) -> {
      if (error != null || current == null) { fail(error, requestRevision); return; }
      if (requestRevision != revision) { again(); return; }
      TdApi.UserPrivacySettingRules backup = snapshot("backup");
      TdApi.UserPrivacySettingRules applied = snapshot("applied");
      if (!selective) {
        if (foreignRules != null) {
          TdApi.UserPrivacySettingRules target = foreignRules;
          foreignRules = null;
          apply(target, false, requestRevision);
        } else if (applied == null || !GhostPrivacyRules.equals(current, applied)) {
          // Preserve the most recent server rules if another client changed them.
          clear();
          busy = false;
          finish();
        } else apply(backup, false, requestRevision);
      } else {
        if (foreignRules != null) {
          save("backup", foreignRules);
          foreignRules = null;
        } else if (backup == null || applied != null && !GhostPrivacyRules.equals(current, applied) && !GhostPrivacyRules.equals(current, backup)) save("backup", current);
        TdApi.UserPrivacySettingRules target = GhostPrivacyRules.selective(backend.users());
        save("applied", target);
        // Always obtain a server acknowledgement before allowing online, even
        // when GetUserPrivacySettingRules returned an identical cached value.
        apply(target, true, requestRevision);
      }
    });
  }

  private void apply (TdApi.UserPrivacySettingRules target, boolean selective, long requestRevision) {
    if (target == null) { fail(new TdApi.Error(500, "Missing privacy snapshot"), requestRevision); return; }
    expected = target;
    backend.setRules(target, (ok, error) -> {
      if (error != null || ok == null) { fail(error, requestRevision); return; }
      if (requestRevision != revision) { again(); return; }
      backend.getRules((actual, checkError) -> {
        if (checkError != null || actual == null) { fail(checkError, requestRevision); return; }
        if (requestRevision != revision) { again(); return; }
        if (!GhostPrivacyRules.equals(actual, target)) { fail(new TdApi.Error(409, "Privacy changed during verification"), requestRevision); return; }
        if (!selective) clear();
        busy = false;
        expected = null;
        finish();
      });
    });
  }

  private void again () {
    busy = false;
    expected = null;
    reconcile();
  }

  private void fail (@Nullable TdApi.Error error, long requestRevision) {
    if (closed) return;
    busy = false;
    expected = null;
    backend.setOnline(false);
    if (requestRevision != revision) { reconcile(); return; }
    backend.stateChanged(error != null ? error.message : "Empty privacy response", false);
  }

  private void finish () {
    if (closed) return;
    boolean selective = selective();
    TdApi.UserPrivacySettingRules applied = snapshot("applied");
    if (selective && (applied == null || !GhostPrivacyRules.equals(applied, GhostPrivacyRules.selective(backend.users()))) ||
        !selective && backend.load("backup") != null) {
      refresh();
      return;
    }
    backend.setOnline(backend.available() && backend.onlineRequested() && (!backend.hideOnline() || backend.users().length > 0));
    backend.stateChanged(null, false);
  }

  public void close () {
    closed = true;
    revision++;
    backend.setOnline(false);
  }
}
