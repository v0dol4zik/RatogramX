package moe.kirao.mgx.utils;

public final class ThemeColorMath {
  private ThemeColorMath () { }

  public static int mix (int first, int second, float amount) {
    amount = Math.max(0f, Math.min(1f, amount));
    int result = 0;
    for (int shift = 0; shift <= 24; shift += 8) {
      int a = (first >>> shift) & 255, b = (second >>> shift) & 255;
      result |= Math.round(a + (b - a) * amount) << shift;
    }
    return result;
  }

  public static int withAlpha (int color, int alpha) {
    return (color & 0xffffff) | (Math.max(0, Math.min(255, alpha)) << 24);
  }

  public static int composite (int background, int foreground) {
    return mix(background | 0xff000000, foreground | 0xff000000, (foreground >>> 24) / 255f);
  }

  private static double channel (int value) {
    double normalized = value / 255.0;
    return normalized <= .04045 ? normalized / 12.92 : Math.pow((normalized + .055) / 1.055, 2.4);
  }

  private static double luminance (int color) {
    return .2126 * channel((color >>> 16) & 255) + .7152 * channel((color >>> 8) & 255) + .0722 * channel(color & 255);
  }

  public static double contrast (int first, int second) {
    double a = luminance(first), b = luminance(second);
    return (Math.max(a, b) + .05) / (Math.min(a, b) + .05);
  }

  public static int readableText (int background, int preferred) {
    if (contrast(background, composite(background, preferred)) >= 4.5) return preferred;
    int light = 0xffffffff, dark = 0xff000000;
    return contrast(background, light) > contrast(background, dark) ? light : dark;
  }
}
