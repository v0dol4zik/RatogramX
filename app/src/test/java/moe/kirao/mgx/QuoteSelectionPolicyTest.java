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
}
