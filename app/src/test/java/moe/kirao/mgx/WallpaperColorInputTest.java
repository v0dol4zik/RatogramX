package moe.kirao.mgx;

import org.drinkless.tdlib.TdApi;
import org.junit.Test;
import moe.kirao.mgx.utils.WallpaperColorInput;
import tgx.td.Td;
import static org.junit.Assert.*;

public class WallpaperColorInputTest {
  @Test public void acceptsSolidLinearAndFreeformFills () {
    assertEquals(0xc62828, ((TdApi.BackgroundFillSolid) WallpaperColorInput.fill("#C62828")).color);
    TdApi.BackgroundFillGradient gradient = (TdApi.BackgroundFillGradient) WallpaperColorInput.fill("#112233, abcdef; 90");
    assertEquals(0x112233, gradient.topColor);
    assertEquals(0xabcdef, gradient.bottomColor);
    assertEquals(90, gradient.rotationAngle);
    assertArrayEquals(new int[] {0x112233, 0xabcdef, 0x123456},
      ((TdApi.BackgroundFillFreeformGradient) WallpaperColorInput.fill("112233,abcdef,123456")).colors);
  }

  @Test public void roundTripsAllSupportedFills () {
    for (String input : new String[] {"000000", "123456,abcdef;315", "112233,223344,334455,445566"}) {
      TdApi.BackgroundFill fill = WallpaperColorInput.fill(input);
      assertTrue(Td.equalsTo(fill, WallpaperColorInput.fill(WallpaperColorInput.formatFill(fill))));
    }
  }

  @Test public void rejectsBadColorsUnsupportedAnglesAndInvalidCounts () {
    for (String input : new String[] {"", "#xyzxyz", "#fff", "#00112233", "#123456,", "#123456;90",
      "123456,abcdef;30", "123456,abcdef;360", "123456,abcdef;90;45",
      "111111,222222,333333,444444,555555", "111111,222222,333333;90"}) {
      try { WallpaperColorInput.fill(input); fail(input); } catch (IllegalArgumentException expected) { }
    }
  }
}
