package moe.kirao.mgx;

import org.junit.Test;

import moe.kirao.mgx.utils.QuoteNavigationPolicy;

import static org.junit.Assert.*;

public class QuoteNavigationPolicyTest {
  @Test public void repeatedQuoteUsesTheSpecifiedOccurrence () {
    assertArrayEquals(new int[] {11, 16}, QuoteNavigationPolicy.findRange("hello then hello", "hello", 11));
    assertArrayEquals(new int[] {0, 5}, QuoteNavigationPolicy.findRange("hello then hello", "hello", 0));
  }

  @Test public void editedPrefixFindsTheNearestIntactQuote () {
    assertArrayEquals(new int[] {7, 12}, QuoteNavigationPolicy.findRange("prefix hello then hello", "hello", 5));
    assertArrayEquals(new int[] {18, 23}, QuoteNavigationPolicy.findRange("prefix hello then hello", "hello", 20));
  }

  @Test public void removedQuoteFallsBackToTheMessage () {
    assertNull(QuoteNavigationPolicy.findRange("changed text", "old text", 0));
    assertNull(QuoteNavigationPolicy.findRange("text", "", 0));
    assertNull(QuoteNavigationPolicy.findRange(null, "text", 0));
  }

  @Test public void emojiPositionsRemainUtf16AndNeverSplitSurrogatePairs () {
    assertArrayEquals(new int[] {3, 7}, QuoteNavigationPolicy.findRange("😀 quote", "quot", 3));
    assertArrayEquals(new int[] {0, 2}, QuoteNavigationPolicy.findRange("😀 quote", "😀", 0));
    assertNull(QuoteNavigationPolicy.findRange("😀 quote", "\uDE00", 1));
  }

  @Test public void invalidPositionsDoNotOverflowOrRejectAnIntactQuote () {
    assertArrayEquals(new int[] {0, 5}, QuoteNavigationPolicy.findRange("hello hello", "hello", -1));
    assertArrayEquals(new int[] {6, 11}, QuoteNavigationPolicy.findRange("hello hello", "hello", Integer.MAX_VALUE));
  }
}
