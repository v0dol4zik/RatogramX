package moe.kirao.mgx;

import moe.kirao.mgx.utils.GhostOnlineSession;
import moe.kirao.mgx.utils.GhostPrivacyRules;
import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import static org.junit.Assert.*;

public class GhostOnlineSessionTest {
  private static TdApi.UserPrivacySettingRules contacts () {
    return new TdApi.UserPrivacySettingRules(new TdApi.UserPrivacySettingRule[] {new TdApi.UserPrivacySettingRuleAllowContacts(), new TdApi.UserPrivacySettingRuleRestrictAll()});
  }

  private static final class Fake implements GhostOnlineSession.Backend {
    boolean available = true, hide = true, requested = true, online, pending;
    String error;
    long[] users = {10};
    final Map<String, long[]> storage;
    final ArrayDeque<Runnable> requests = new ArrayDeque<>();
    TdApi.UserPrivacySettingRules server = contacts();
    boolean failNextGet, failNextSet, normalizeRules;
    int sets;
    final GhostOnlineSession session;

    Fake () { this(new HashMap<>()); }
    Fake (Map<String, long[]> storage) { this.storage = storage; session = new GhostOnlineSession(this); }
    @Override public boolean available () { return available; }
    @Override public boolean hideOnline () { return hide; }
    @Override public boolean onlineRequested () { return requested; }
    @Override public long[] users () { return users; }
    @Override public long[] load (String key) { return storage.get(key); }
    @Override public void save (String key, long[] value) { if (value != null) storage.put(key, value); else storage.remove(key); }
    @Override public void setOnline (boolean value) { online = value; }
    @Override public void stateChanged (String value, boolean pending) { error = value; this.pending = pending; }
    @Override public void getRules (BiConsumer<TdApi.UserPrivacySettingRules, TdApi.Error> callback) {
      requests.add(() -> {
        boolean failed = failNextGet; failNextGet = false;
        callback.accept(failed ? null : server, failed ? new TdApi.Error(500, "network") : null);
      });
    }
    @Override public void setRules (TdApi.UserPrivacySettingRules rules, BiConsumer<TdApi.Ok, TdApi.Error> callback) {
      sets++;
      requests.add(() -> {
        boolean failed = failNextSet; failNextSet = false;
        if (!failed) {
          server = normalizeRules && rules.rules.length > 0 && rules.rules[rules.rules.length - 1] instanceof TdApi.UserPrivacySettingRuleRestrictAll ?
            new TdApi.UserPrivacySettingRules(java.util.Arrays.copyOf(rules.rules, rules.rules.length - 1)) : rules;
          session.onPrivacyChanged(server);
        }
        callback.accept(failed ? null : new TdApi.Ok(), failed ? new TdApi.Error(500, "network") : null);
      });
    }
    void next () { requests.remove().run(); }
    void complete () { int remaining = 30; while (!requests.isEmpty() && --remaining > 0) next(); assertTrue("Privacy request loop", remaining > 0); }
    void enable () { session.refresh(); complete(); assertTrue(online); }
  }

  @Test public void onlineRequiresSetAcknowledgementAndVerification () {
    Fake f = new Fake();
    f.session.refresh(); assertFalse(f.online);
    f.next(); assertFalse(f.online); assertEquals(1, f.sets);
    assertArrayEquals(GhostPrivacyRules.encode(contacts()), f.load("backup"));
    f.next(); assertFalse(f.online);
    f.next(); assertTrue(f.online); assertFalse(f.pending);
    assertTrue(GhostPrivacyRules.equals(GhostPrivacyRules.selective(f.users), f.server));
  }

  @Test public void anIdenticalCachedRuleStillRequiresServerAcknowledgement () {
    Fake f = new Fake(); f.server = GhostPrivacyRules.selective(f.users);
    f.session.refresh(); f.next(); assertEquals(1, f.sets); assertFalse(f.online);
    f.failNextSet = true; f.complete(); assertFalse(f.online); assertNotNull(f.error);
  }

  @Test public void failedFetchOrApplyKeepsOfflineAndCanRetry () {
    Fake f = new Fake(); f.failNextGet = true;
    f.session.refresh(); f.complete(); assertFalse(f.online); assertEquals(0, f.sets);
    f.failNextSet = true;
    f.session.refresh(); f.complete(); assertFalse(f.online); assertNotNull(f.load("backup"));
    f.session.refresh(); f.complete(); assertTrue(f.online); assertNull(f.error);
  }

  @Test public void disablingRestoresOriginalRulesBeforeOnline () {
    Fake f = new Fake(); f.enable();
    f.hide = false; f.session.refresh(); f.next(); assertFalse(f.online);
    f.next(); assertFalse(f.online);
    f.next(); assertTrue(f.online); assertTrue(GhostPrivacyRules.equals(contacts(), f.server));
    assertNull(f.load("backup")); assertNull(f.load("applied"));
  }

  @Test public void failedRestoreRetainsBackupAndKeepsOffline () {
    Fake f = new Fake(); f.enable(); f.hide = false; f.failNextSet = true;
    f.session.refresh(); f.complete(); assertFalse(f.online); assertNotNull(f.load("backup"));
    f.session.refresh(); f.complete(); assertTrue(f.online); assertTrue(GhostPrivacyRules.equals(contacts(), f.server));
  }

