package moe.kirao.mgx.utils;

import org.drinkless.tdlib.TdApi;

import java.util.Locale;

public final class WallpaperColorInput {
  private WallpaperColorInput () { }

  public static int color (String text) {
    text = text.trim();
    if (text.startsWith("#")) text = text.substring(1);
    if (!text.matches("[0-9a-fA-F]{6}")) throw new IllegalArgumentException();
    return Integer.parseInt(text, 16);
  }

  public static TdApi.BackgroundFill fill (String text) {
    String[] parts = text.split(";", -1);
    if (parts.length > 2) throw new IllegalArgumentException();
    int angle = parts.length == 2 ? Integer.parseInt(parts[1].trim()) : 0;
    if (angle < 0 || angle >= 360 || angle % 45 != 0) throw new IllegalArgumentException();
    String[] values = parts[0].split(",", -1);
    if (values.length < 1 || values.length > 4 || (parts.length == 2 && values.length != 2)) throw new IllegalArgumentException();
    int[] colors = new int[values.length];
    for (int i = 0; i < values.length; i++) colors[i] = color(values[i]);
    switch (colors.length) {
      case 1: return new TdApi.BackgroundFillSolid(colors[0]);
      case 2: return new TdApi.BackgroundFillGradient(colors[0], colors[1], angle);
      default: return new TdApi.BackgroundFillFreeformGradient(colors);
    }
  }

  public static String formatColor (int color) {
    return String.format(Locale.ROOT, "#%06X", color & 0xffffff);
  }

  public static String formatFill (TdApi.BackgroundFill fill) {
    if (fill instanceof TdApi.BackgroundFillSolid) return formatColor(((TdApi.BackgroundFillSolid) fill).color);
    if (fill instanceof TdApi.BackgroundFillGradient) {
      TdApi.BackgroundFillGradient gradient = (TdApi.BackgroundFillGradient) fill;
      return formatColor(gradient.topColor) + ", " + formatColor(gradient.bottomColor) + "; " + gradient.rotationAngle;
    }
    StringBuilder text = new StringBuilder();
    if (fill instanceof TdApi.BackgroundFillFreeformGradient) {
      for (int color : ((TdApi.BackgroundFillFreeformGradient) fill).colors) {
        if (text.length() > 0) text.append(", ");
        text.append(formatColor(color));
      }
    }
    return text.toString();
  }
}
