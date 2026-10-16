package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;
import org.drinkless.tdlib.TdApi;
import java.util.ArrayList;
import java.util.Arrays;

/** Lossless local snapshots, including rules this feature does not generate. */
public final class GhostPrivacyRules {
  private GhostPrivacyRules () { }

  public static TdApi.UserPrivacySettingRules selective (long[] userIds) {
    long[] ids = Arrays.stream(userIds).filter(id -> id > 0).distinct().sorted().toArray();
    return new TdApi.UserPrivacySettingRules(ids.length == 0 ?
      new TdApi.UserPrivacySettingRule[] {new TdApi.UserPrivacySettingRuleRestrictAll()} :
      new TdApi.UserPrivacySettingRule[] {new TdApi.UserPrivacySettingRuleAllowUsers(ids), new TdApi.UserPrivacySettingRuleRestrictAll()});
  }

  public static long[] encode (TdApi.UserPrivacySettingRules rules) {
    ArrayList<Long> data = new ArrayList<>();
    for (TdApi.UserPrivacySettingRule rule : rules.rules) {
      long[] ids = new long[0];
      if (rule instanceof TdApi.UserPrivacySettingRuleAllowUsers) ids = ((TdApi.UserPrivacySettingRuleAllowUsers) rule).userIds;
      else if (rule instanceof TdApi.UserPrivacySettingRuleRestrictUsers) ids = ((TdApi.UserPrivacySettingRuleRestrictUsers) rule).userIds;
      else if (rule instanceof TdApi.UserPrivacySettingRuleAllowChatMembers) ids = ((TdApi.UserPrivacySettingRuleAllowChatMembers) rule).chatIds;
      else if (rule instanceof TdApi.UserPrivacySettingRuleRestrictChatMembers) ids = ((TdApi.UserPrivacySettingRuleRestrictChatMembers) rule).chatIds;
      data.add((long) rule.getConstructor());
      data.add((long) ids.length);
      for (long id : ids) data.add(id);
    }
    long[] result = new long[data.size()];
    for (int i = 0; i < result.length; i++) result[i] = data.get(i);
    return result;
  }

  @Nullable
  public static TdApi.UserPrivacySettingRules decode (@Nullable long[] data) {
    if (data == null) return null;
    ArrayList<TdApi.UserPrivacySettingRule> rules = new ArrayList<>();
    for (int i = 0; i < data.length;) {
      if (data.length - i < 2) return null;
      long constructor = data[i++];
      if (constructor != (int) constructor) return null;
      int type = (int) constructor;
      long size = data[i++];
      if (size < 0 || size > data.length - i) return null;
      long[] ids = Arrays.copyOfRange(data, i, i + (int) size);
      i += (int) size;
      TdApi.UserPrivacySettingRule rule;
      switch (type) {
        case TdApi.UserPrivacySettingRuleAllowAll.CONSTRUCTOR: rule = new TdApi.UserPrivacySettingRuleAllowAll(); break;
        case TdApi.UserPrivacySettingRuleAllowContacts.CONSTRUCTOR: rule = new TdApi.UserPrivacySettingRuleAllowContacts(); break;
        case TdApi.UserPrivacySettingRuleAllowBots.CONSTRUCTOR: rule = new TdApi.UserPrivacySettingRuleAllowBots(); break;
        case TdApi.UserPrivacySettingRuleAllowPremiumUsers.CONSTRUCTOR: rule = new TdApi.UserPrivacySettingRuleAllowPremiumUsers(); break;
        case TdApi.UserPrivacySettingRuleRestrictAll.CONSTRUCTOR: rule = new TdApi.UserPrivacySettingRuleRestrictAll(); break;
        case TdApi.UserPrivacySettingRuleRestrictContacts.CONSTRUCTOR: rule = new TdApi.UserPrivacySettingRuleRestrictContacts(); break;
        case TdApi.UserPrivacySettingRuleRestrictBots.CONSTRUCTOR: rule = new TdApi.UserPrivacySettingRuleRestrictBots(); break;
        case TdApi.UserPrivacySettingRuleAllowUsers.CONSTRUCTOR: rule = new TdApi.UserPrivacySettingRuleAllowUsers(ids); break;
        case TdApi.UserPrivacySettingRuleRestrictUsers.CONSTRUCTOR: rule = new TdApi.UserPrivacySettingRuleRestrictUsers(ids); break;
        case TdApi.UserPrivacySettingRuleAllowChatMembers.CONSTRUCTOR: rule = new TdApi.UserPrivacySettingRuleAllowChatMembers(ids); break;
        case TdApi.UserPrivacySettingRuleRestrictChatMembers.CONSTRUCTOR: rule = new TdApi.UserPrivacySettingRuleRestrictChatMembers(ids); break;
        default: return null;
      }
      if (size != 0 && !(rule instanceof TdApi.UserPrivacySettingRuleAllowUsers || rule instanceof TdApi.UserPrivacySettingRuleRestrictUsers ||
          rule instanceof TdApi.UserPrivacySettingRuleAllowChatMembers || rule instanceof TdApi.UserPrivacySettingRuleRestrictChatMembers)) return null;
      rules.add(rule);
    }
    return new TdApi.UserPrivacySettingRules(rules.toArray(new TdApi.UserPrivacySettingRule[0]));
  }

  public static boolean equals (TdApi.UserPrivacySettingRules a, TdApi.UserPrivacySettingRules b) {
    if (a == b) return true;
    if (a == null || b == null) return false;
    return Arrays.equals(canonical(a), canonical(b));
  }

  private static long[] canonical (TdApi.UserPrivacySettingRules rules) {
    long[] data = encode(rules);
    for (int i = 0; i < data.length;) {
      int count = (int) data[i + 1];
      Arrays.sort(data, i + 2, i + 2 + count);
      i += 2 + count;
    }
    // TDLib omits the implicit trailing deny-all rule in server responses.
    if (rules.rules.length > 0 && rules.rules[rules.rules.length - 1] instanceof TdApi.UserPrivacySettingRuleRestrictAll) {
      return Arrays.copyOf(data, data.length - 2);
    }
    return data;
  }
}