  @Test public void changesFromAnotherClientAreNotOverwrittenOnDisable () {
    Fake f = new Fake(); f.enable();
    TdApi.UserPrivacySettingRules external = GhostPrivacyRules.selective(new long[] {77});
    f.server = external; f.hide = false;
    f.session.refresh(); f.complete();
    assertTrue(GhostPrivacyRules.equals(external, f.server)); assertEquals(1, f.sets); assertTrue(f.online);
  }

  @Test public void newerExternalRulesBecomeTheRestorationTarget () {
    Fake f = new Fake(); f.enable();
    TdApi.UserPrivacySettingRules external = GhostPrivacyRules.selective(new long[] {77});
    f.server = external; f.session.onPrivacyChanged(external); f.complete();
    assertTrue(GhostPrivacyRules.equals(GhostPrivacyRules.selective(f.users), f.server));
    f.hide = false; f.session.refresh(); f.complete();
    assertTrue(GhostPrivacyRules.equals(external, f.server));
  }

  @Test public void anExternalEditDuringRestoreWinsOverTheOldBackup () {
    Fake f = new Fake(); f.enable(); f.hide = false;
    f.session.refresh(); f.next();
    TdApi.UserPrivacySettingRules external = GhostPrivacyRules.selective(new long[] {88});
    f.server = external; f.session.onPrivacyChanged(external); f.complete();
    assertTrue(GhostPrivacyRules.equals(external, f.server)); assertTrue(f.online);
  }

  @Test public void rapidListChangesAndDisablingCannotReleaseAStaleRequest () {
    Fake f = new Fake(); f.session.refresh(); f.next();
    f.users = new long[] {20}; f.session.refresh(); f.next();
    assertFalse(f.online); f.complete();
    assertTrue(GhostPrivacyRules.equals(GhostPrivacyRules.selective(new long[] {20}), f.server));
    f.session.refresh(); f.next(); f.hide = false; f.session.refresh(); f.next();
    assertFalse(f.online); f.complete(); assertTrue(GhostPrivacyRules.equals(contacts(), f.server));
  }

  @Test public void restartRecoversPersistedBackupAndAccountsRemainIndependent () {
    Fake first = new Fake(); first.enable();
    Fake restarted = new Fake(first.storage); restarted.server = first.server; restarted.hide = false;
    restarted.session.refresh(); restarted.complete(); assertTrue(GhostPrivacyRules.equals(contacts(), restarted.server));
    Fake other = new Fake(); other.users = new long[0]; other.session.refresh(); other.complete();
    assertFalse(other.online); assertEquals(0, other.sets); assertTrue(other.storage.isEmpty());
  }

  @Test public void losingNetworkAndReturningDoesNotTrustAnOldResponse () {
    Fake f = new Fake(); f.session.refresh(); f.next();
    f.available = false; f.session.refresh(); f.complete(); assertFalse(f.online);
    f.available = true; f.session.refresh(); f.complete(); assertTrue(f.online);
  }

  @Test public void emptyExceptionsForceOfflineAndRestorePrivacy () {
    Fake f = new Fake(); f.enable(); f.users = new long[0];
    f.session.refresh(); f.complete(); assertFalse(f.online); assertTrue(GhostPrivacyRules.equals(contacts(), f.server));
  }

  @Test public void corruptSnapshotCannotAllowOnline () {
    Fake f = new Fake(); f.hide = false; f.storage.put("backup", new long[] {42});
    f.session.refresh(); f.complete(); assertFalse(f.online); assertNotNull(f.error);
  }

  @Test public void closingAnAccountIgnoresPendingPrivacyReplies () {
    Fake old = new Fake(); old.session.refresh(); old.next();
    old.session.close(); old.complete();
    assertFalse(old.online);
    assertEquals(1, old.sets);
    assertNotNull(old.load("backup"));
    old.session.refresh(); old.complete(); assertFalse(old.online);
    Fake other = new Fake(); other.users = new long[] {99}; other.enable();
    assertTrue(GhostPrivacyRules.equals(GhostPrivacyRules.selective(new long[] {99}), other.server));
    assertFalse(old.online);
  }

  @Test public void changedConfigurationCannotGoOnlineBeforeItsQueuedRefresh () {
    Fake f = new Fake(); f.session.refresh(); f.next(); f.next();
    f.users = new long[] {20};
    f.next(); assertFalse(f.online);
    f.complete(); assertTrue(f.online);
    assertTrue(GhostPrivacyRules.equals(GhostPrivacyRules.selective(f.users), f.server));
  }

  @Test public void serverNormalizationDoesNotOverwriteOriginalPrivacy () {
    Fake f = new Fake(); f.normalizeRules = true;
    f.enable();
    assertTrue(GhostPrivacyRules.equals(contacts(), GhostPrivacyRules.decode(f.load("backup"))));
    f.hide = false; f.session.refresh(); f.complete();
    assertTrue(f.online); assertTrue(GhostPrivacyRules.equals(contacts(), f.server));
    assertNull(f.load("backup"));
  }
}
