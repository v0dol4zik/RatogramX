package moe.kirao.mgx.utils;

/** Keeps local Premium composition separate from Telegram operations. */
public final class RichMessagePolicy {
  private RichMessagePolicy () { }

  public enum Mode { UNAVAILABLE, LOCAL, TELEGRAM }

  public static Mode creationMode (boolean telegramPremium, boolean localPremium, boolean savedMessages,
                                   boolean canWrite, boolean secretChat) {
    if (!canWrite || secretChat) return Mode.UNAVAILABLE;
    if (telegramPremium) return Mode.TELEGRAM;
    return localPremium && savedMessages ? Mode.LOCAL : Mode.UNAVAILABLE;
  }

  public static boolean canEdit (boolean localMessage, boolean telegramPremium, boolean canWrite, boolean canBeEdited) {
    return localMessage || telegramPremium && canWrite && canBeEdited;
  }

  // Server message IDs are positive. This range is reserved for our private history.
  public static boolean isLocalId (long id) { return id <= Long.MIN_VALUE / 2; }

  public static long localId (long sequence) {
    if (sequence <= 0 || sequence >= Long.MAX_VALUE / 2) throw new IllegalArgumentException("Invalid local sequence");
    return Long.MIN_VALUE + sequence;
  }
}
