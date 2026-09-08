package moe.kirao.mgx.utils;

/** Unicode-safe word selection helpers for partial message quotes. */
public final class QuoteSelectionPolicy {
  private QuoteSelectionPolicy () { }

  public static int[] wordRange (String text, int offset) {
    if (text == null || text.isEmpty()) return new int[] {0, 0};
    int start = Math.max(0, Math.min(offset, text.length() - 1));
    if (start > 0 && Character.isLowSurrogate(text.charAt(start)) && Character.isHighSurrogate(text.charAt(start - 1))) start--;
    int codePoint = text.codePointAt(start);
    int end = start + Character.charCount(codePoint);
    boolean word = Character.isLetterOrDigit(codePoint) || codePoint == '_';
    while (word && start > 0) {
      int previous = text.codePointBefore(start);
      if (!Character.isLetterOrDigit(previous) && previous != '_') break;
      start -= Character.charCount(previous);
    }
    while (word && end < text.length()) {
      int next = text.codePointAt(end);
      if (!Character.isLetterOrDigit(next) && next != '_') break;
      end += Character.charCount(next);
    }
    return new int[] {start, end};
  }
}
