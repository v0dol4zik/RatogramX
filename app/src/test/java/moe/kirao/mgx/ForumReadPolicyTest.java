package moe.kirao.mgx;

import moe.kirao.mgx.utils.ForumReadPolicy;
import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ForumReadPolicyTest {
  @Test public void aNewTopicWithoutAReadMarkerStillHasUnreadMessages () {
    TdApi.ForumTopic topic = topic(3, 0, 100);
    assertTrue(ForumReadPolicy.hasUnread(topic));
    assertFalse(ForumReadPolicy.hasUnread(null));
  }

  @Test public void readingTheLastMessageClearsAStaleServerCounter () {
    TdApi.ForumTopic topic = topic(7, 100, 100);
    assertFalse(ForumReadPolicy.hasUnread(topic));
    assertEquals(0, ForumReadPolicy.unreadAfterRead(7, 100, 100, 0));
    assertEquals(0, ForumReadPolicy.unreadAfterRead(7, 100, 101, 0));
  }

  @Test public void partialReadingOnlySubtractsNewIncomingMessages () {
    assertEquals(5, ForumReadPolicy.unreadAfterRead(7, 100, 50, 2));
    assertEquals(7, ForumReadPolicy.unreadAfterRead(7, 100, 50, 0));
    assertEquals(0, ForumReadPolicy.unreadAfterRead(1, 100, 50, 2));
    assertEquals(5, ForumReadPolicy.unreadAfterRead(7, 0, 50, 2));
  }

  private static TdApi.ForumTopic topic (int unreadCount, long readId, long lastId) {
    TdApi.ForumTopic topic = new TdApi.ForumTopic();
    topic.unreadCount = unreadCount;
    topic.lastReadInboxMessageId = readId;
    topic.lastMessage = new TdApi.Message();
    topic.lastMessage.id = lastId;
    return topic;
  }
}
