package moe.kirao.mgx;

import org.junit.Test;
import org.thunderdog.challegram.util.text.FormattedText;
import org.thunderdog.challegram.util.text.Highlight;
import org.thunderdog.challegram.util.text.TextColorSet;
import org.thunderdog.challegram.util.text.TextEntity;
import org.thunderdog.challegram.util.text.TextEntityCustom;

import static org.junit.Assert.*;

public class HighlightCompositionTest {
  private static final TextColorSet SEARCH = () -> 1;
  private static final TextColorSet QUOTE = () -> 2;

  @Test public void overlappingSearchAndQuoteHaveSeparateColorsWithoutOverlappingEntities () {
    Highlight search = Highlight.range(1, 5);
    search.setCustomColorSet(SEARCH);
    Highlight quote = Highlight.range(3, 8);
    quote.setCustomColorSet(QUOTE);
    FormattedText result = new FormattedText("abcdefghij").highlight(Highlight.combine(search, quote));
    assertEquals(3, result.entities.length);
    assertRange(result.entities[0], 1, 3, SEARCH);
    assertRange(result.entities[1], 3, 5, QUOTE);
    assertRange(result.entities[2], 5, 8, QUOTE);
    assertNull(search.getAdditionalHighlight());
  }

  @Test public void highlightingAcrossLinksRetainsClickTargetsAndTheOriginalEntities () {
    TextEntity link = new TextEntityCustom(null, null, "abcdefghij", 2, 4, TextEntityCustom.FLAG_CLICKABLE, null).setTag("link");
    TextEntity other = new TextEntityCustom(null, null, "abcdefghij", 8, 10, TextEntityCustom.FLAG_CLICKABLE, null).setTag("other");
    FormattedText source = new FormattedText("abcdefghij", new TextEntity[] {link, other});
    Highlight quote = Highlight.range(1, 9);
    quote.setCustomColorSet(QUOTE);
    FormattedText result = source.highlight(quote);
    assertEquals(5, result.entities.length);
    assertRange(result.entities[0], 1, 2, QUOTE);
    assertRange(result.entities[1], 2, 4, QUOTE);
    assertRange(result.entities[2], 4, 8, QUOTE);
    assertRange(result.entities[3], 8, 9, QUOTE);
    assertEquals(9, result.entities[4].getStart());
    assertEquals(10, result.entities[4].getEnd());
    assertTrue(result.entities[1].isClickable());
    assertEquals("link", result.entities[1].getTag());
    assertTrue(result.entities[3].isClickable());
    assertEquals("other", result.entities[3].getTag());
    assertEquals(8, other.getStart());
    assertEquals(10, other.getEnd());
    assertNull(other.getSpecialColorSet(SEARCH));
  }

  @Test public void movingCombinedHighlightsRetainsBothColorsAndRanges () {
    Highlight search = Highlight.range(0, 2);
    search.setCustomColorSet(SEARCH);
    Highlight quote = Highlight.range(4, 6);
    quote.setCustomColorSet(QUOTE);
    Highlight moved = Highlight.combine(search, quote).offset(3);
    assertEquals(3, moved.parts.get(0).start);
    assertEquals(7, moved.getAdditionalHighlight().parts.get(0).start);
    assertSame(SEARCH, moved.getColorSet());
    assertSame(QUOTE, moved.getAdditionalHighlight().getColorSet());
  }

  private static void assertRange (TextEntity entity, int start, int end, TextColorSet color) {
    assertEquals(start, entity.getStart());
    assertEquals(end, entity.getEnd());
    assertSame(color, entity.getSpecialColorSet(SEARCH));
  }
}
