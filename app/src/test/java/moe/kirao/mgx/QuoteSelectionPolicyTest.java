package moe.kirao.mgx;

import moe.kirao.mgx.utils.QuoteSelectionPolicy;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class QuoteSelectionPolicyTest {
  @Test public void selectsUnicodeWordWithoutSplittingSurrogatePair() {
    String text = "hello 😀 мир";
    int[] range = QuoteSelectionPolicy.wordRange(text, text.indexOf("мир") + 1);
    assertEquals(text.indexOf("мир"), range[0]);
    assertEquals(text.length(), range[1]);
  }

  @Test public void selectingEmojiReturnsOnlyEmoji() {
    String text = "a 😀 b";
    int emoji = text.indexOf("😀");
    int[] range = QuoteSelectionPolicy.wordRange(text, emoji + 1);
    assertEquals(emoji, range[0]);
    assertEquals(emoji + 2, range[1]);
  }

  @Test public void draggedBoundariesNeverSplitSurrogatePairs() {
    String text = "a😀b";
    assertEquals(1, QuoteSelectionPolicy.boundary(text, 2, false));
    assertEquals(3, QuoteSelectionPolicy.boundary(text, 2, true));
    assertEquals(1, QuoteSelectionPolicy.boundary(text, 1, true));
    assertEquals(3, QuoteSelectionPolicy.boundary(text, 3, false));
  }

  @Test public void clampsDraggedBoundariesAtTextEnds() {
    assertEquals(0, QuoteSelectionPolicy.boundary("😀", -1, false));
    assertEquals(2, QuoteSelectionPolicy.boundary("😀", 3, true));
    assertEquals(0, QuoteSelectionPolicy.boundary("", 1, true));
  }
}
