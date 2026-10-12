package moe.kirao.mgx;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;

import moe.kirao.mgx.utils.VideoPositionPolicy;
import tgx.td.ChatId;

import static org.junit.Assert.*;

public class VideoPositionPolicyTest {
  @Test public void bookmarksBelongToAnAccountAndMessage () {
    assertNotEquals(VideoPositionPolicy.key(1, 20, 30, "file"), VideoPositionPolicy.key(2, 20, 30, "file"));
    assertNotEquals(VideoPositionPolicy.key(1, 20, 30, "file"), VideoPositionPolicy.key(1, 21, 30, "file"));
    assertNotEquals(VideoPositionPolicy.key(1, 20, 30, "file"), VideoPositionPolicy.key(1, 20, 31, "file"));
    assertEquals(VideoPositionPolicy.key(1, 20, 30, "file"), VideoPositionPolicy.key(1, 20, 30, ""));
  }

  @Test public void remoteFallbackDoesNotUseAnUnstableLocalFileId () {
    assertEquals(VideoPositionPolicy.key(1, 0, 0, "remote"), VideoPositionPolicy.key(1, 20, 0, "remote"));
    assertNotEquals(VideoPositionPolicy.key(1, 0, 0, "remote"), VideoPositionPolicy.key(2, 0, 0, "remote"));
    assertNull(VideoPositionPolicy.key(1, 0, 0, ""));
    assertNull(VideoPositionPolicy.key(1, 0, 0, null));
  }

  @Test public void privateAndEphemeralVideosHaveNoBookmark () {
    assertNull(VideoPositionPolicy.key(1, ChatId.fromSecretChatId(42), 30, "remote"));
    assertFalse(VideoPositionPolicy.canRemember(false, false, null));
    assertFalse(VideoPositionPolicy.canRemember(true, true, null));
    TdApi.Message message = new TdApi.Message();
    assertTrue(VideoPositionPolicy.canRemember(true, false, message));
    message.selfDestructType = new TdApi.MessageSelfDestructTypeImmediately();
    assertFalse(VideoPositionPolicy.canRemember(true, false, message));
    message.selfDestructType = new TdApi.MessageSelfDestructTypeTimer(10);
    assertFalse(VideoPositionPolicy.canRemember(true, false, message));
  }

  @Test public void unfinishedAndScheduledMessagesAreExcluded () {
    TdApi.Message message = new TdApi.Message();
    message.sendingState = new TdApi.MessageSendingStatePending();
    assertFalse(VideoPositionPolicy.canRemember(true, false, message));
    message.sendingState = null;
    message.schedulingState = new TdApi.MessageSchedulingStateSendWhenOnline();
    assertFalse(VideoPositionPolicy.canRemember(true, false, message));
  }

  @Test public void onlyMeaningfulProgressInLongVideosIsRetained () {
    assertEquals(0, VideoPositionPolicy.retainedPosition(30_000, 60_000));
    assertEquals(0, VideoPositionPolicy.retainedPosition(4_999, 120_000));
    assertEquals(5_000, VideoPositionPolicy.retainedPosition(5_000, 120_000));
    assertEquals(115_000, VideoPositionPolicy.retainedPosition(115_000, 120_000));
    assertEquals(0, VideoPositionPolicy.retainedPosition(115_001, 120_000));
    assertEquals(0, VideoPositionPolicy.retainedPosition(30_000, -1));
    assertEquals(0, VideoPositionPolicy.retainedPosition(-1, 120_000));
  }
}
