package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;
import org.drinkless.tdlib.TdApi;

/** Pure pagination guard used by the forum topic list. */
public final class ForumPaginationPolicy {
  private ForumPaginationPolicy () { }

  public static boolean hasNextPage (@Nullable TdApi.ForumTopics result,
                                     int previousDate, long previousMessageId, int previousTopicId) {
    return result != null && result.topics != null && result.topics.length > 0
      && (result.nextOffsetDate != previousDate || result.nextOffsetMessageId != previousMessageId
          || result.nextOffsetForumTopicId != previousTopicId)
      && (result.nextOffsetDate != 0 || result.nextOffsetMessageId != 0L
          || result.nextOffsetForumTopicId != 0);
  }
}
