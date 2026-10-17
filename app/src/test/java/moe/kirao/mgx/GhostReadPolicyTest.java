package moe.kirao.mgx;

import moe.kirao.mgx.utils.GhostReadPolicy;
import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GhostReadPolicyTest {
  @Test public void accountChatAndTopicTypesHaveSeparateMarkers () {
    Set<String> keys = new HashSet<>();
    keys.add(GhostReadPolicy.key(0, -100, null));
    keys.add(GhostReadPolicy.key(1, -100, null));
    keys.add(GhostReadPolicy.key(0, -101, null));
    keys.add(GhostReadPolicy.key(0, -100, new TdApi.MessageTopicForum(5)));
    keys.add(GhostReadPolicy.key(0, -100, new TdApi.MessageTopicForum(6)));
    keys.add(GhostReadPolicy.key(0, -100, new TdApi.MessageTopicThread(5)));
    keys.add(GhostReadPolicy.key(0, -100, new TdApi.MessageTopicDirectMessages(5)));
    keys.add(GhostReadPolicy.key(0, -100, new TdApi.MessageTopicSavedMessages(5)));
    assertEquals(8, keys.size());
  }

  @Test public void previewsSearchesAndInteractionRefreshesAreNotLocalReads () {
    assertTrue(GhostReadPolicy.isHistorySource(new TdApi.MessageSourceChatHistory()));
    assertTrue(GhostReadPolicy.isHistorySource(new TdApi.MessageSourceForumTopicHistory()));
    assertTrue(GhostReadPolicy.isHistorySource(new TdApi.MessageSourceMessageThreadHistory()));
    assertTrue(GhostReadPolicy.isHistorySource(new TdApi.MessageSourceDirectMessagesChatTopicHistory()));
    assertFalse(GhostReadPolicy.isHistorySource(new TdApi.MessageSourceHistoryPreview()));
    assertFalse(GhostReadPolicy.isHistorySource(new TdApi.MessageSourceSearch()));
    assertFalse(GhostReadPolicy.isHistorySource(new TdApi.MessageSourceChatList()));
    assertFalse(GhostReadPolicy.isHistorySource(new TdApi.MessageSourceOther()));
  }

  @Test public void pendingAndScheduledMessagesCannotAdvanceInboxMarkers () {
    TdApi.Message message = new TdApi.Message();
    message.id = 100;
    assertTrue(GhostReadPolicy.isReadCandidate(message));
    message.sendingState = new TdApi.MessageSendingStatePending();
    assertFalse(GhostReadPolicy.isReadCandidate(message));
    message.sendingState = null;
    message.schedulingState = new TdApi.MessageSchedulingStateSendWhenOnline();
    assertFalse(GhostReadPolicy.isReadCandidate(message));
  }

  @Test public void ghostViewsNeverCarryAReadBearingSourceOrForceRead () {
    for (TdApi.MessageSource source : new TdApi.MessageSource[] {
      null, new TdApi.MessageSourceChatHistory(), new TdApi.MessageSourceForumTopicHistory(),
      new TdApi.MessageSourceMessageThreadHistory(), new TdApi.MessageSourceDirectMessagesChatTopicHistory()
    }) {
      TdApi.ViewMessages request = GhostReadPolicy.viewRequest(123, new long[] {100}, source, true, true);
      assertTrue(request.source instanceof TdApi.MessageSourceOther);
      assertFalse(request.forceRead);
      request = GhostReadPolicy.viewRequest(123, new long[] {100}, source, true, false);
      assertTrue(request.source == source);
      assertTrue(request.forceRead);
    }
  }

  @Test public void pinAndTitleNoticesAreReadableButVoiceMentionsRequirePlayback () {
    TdApi.Message message = new TdApi.Message();
    message.content = new TdApi.MessagePinMessage(12);
    assertTrue(GhostReadPolicy.canReadMentionOnView(message));
    message.content = new TdApi.MessageChatChangeTitle("Title");
    assertTrue(GhostReadPolicy.canReadMentionOnView(message));
    message.content = new TdApi.MessageVoiceNote();
    assertFalse(GhostReadPolicy.canReadMentionOnView(message));
    message.content = new TdApi.MessageVideoNote();
    assertFalse(GhostReadPolicy.canReadMentionOnView(message));
  }
}
