package moe.kirao.mgx.utils;

/** Pure sizing and scheduling rules used by {@link MessageHistoryStore}. */
public final class MessageHistoryPolicy {
  public static final long PRUNE_INTERVAL_MS = 60L * 1000L;
  public static final int PRUNE_WRITE_THRESHOLD = 64;
  public static final int MAX_TEXT_LENGTH = 32 * 1024;

  private MessageHistoryPolicy () { }

  /** Returns whether a write batch should trigger a maintenance pass. */
  public static boolean shouldPrune (long now, long lastPrune, int writesSincePrune) {
    return writesSincePrune >= PRUNE_WRITE_THRESHOLD ||
      (lastPrune == 0L || now - lastPrune >= PRUNE_INTERVAL_MS);
  }

  /** Bounds retained text without splitting a UTF-16 surrogate pair. */
  public static String limitText (String text) {
    if (text == null || text.length() <= MAX_TEXT_LENGTH) return text;
    int end = MAX_TEXT_LENGTH;
    if (end > 0 && Character.isHighSurrogate(text.charAt(end - 1))) end--;
    return text.substring(0, Math.max(0, end));
  }
}
