package moe.kirao.mgx;

import moe.kirao.mgx.utils.MessageHistoryPolicy;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MessageHistoryPolicyTest {
  @Test public void pruneRunsAtBatchThreshold() {
    assertTrue(MessageHistoryPolicy.shouldPrune(10_000L, 9_999L,
      MessageHistoryPolicy.PRUNE_WRITE_THRESHOLD));
    assertFalse(MessageHistoryPolicy.shouldPrune(10_000L, 9_999L,
      MessageHistoryPolicy.PRUNE_WRITE_THRESHOLD - 1));
  }

  @Test public void pruneRunsAfterInterval() {
    assertTrue(MessageHistoryPolicy.shouldPrune(
      MessageHistoryPolicy.PRUNE_INTERVAL_MS + 1, 1, 0));
  }

  @Test public void longTextIsBoundedWithoutDanglingSurrogate() {
    StringBuilder text = new StringBuilder(MessageHistoryPolicy.MAX_TEXT_LENGTH + 2);
    for (int i = 0; i < MessageHistoryPolicy.MAX_TEXT_LENGTH; i++) text.append('a');
    text.append("😀");
    String limited = MessageHistoryPolicy.limitText(text.toString());
    assertEquals(MessageHistoryPolicy.MAX_TEXT_LENGTH, limited.length());
    assertFalse(Character.isHighSurrogate(limited.charAt(limited.length() - 1)));
  }
}
