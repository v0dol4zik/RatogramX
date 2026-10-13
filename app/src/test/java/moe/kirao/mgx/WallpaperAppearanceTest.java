package moe.kirao.mgx;

import org.junit.Test;
import moe.kirao.mgx.utils.WallpaperAppearance;
import static org.junit.Assert.*;

public class WallpaperAppearanceTest {
  @Test public void clampsUntrustedSavedAdjustments () {
    WallpaperAppearance value = new WallpaperAppearance(200, Integer.MAX_VALUE, 0xff123456, -10, 20);
    assertEquals(80, value.dim);
    assertEquals(15, value.blurRadius);
    assertEquals(0x123456, value.tintColor);
    assertEquals(0, value.tintIntensity);
    assertEquals(WallpaperAppearance.BLEND_SCREEN, value.tintBlend);
  }

  @Test public void blurStepsOnlyUseRadiiSupportedByNativeDecoder () {
    int[] expected = {0, 1, 3, 7, 15};
    for (int i = 0; i < expected.length; i++) {
      assertEquals(expected[i], WallpaperAppearance.blurRadiusForStep(i));
      assertEquals(i, WallpaperAppearance.blurStep(expected[i]));
    }
    assertEquals(3, new WallpaperAppearance(0, 4, 0, 0, 0).blurRadius);
    assertEquals(0, new WallpaperAppearance(0, Integer.MIN_VALUE, 0, 0, 0).blurRadius);
  }

  @Test public void legacyBlurDefaultAndSwitchingPreserveOtherLocalOptions () {
    assertEquals(3, WallpaperAppearance.forBlurred(true).blurRadius);
    WallpaperAppearance original = new WallpaperAppearance(50, 15, 0xabcdef, 30, 1);
    WallpaperAppearance clear = original.withBlur(false);
    assertEquals(0, clear.blurRadius);
    assertEquals(original.dim, clear.dim);
    assertEquals(original.tintColor, clear.tintColor);
    assertEquals(original.tintIntensity, clear.tintIntensity);
    assertEquals(original.tintBlend, clear.tintBlend);
    assertEquals(15, original.withBlur(true).blurRadius);
  }

  @Test public void equalityIncludesEveryLocalParameter () {
    WallpaperAppearance base = new WallpaperAppearance(20, 7, 0x123456, 40, 2);
    assertEquals(base, new WallpaperAppearance(20, 7, 0x123456, 40, 2));
    assertNotEquals(base, new WallpaperAppearance(21, 7, 0x123456, 40, 2));
    assertNotEquals(base, new WallpaperAppearance(20, 3, 0x123456, 40, 2));
    assertNotEquals(base, new WallpaperAppearance(20, 7, 0x654321, 40, 2));
    assertNotEquals(base, new WallpaperAppearance(20, 7, 0x123456, 41, 2));
    assertNotEquals(base, new WallpaperAppearance(20, 7, 0x123456, 40, 1));
  }
}
