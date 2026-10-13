package moe.kirao.mgx.utils;

import androidx.annotation.Nullable;

import org.thunderdog.challegram.theme.ColorId;
import org.thunderdog.challegram.theme.ThemeDelegate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Color groups for the basic editor. Null values restore inherited colors. */
public final class BasicThemePalette {
  public static final int[] COLORS = {
    ColorId.textLink, ColorId.headerBackground, ColorId.filling, ColorId.background,
    ColorId.separator, ColorId.text, ColorId.textLight, ColorId.chatBackground,
    ColorId.bubbleOut_background, ColorId.bubbleIn_background, ColorId.bubbleOut_text, ColorId.badge
  };

  private BasicThemePalette () { }

  public static Map<Integer, Integer> colorsFor (ThemeDelegate current, ThemeDelegate parent, int anchor,
                                               @Nullable Integer color, Set<Integer> accentIds) {
    Map<Integer, Integer> values = new LinkedHashMap<>();
    values.put(anchor, color);
    switch (anchor) {
      case ColorId.textLink:
        int filling = current.getColor(ColorId.filling);
        for (int id : accentIds) {
          if (color == null) {
            values.put(id, null);
          } else {
            int tone = color;
            switch (id) {
              case ColorId.headerBackground:
              case ColorId.passcode:
              case ColorId.notification:
              case ColorId.notificationLink:
              case ColorId.notificationPlayer:
              case ColorId.messageSwipeBackground:
                tone = ThemeColorMath.mix(color, 0xff000000, .18f);
                break;
              case ColorId.bubbleOut_time:
              case ColorId.bubbleOut_separator:
              case ColorId.bubbleOut_progress:
                tone = ThemeColorMath.mix(color, filling, .35f);
                break;
              case ColorId.bubbleOut_background:
              case ColorId.togglerActiveBackground:
              case ColorId.textSelectionHighlight:
              case ColorId.waveformInactive:
              case ColorId.bubbleOut_waveformInactive:
              case ColorId.seekReady:
              case ColorId.unread:
                tone = ThemeColorMath.mix(filling, color, .16f);
                break;
            }
            int alpha = (parent.getColor(id) >>> 24) * (color >>> 24) / 255;
            values.put(id, ThemeColorMath.withAlpha(tone, alpha));
          }
        }
        values.put(anchor, color);
        header(current, values, color == null);
        bubble(current, values, true, color == null);
        badge(current, values, color == null);
        break;
      case ColorId.headerBackground:
        header(current, values, color == null);
        break;
      case ColorId.filling:
        foregrounds(current, values, ThemeColorMath.composite(current.getColor(ColorId.background),
          resolved(current, values, anchor)), color == null,
          ColorId.text, ColorId.textLight, ColorId.icon, ColorId.iconLight);
        break;
      case ColorId.background:
        foregrounds(current, values, resolved(current, values, anchor) | 0xff000000, color == null,
          ColorId.background_text, ColorId.background_textLight, ColorId.background_icon);
        break;
      case ColorId.textLight:
        values.put(ColorId.icon, color);
        values.put(ColorId.iconLight, color);
        break;
      case ColorId.chatBackground:
        values.put(ColorId.bubble_chatBackground, color);
        break;
      case ColorId.bubbleOut_background:
        bubble(current, values, true, color == null);
        break;
      case ColorId.bubbleIn_background:
        bubble(current, values, false, color == null);
        break;
      case ColorId.bubbleOut_text:
        values.put(ColorId.bubbleIn_text, color);
        break;
      case ColorId.badge:
        badge(current, values, color == null);
        break;
    }
    return values;
  }

  private static int resolved (ThemeDelegate theme, Map<Integer, Integer> values, int id) {
    Integer value = values.get(id);
    return value != null ? value : theme.getColor(id);
  }

  private static void header (ThemeDelegate theme, Map<Integer, Integer> values, boolean reset) {
    int background = ThemeColorMath.composite(theme.getColor(ColorId.filling), resolved(theme, values, ColorId.headerBackground));
    values.put(ColorId.headerText, reset ? null : ThemeColorMath.readableText(background, theme.getColor(ColorId.headerText)));
    values.put(ColorId.headerIcon, reset ? null : ThemeColorMath.readableText(background, theme.getColor(ColorId.headerIcon)));
  }

  private static void bubble (ThemeDelegate theme, Map<Integer, Integer> values, boolean outgoing, boolean reset) {
    int backgroundId = outgoing ? ColorId.bubbleOut_background : ColorId.bubbleIn_background;
    int background = ThemeColorMath.composite(theme.getColor(ColorId.background), resolved(theme, values, backgroundId));
    int[] ids = outgoing ? new int[] {ColorId.bubbleOut_text, ColorId.bubbleOut_time, ColorId.bubbleOut_ticks,
      ColorId.bubbleOut_ticksRead, ColorId.bubbleOut_progress} :
      new int[] {ColorId.bubbleIn_text, ColorId.bubbleIn_time, ColorId.bubbleIn_progress};
    for (int id : ids) {
      values.put(id, reset ? null : ThemeColorMath.readableText(background, resolved(theme, values, id)));
    }
  }

  private static void badge (ThemeDelegate theme, Map<Integer, Integer> values, boolean reset) {
    int background = ThemeColorMath.composite(theme.getColor(ColorId.filling), resolved(theme, values, ColorId.badge));
    values.put(ColorId.badgeText, reset ? null : ThemeColorMath.readableText(background, theme.getColor(ColorId.badgeText)));
  }

  private static void foregrounds (ThemeDelegate theme, Map<Integer, Integer> values, int background,
                                   boolean reset, int... ids) {
    for (int id : ids) {
      values.put(id, reset ? null : ThemeColorMath.readableText(background, theme.getColor(id)));
    }
  }
}
