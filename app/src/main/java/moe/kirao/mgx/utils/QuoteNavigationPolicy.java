package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;

/** Resolves TDLib's approximate UTF-16 quote position against the current text. */
public final class QuoteNavigationPolicy {
  private QuoteNavigationPolicy () { }

  public static @Nullable int[] findRange (String text, String quote, int position) {
    if (text == null || quote == null || quote.isEmpty() || quote.length() > text.length()) return null;
    int best = -1;
    long distance = Long.MAX_VALUE;
    for (int start = text.indexOf(quote); start != -1; start = text.indexOf(quote, start + 1)) {
      int end = start + quote.length();
      if (!isBoundary(text, start) || !isBoundary(text, end)) continue;
      long candidateDistance = position >= 0 ? Math.abs((long) start - position) : start;
      if (candidateDistance < distance) {
        best = start;
        distance = candidateDistance;
        if (distance == 0) break;
      }
    }
    return best != -1 ? new int[] {best, best + quote.length()} : null;
  }

  private static boolean isBoundary (String text, int offset) {
    return offset == 0 || offset == text.length() ||
      !Character.isHighSurrogate(text.charAt(offset - 1)) || !Character.isLowSurrogate(text.charAt(offset));
  }
}
