package moe.kirao.mgx;

import moe.kirao.mgx.utils.LocalReadState;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LocalReadStateTest {
  @Test public void partialProgressSurvivesRestoringTheState () {
    LocalReadState state = new LocalReadState(0, null);
    assertTrue(state.update(10, 8, 100, 30, 3, -1));
    assertEquals(5, state.getUnreadCount(10, 8, 100));
    LocalReadState restored = new LocalReadState(state.readMessageId, state.counter());
    assertEquals(30, restored.readMessageId);
    assertEquals(5, restored.getUnreadCount(10, 8, 100));
    assertFalse(restored.update(10, 8, 100, 30, 0, -1));
    assertTrue(restored.update(10, 8, 100, 50, 2, -1));
    assertEquals(3, restored.getUnreadCount(10, 8, 100));
  }

  @Test public void readingTheLatestMessageClearsTheBadgeWithoutChangingServerData () {
    LocalReadState state = new LocalReadState(0, null);
    state.update(10, 50, 100, 100, 1, -1);
    assertEquals(0, state.getUnreadCount(10, 50, 100));
    assertEquals(2, state.getUnreadCount(10, 52, 120));
    state.update(10, 52, 120, 110, 1, -1);
    assertEquals(1, state.getUnreadCount(10, 52, 120));
  }

  @Test public void serverCatchUpWinsAndPartialExternalReadsUseTheServerCount () {
    LocalReadState state = new LocalReadState(0, null);
    state.update(10, 8, 100, 50, 3, -1);
    assertEquals(4, state.getUnreadCount(30, 4, 100));
    assertEquals(2, state.getUnreadCount(50, 2, 100));
    assertFalse(state.update(50, 2, 100, 40, 0, -1));
    assertEquals(50, state.readMessageId);
  }

  @Test public void exactLoadedTailCanResolveAnUnknownThreadCount () {
    LocalReadState state = new LocalReadState(0, null);
    state.update(10, -1, 100, 30, 2, -1);
    assertEquals(-1, state.getUnreadCount(10, -1, 100));
    state.update(10, -1, 100, 30, 0, 4);
    assertEquals(4, state.getUnreadCount(10, -1, 100));
  }

  @Test public void countsAreClampedAndLargeCountersDoNotOverflow () {
    LocalReadState state = new LocalReadState(0, null);
    state.update(10, 2, 100, 30, 10, -1);
    assertEquals(0, state.getUnreadCount(10, 2, 100));
    state = new LocalReadState(20, new long[] {10, 1, Integer.MAX_VALUE});
    assertEquals(Integer.MAX_VALUE, state.getUnreadCount(10, Integer.MAX_VALUE, 100));
  }
}
