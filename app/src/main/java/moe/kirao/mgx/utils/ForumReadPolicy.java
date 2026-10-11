package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

/** Topic read markers must never be inferred from the parent chat. */
public final class ForumReadPolicy {
  private ForumReadPolicy () { }

  public static boolean hasUnread (@Nullable TdApi.ForumTopic topic) {
    return topic != null && topic.unreadCount > 0 &&
      (topic.lastMessage == null || topic.lastReadInboxMessageId < topic.lastMessage.id);
  }

  public static int unreadAfterRead (int unreadCount, long lastMessageId, long readMessageId, int newlyReadCount) {
    if (lastMessageId != 0 && readMessageId >= lastMessageId) return 0;
    return Math.max(0, unreadCount - newlyReadCount);
  }
}
