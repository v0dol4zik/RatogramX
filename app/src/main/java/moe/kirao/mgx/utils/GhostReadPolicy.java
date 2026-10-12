package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;

import org.drinkless.tdlib.TdApi;

public final class GhostReadPolicy {
  private GhostReadPolicy () { }

  public static boolean isHistorySource (TdApi.MessageSource source) {
    return source instanceof TdApi.MessageSourceChatHistory ||
      source instanceof TdApi.MessageSourceMessageThreadHistory ||
      source instanceof TdApi.MessageSourceForumTopicHistory ||
      source instanceof TdApi.MessageSourceDirectMessagesChatTopicHistory;
  }

  public static String key (int accountId, long chatId, @Nullable TdApi.MessageTopic topicId) {
    String key = "ghost_read_" + accountId + "_" + chatId;
    if (topicId instanceof TdApi.MessageTopicForum) {
      key += "_forum_" + ((TdApi.MessageTopicForum) topicId).forumTopicId;
    } else if (topicId instanceof TdApi.MessageTopicThread) {
      key += "_thread_" + ((TdApi.MessageTopicThread) topicId).messageThreadId;
    } else if (topicId instanceof TdApi.MessageTopicDirectMessages) {
      key += "_direct_" + ((TdApi.MessageTopicDirectMessages) topicId).directMessagesChatTopicId;
    } else if (topicId instanceof TdApi.MessageTopicSavedMessages) {
      key += "_saved_" + ((TdApi.MessageTopicSavedMessages) topicId).savedMessagesTopicId;
    }
    return key;
  }

  public static boolean isReadCandidate (TdApi.Message message) {
    return message.id > 0 && message.sendingState == null && message.schedulingState == null;
  }
}
