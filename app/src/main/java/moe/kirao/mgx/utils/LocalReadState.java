package moe.kirao.mgx.utils;

/** A local inbox estimate; server message objects are never modified. */
public final class LocalReadState {
  public long readMessageId;
  private long serverReadMessageId;
  private int serverUnreadCount = -1;
  private int unreadCount = -1;

  public LocalReadState (long readMessageId, long[] counter) {
    this.readMessageId = readMessageId;
    if (counter != null && counter.length == 3) {
      serverReadMessageId = counter[0];
      serverUnreadCount = (int) counter[1];
      unreadCount = (int) counter[2];
    }
  }

  public int getUnreadCount (long serverReadId, int serverCount, long lastMessageId) {
    if (readMessageId == 0 || readMessageId <= serverReadId) return serverCount;
    if (lastMessageId != 0 && readMessageId >= lastMessageId) return 0;
    if (serverReadId != serverReadMessageId || unreadCount < 0) return serverCount;
    if (serverCount < 0) return serverUnreadCount < 0 ? unreadCount : serverCount;
    if (serverUnreadCount < 0) return Math.min(serverCount, unreadCount);
    long remaining = (long) unreadCount + serverCount - serverUnreadCount;
    return (int) Math.max(0L, Math.min(serverCount, remaining));
  }

  public boolean update (long serverReadId, int serverCount, long lastMessageId,
                         long viewedMessageId, int newlyReadCount, int exactRemainingCount) {
    long readId = Math.max(readMessageId, viewedMessageId);
    if (readId <= serverReadId || readId == 0) return false;
    int remaining = getUnreadCount(serverReadId, serverCount, lastMessageId);
    if (lastMessageId != 0 && readId >= lastMessageId) {
      remaining = 0;
    } else if (exactRemainingCount >= 0) {
      remaining = serverCount >= 0 ? Math.min(serverCount, exactRemainingCount) : exactRemainingCount;
    } else if (remaining >= 0) {
      remaining = Math.max(0, remaining - Math.max(0, newlyReadCount));
    }
    boolean changed = readMessageId != readId || serverReadMessageId != serverReadId ||
      serverUnreadCount != serverCount || unreadCount != remaining;
    readMessageId = readId;
    serverReadMessageId = serverReadId;
    serverUnreadCount = serverCount;
    unreadCount = remaining;
    return changed;
  }

  public long[] counter () {
    return new long[] {serverReadMessageId, serverUnreadCount, unreadCount};
  }

  public int locallyReadCount () {
    return readMessageId > serverReadMessageId && serverUnreadCount >= 0 && unreadCount >= 0 ?
      Math.max(0, serverUnreadCount - unreadCount) : 0;
  }

  public int locallyReadCountAfter (long parentReadId) {
    // A partial overlap cannot be counted twice in the parent and topic inboxes.
    return parentReadId <= serverReadMessageId ? locallyReadCount() : 0;
  }

  public boolean invalidateEstimate (long serverReadId) {
    if (serverReadId <= serverReadMessageId || serverReadId >= readMessageId) return false;
    serverReadMessageId = serverReadId;
    serverUnreadCount = unreadCount = -1;
    return true;
  }
}
