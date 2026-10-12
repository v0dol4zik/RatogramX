package moe.kirao.mgx;

import moe.kirao.mgx.utils.UnreadCounterEstimate;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class UnreadCounterEstimateTest {
  @Test public void aStaleServerCountDoesNotEraseScrollProgress () {
    UnreadCounterEstimate estimate = new UnreadCounterEstimate(10, 8);
    estimate.view(30, 3, -1, 100);
    estimate.updateServer(30, 8, 0);
    assertEquals(5, estimate.unreadCount());
    estimate.view(50, 2, -1, 100);
    estimate.updateServer(50, 8, 0);
    assertEquals(3, estimate.unreadCount());
    estimate.updateServer(50, 3, 0);
    assertEquals(3, estimate.unreadCount());
  }

  @Test public void repairedCountsDoNotSubtractTheSameMessagesTwice () {
    UnreadCounterEstimate estimate = new UnreadCounterEstimate(10, 8);
    estimate.view(30, 3, -1, 100);
    estimate.updateServer(30, 8, 0);
    estimate.updateServer(30, 5, 0);
    assertEquals(5, estimate.unreadCount());
    estimate.view(50, 2, -1, 100);
    estimate.updateServer(50, 3, 0);
    assertEquals(3, estimate.unreadCount());
  }

  @Test public void aFastServerMarkerBeforeTheViewCallbackStillCountsLoadedMessages () {
    UnreadCounterEstimate estimate = new UnreadCounterEstimate(10, 8);
    estimate.updateServer(30, 8, 0, 3);
    assertEquals(5, estimate.unreadCount());
    estimate.view(30, 0, -1, 100);
    assertEquals(5, estimate.unreadCount());
    estimate.updateServer(30, 5, 0, 0);
    assertEquals(5, estimate.unreadCount());
  }

  @Test public void serverCountsIncludeOnlyTheUnacknowledgedLocalProgress () {
    UnreadCounterEstimate estimate = new UnreadCounterEstimate(10, 8);
    estimate.view(50, 5, -1, 100);
    estimate.updateServer(30, 5, 2);
    assertEquals(3, estimate.unreadCount());
    estimate.updateServer(50, 3, 0);
    assertEquals(3, estimate.unreadCount());
  }

  @Test public void newMessagesAndDeletesAdjustTheEstimate () {
    UnreadCounterEstimate estimate = new UnreadCounterEstimate(10, 8);
    estimate.view(30, 3, -1, 100);
    estimate.updateServer(10, 9, 3);
    assertEquals(6, estimate.unreadCount());
    estimate.updateServer(10, 8, 3);
    assertEquals(5, estimate.unreadCount());
  }

  @Test public void olderCallbacksAndRepeatedViewsDoNotResetOrDoubleCount () {
    UnreadCounterEstimate estimate = new UnreadCounterEstimate(10, 8);
    estimate.view(30, 3, -1, 100);
    estimate.view(30, 3, -1, 100);
    estimate.updateServer(30, 5, 0);
    estimate.updateServer(10, 8, 3);
    assertEquals(5, estimate.unreadCount());
    assertEquals(30, estimate.viewedReadId());
  }

  @Test public void newArrivalsDuringAStaleMarkerUpdatePreserveScrollProgress () {
    UnreadCounterEstimate estimate = new UnreadCounterEstimate(10, 8);
    estimate.view(30, 3, -1, 100);
    estimate.updateServer(30, 9, 0);
    assertEquals(6, estimate.unreadCount());
    estimate.updateServer(30, 6, 0);
    assertEquals(6, estimate.unreadCount());

    UnreadCounterEstimate fastMarker = new UnreadCounterEstimate(10, 8);
    fastMarker.updateServer(30, 9, 0, 3);
    assertEquals(6, fastMarker.unreadCount());
  }

  @Test public void aLoadedTailAndTheLatestMessageGiveAnExactCount () {
    UnreadCounterEstimate estimate = new UnreadCounterEstimate(10, 20);
    estimate.view(50, 2, 4, 100);
    assertEquals(4, estimate.unreadCount());
    estimate.view(100, 4, 0, 100);
    assertEquals(0, estimate.unreadCount());
    estimate.updateServer(100, 0, 0);
    estimate.updateServer(100, 1, 0);
    assertEquals(1, estimate.unreadCount());
  }
}
