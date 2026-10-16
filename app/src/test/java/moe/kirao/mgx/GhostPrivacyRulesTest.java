package moe.kirao.mgx;

import moe.kirao.mgx.utils.GhostPrivacyRules;
import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import static org.junit.Assert.*;

public class GhostPrivacyRulesTest {
  @Test public void snapshotsPreserveAllRulesAndTheirOrder () {
    TdApi.UserPrivacySettingRules original = new TdApi.UserPrivacySettingRules(new TdApi.UserPrivacySettingRule[] {
      new TdApi.UserPrivacySettingRuleRestrictUsers(new long[] {7, 8}), new TdApi.UserPrivacySettingRuleAllowUsers(new long[] {9}),
      new TdApi.UserPrivacySettingRuleAllowChatMembers(new long[] {-100}), new TdApi.UserPrivacySettingRuleRestrictChatMembers(new long[] {-200}),
      new TdApi.UserPrivacySettingRuleAllowBots(), new TdApi.UserPrivacySettingRuleAllowPremiumUsers(),
      new TdApi.UserPrivacySettingRuleRestrictBots(), new TdApi.UserPrivacySettingRuleRestrictContacts(),
      new TdApi.UserPrivacySettingRuleAllowContacts(), new TdApi.UserPrivacySettingRuleRestrictAll(), new TdApi.UserPrivacySettingRuleAllowAll()
    });
    assertTrue(GhostPrivacyRules.equals(original, GhostPrivacyRules.decode(GhostPrivacyRules.encode(original))));
  }

  @Test public void selectiveRulesAllowOnlyValidDistinctUsers () {
    TdApi.UserPrivacySettingRules rules = GhostPrivacyRules.selective(new long[] {9, 7, 9, -1, 0});
    assertEquals(2, rules.rules.length);
    assertArrayEquals(new long[] {7, 9}, ((TdApi.UserPrivacySettingRuleAllowUsers) rules.rules[0]).userIds);
    assertTrue(rules.rules[1] instanceof TdApi.UserPrivacySettingRuleRestrictAll);
    assertTrue(GhostPrivacyRules.selective(new long[0]).rules[0] instanceof TdApi.UserPrivacySettingRuleRestrictAll);
  }

  @Test public void invalidSnapshotsAreRejected () {
    assertNull(GhostPrivacyRules.decode(null));
    assertNull(GhostPrivacyRules.decode(new long[] {42}));
    assertNull(GhostPrivacyRules.decode(new long[] {42, 0}));
    assertNull(GhostPrivacyRules.decode(new long[] {TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR, -1}));
    assertNull(GhostPrivacyRules.decode(new long[] {TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR, 10000000}));
    assertNull(GhostPrivacyRules.decode(new long[] {TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR, 1, 42}));
    assertNull(GhostPrivacyRules.decode(new long[] {(long) TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR + (1L << 32), 0}));
  }

  @Test public void comparisonIgnoresUserOrderingButKeepsRulePriority () {
    TdApi.UserPrivacySettingRules a = GhostPrivacyRules.selective(new long[] {9, 7});
    TdApi.UserPrivacySettingRules b = new TdApi.UserPrivacySettingRules(new TdApi.UserPrivacySettingRule[] {
      new TdApi.UserPrivacySettingRuleAllowUsers(new long[] {9, 7}), new TdApi.UserPrivacySettingRuleRestrictAll()
    });
    assertTrue(GhostPrivacyRules.equals(a, b));
    assertArrayEquals(new long[] {9, 7}, ((TdApi.UserPrivacySettingRuleAllowUsers) b.rules[0]).userIds);
    assertFalse(GhostPrivacyRules.equals(a, new TdApi.UserPrivacySettingRules(new TdApi.UserPrivacySettingRule[] {b.rules[1], b.rules[0]})));
  }

  @Test public void comparisonAcceptsTdlibImplicitDenyAll () {
    TdApi.UserPrivacySettingRules explicit = GhostPrivacyRules.selective(new long[] {7});
    TdApi.UserPrivacySettingRules normalized = new TdApi.UserPrivacySettingRules(new TdApi.UserPrivacySettingRule[] {explicit.rules[0]});
    assertTrue(GhostPrivacyRules.equals(explicit, normalized));
    assertTrue(GhostPrivacyRules.equals(GhostPrivacyRules.selective(new long[0]), new TdApi.UserPrivacySettingRules(new TdApi.UserPrivacySettingRule[0])));
  }
}
