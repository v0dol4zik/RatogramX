package moe.kirao.mgx.utils;

/** Keeps scroll progress visible while TDLib repairs an incomplete inbox count. */
public final class UnreadCounterEstimate {
  private long serverReadId, viewedReadId;
  private int serverCount, unreadCount;
  private boolean waitingForRepair;

  public UnreadCounterEstimate (long serverReadId, int serverCount) {
    this.serverReadId = this.viewedReadId = serverReadId;
    this.serverCount = this.unreadCount = Math.max(0, serverCount);
  }

  public long viewedReadId () {
    return viewedReadId;
  }

  public int unreadCount () {
    return unreadCount;
  }

  public void updateServer (long readId, int count, int pendingReadCount) {
    updateServer(readId, count, pendingReadCount, 0);
  }

  public void updateServer (long readId, int count, int pendingReadCount, int newlyConfirmedCount) {
    if (readId < serverReadId) return;
    count = Math.max(0, count);
    pendingReadCount = Math.max(0, pendingReadCount);
    if (readId > serverReadId) {
      if (count >= serverCount) {
        // A read marker can arrive with the old count when history has gaps.
        unreadCount = (int) Math.min(count, (long) unreadCount + count - serverCount);
        if (readId > viewedReadId) unreadCount = Math.max(0, unreadCount - Math.max(0, newlyConfirmedCount));
        waitingForRepair = true;
      } else {
        unreadCount = Math.max(0, count - pendingReadCount);
        waitingForRepair = false;
      }
    } else if (waitingForRepair && count < serverCount) {
      unreadCount = Math.max(0, count - pendingReadCount);
      waitingForRepair = false;
    } else {
      unreadCount = (int) Math.max(0L, Math.min(count, (long) unreadCount + count - serverCount));
    }
    unreadCount = Math.min(count, unreadCount);
    viewedReadId = Math.max(viewedReadId, readId);
    serverReadId = readId;
    serverCount = count;
  }

  public void view (long readId, int newlyReadCount, int exactRemainingCount, long lastMessageId) {
    if (readId > viewedReadId) {
      unreadCount = Math.max(0, unreadCount - Math.max(0, newlyReadCount));
      viewedReadId = readId;
    }
    if (lastMessageId != 0 && viewedReadId >= lastMessageId) {
      unreadCount = 0;
    } else if (exactRemainingCount >= 0) {
      unreadCount = Math.min(serverCount, exactRemainingCount);
    }
  }
}
