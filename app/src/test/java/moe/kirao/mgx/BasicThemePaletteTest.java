package moe.kirao.mgx;

import org.junit.Test;
import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ThemeCustom;
import org.thunderdog.challegram.theme.ThemeDelegate;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import moe.kirao.mgx.utils.BasicThemePalette;
import moe.kirao.mgx.utils.ThemeColorMath;

import static org.junit.Assert.*;

public class BasicThemePaletteTest {
  private static final ThemeDelegate DARK = new ThemeDelegate() {
    @Override public int getId () { return 0; }
    @Override public String getDefaultWallpaper () { return null; }
    @Override public float getProperty (int id) { return 1f; }
    @Override public int getColor (int id) {
      if (id == ColorId.textLinkPressHighlight) return 0x40223344;
      if (id == ColorId.headerText || id == ColorId.headerIcon || id == ColorId.bubbleOut_text ||
          id == ColorId.bubbleIn_text || id == ColorId.badgeText) return 0xffeeeeee;
      return 0xff101010;
    }
  };

  private static Set<Integer> accents () {
    return new HashSet<>(Arrays.asList(ColorId.textLink, ColorId.textLinkPressHighlight, ColorId.headerBackground,
      ColorId.bubbleOut_background, ColorId.badge));
  }

  @Test public void blendsArgbAndCompositesTransparency () {
    assertEquals(0xff800080, ThemeColorMath.mix(0xffff0000, 0xff0000ff, .5f));
    assertEquals(0xff123456, ThemeColorMath.composite(0xff123456, 0x00ffffff));
    assertEquals(0xffabcdef, ThemeColorMath.composite(0xff123456, 0xffabcdef));
  }

  @Test public void readableTextWorksForLightDarkAndMidtoneSurfaces () {
    assertEquals(21.0, ThemeColorMath.contrast(0xffffffff, 0xff000000), .0001);
    for (int background : new int[] {0xffffffff, 0xff101010, 0xff888888, 0xffa02020}) {
      int text = ThemeColorMath.readableText(background, background);
      assertTrue(ThemeColorMath.contrast(background, text) >= 4.5);
    }
    assertEquals(0xffeeeeee, ThemeColorMath.readableText(0xff101010, 0xffeeeeee));
  }

  @Test public void accentPreservesRoleAlphaAndUsesDifferentTones () {
    Map<Integer, Integer> values = BasicThemePalette.colorsFor(DARK, DARK, ColorId.textLink, 0xffb62828, accents());
    assertEquals(Integer.valueOf(0xffb62828), values.get(ColorId.textLink));
    assertEquals(0x40, values.get(ColorId.textLinkPressHighlight) >>> 24);
    assertNotEquals(values.get(ColorId.headerBackground), values.get(ColorId.textLink));
    assertNotEquals(values.get(ColorId.bubbleOut_background), values.get(ColorId.textLink));
  }

  @Test public void lightHeaderAndBubbleGetReadableForegrounds () {
    Map<Integer, Integer> header = BasicThemePalette.colorsFor(DARK, DARK, ColorId.headerBackground, 0xffffffff, accents());
    assertTrue(ThemeColorMath.contrast(header.get(ColorId.headerBackground), header.get(ColorId.headerText)) >= 4.5);
    assertTrue(ThemeColorMath.contrast(header.get(ColorId.headerBackground), header.get(ColorId.headerIcon)) >= 4.5);
    Map<Integer, Integer> bubble = BasicThemePalette.colorsFor(DARK, DARK, ColorId.bubbleOut_background, 0xffffffff, accents());
    assertTrue(ThemeColorMath.contrast(bubble.get(ColorId.bubbleOut_background), bubble.get(ColorId.bubbleOut_text)) >= 4.5);
  }

  @Test public void resettingAccentRestoresEveryAffectedColorToInheritance () {
    Map<Integer, Integer> changed = BasicThemePalette.colorsFor(DARK, DARK, ColorId.textLink, 0xffb62828, accents());
    Map<Integer, Integer> reset = BasicThemePalette.colorsFor(DARK, DARK, ColorId.textLink, null, accents());
    assertEquals(changed.keySet(), reset.keySet());
    for (Integer value : reset.values()) assertNull(value);
  }

  @Test public void chatBackgroundUpdatesBothChatStyles () {
    Map<Integer, Integer> values = BasicThemePalette.colorsFor(DARK, DARK, ColorId.chatBackground, 0xff182024, accents());
    assertEquals(values.get(ColorId.chatBackground), values.get(ColorId.bubble_chatBackground));
  }

  @Test public void changingSurfacesKeepsSettingsReadableAndResetRestoresInheritance () {
    for (int anchor : new int[] {ColorId.filling, ColorId.background}) {
      Map<Integer, Integer> values = BasicThemePalette.colorsFor(DARK, DARK, anchor, 0xff101010, accents());
      int[] foregrounds = anchor == ColorId.filling ?
        new int[] {ColorId.text, ColorId.textLight, ColorId.icon, ColorId.iconLight} :
        new int[] {ColorId.background_text, ColorId.background_textLight, ColorId.background_icon};
      for (int id : foregrounds) {
        assertTrue(ThemeColorMath.contrast(values.get(anchor), values.get(id)) >= 4.5);
      }
      Map<Integer, Integer> reset = BasicThemePalette.colorsFor(DARK, DARK, anchor, null, accents());
      assertEquals(values.keySet(), reset.keySet());
      for (Integer value : reset.values()) assertNull(value);
    }
  }

  @Test public void batchChangesInvalidateAllMembersAndSingleChangesClearBatch () {
    ThemeCustom theme = new ThemeCustom(-1);
    Map<Integer, Integer> values = BasicThemePalette.colorsFor(DARK, DARK, ColorId.headerBackground, 0xffffffff, accents());
    theme.setColors(values);
    assertTrue(theme.hasRecentlyChanged(ColorId.headerBackground));
    assertTrue(theme.hasRecentlyChanged(ColorId.headerText));
    assertEquals(0xffffffff, theme.getColor(ColorId.headerBackground));
    theme.setColor(ColorId.textLink, 0xffb62828);
    assertFalse(theme.hasRecentlyChanged(ColorId.headerText));
    assertTrue(theme.hasRecentlyChanged(ColorId.textLink));
  }
}
